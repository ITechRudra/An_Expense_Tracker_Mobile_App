package com.rudra.expensetracker.domain

import com.rudra.expensetracker.core.category.CategorySuggestion
import com.rudra.expensetracker.core.category.CategorySuggestionEngine
import com.rudra.expensetracker.core.dedupe.DuplicateDetector
import com.rudra.expensetracker.core.dedupe.TransactionFingerprint
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.IngestResult
import com.rudra.expensetracker.core.sms.ParsedTransaction
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.core.sms.TransactionSmsPipeline
import com.rudra.expensetracker.data.local.IngestLogDao
import com.rudra.expensetracker.data.local.IngestLogEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.data.repository.AccountRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the caller (notification presenter or backfill importer) should do next. */
sealed interface SmsIngestOutcome {

    /** Stored unconfirmed; the user still needs to say what it was for. */
    data class Prompt(
        val transaction: TransactionEntity,
        val suggestion: CategorySuggestion?,
        val needsReview: Boolean,
    ) : SmsIngestOutcome

    data class Duplicate(val existingId: String) : SmsIngestOutcome

    data class Ignored(val reason: String) : SmsIngestOutcome
}

/**
 * The single path by which an SMS becomes a stored transaction.
 *
 * Parsing, duplicate rejection, account resolution and category suggestion all
 * happen here so that the SMS receiver, the onboarding backfill and any future
 * ingestion source behave identically.
 */
@Singleton
class IngestSmsUseCase @Inject constructor(
    private val pipeline: TransactionSmsPipeline,
    private val duplicateDetector: DuplicateDetector,
    private val suggestionEngine: CategorySuggestionEngine,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val mappings: MerchantMappingRepository,
    private val ingestLog: IngestLogDao,
    private val clock: AppClock,
) {

    suspend operator fun invoke(sms: RawSms): SmsIngestOutcome {
        val result = pipeline.ingest(sms)

        val (parsed, needsReview) = when (result) {
            is IngestResult.Transaction -> result.parsed to false
            is IngestResult.NeedsReview -> result.parsed to true
            is IngestResult.Ignored -> {
                log(sms, "IGNORED", result.reason.name)
                return SmsIngestOutcome.Ignored(result.reason.name)
            }
        }

        findDuplicate(parsed)?.let { existingId ->
            log(sms, "DUPLICATE", null)
            return SmsIngestOutcome.Duplicate(existingId)
        }

        val account = accounts.resolveOrCreate(parsed.bankCode, parsed.bankName, parsed.accountTail)
        val occurredAt = parsed.occurredAt?.let(clock::toEpochMillis) ?: sms.receivedAtEpochMillis
        val now = clock.nowMillis()

        val entity = TransactionEntity(
            id = "txn_${UUID.randomUUID()}",
            type = parsed.type,
            amountMinorUnits = parsed.amount.minorUnits,
            description = null,
            categoryId = null,
            merchant = parsed.merchant,
            upiId = parsed.upiId,
            counterparty = parsed.counterparty,
            accountId = account?.id,
            bankCode = parsed.bankCode,
            bankName = parsed.bankName,
            accountTail = parsed.accountTail,
            paymentMethod = parsed.paymentMethod,
            referenceId = parsed.referenceId,
            rrn = parsed.rrn,
            availableBalanceMinorUnits = parsed.availableBalance?.minorUnits,
            occurredAtEpochMillis = occurredAt,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            source = TransactionSource.SMS,
            parserId = parsed.parserId,
            smsId = sms.id,
            smsSender = sms.sender,
            isConfirmed = false,
            confidence = parsed.confidence,
            fuzzyKey = TransactionFingerprint.fuzzyKey(parsed),
        )

        // Losing this race means another path already stored the same alert.
        transactions.insertIfNew(entity, TransactionFingerprint.strongIds(parsed).toList())
            ?.let {
                log(sms, "DUPLICATE", null)
                return SmsIngestOutcome.Duplicate(it)
            }

        if (account != null && parsed.availableBalance != null) {
            accounts.recordReportedBalance(account.id, parsed.availableBalance!!, occurredAt)
        }

        val suggestion = suggestionEngine.suggest(
            parsed,
            mappings.forKey(suggestionEngine.payeeKey(parsed)),
        )
        log(sms, if (needsReview) "NEEDS_REVIEW" else "PARSED", parsed.parserId)
        return SmsIngestOutcome.Prompt(entity, suggestion, needsReview)
    }

    /**
     * Strong identifiers are checked by the database itself on insert; this
     * covers the fuzzy arm, where the same payment arrives worded differently
     * and carries no shared reference.
     */
    private suspend fun findDuplicate(parsed: ParsedTransaction): String? {
        val at = parsed.occurredAt?.let(clock::toEpochMillis) ?: parsed.receivedAtEpochMillis
        val window = if (parsed.upiId != null || parsed.merchant != null) {
            DuplicateDetector.DEFAULT_IDENTIFIED_WINDOW_MILLIS
        } else {
            DuplicateDetector.DEFAULT_ANONYMOUS_WINDOW_MILLIS
        }
        return transactions.findFuzzyDuplicate(
            TransactionFingerprint.fuzzyKey(parsed), parsed.amount.minorUnits, at, window,
        )?.id
    }

    /**
     * Records the verdict only. The message body is never written to the log or
     * to logcat -- see docs/PRIVACY notes in the README.
     */
    private suspend fun log(sms: RawSms, outcome: String, reason: String?) {
        runCatching {
            ingestLog.insert(
                IngestLogEntity(
                    sender = sms.sender,
                    outcome = outcome,
                    reason = reason,
                    bodyLength = sms.body.length,
                    createdAtEpochMillis = clock.nowMillis(),
                ),
            )
        }
    }
}

/**
 * Applies the user's answer to "what was this for?" and teaches the suggestion
 * engine from it.
 */
@Singleton
class ConfirmTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val mappings: MerchantMappingRepository,
    private val suggestionEngine: CategorySuggestionEngine,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        transactionId: String,
        categoryId: String,
        description: String?,
    ): TransactionEntity? {
        val existing = transactions.byId(transactionId) ?: return null
        val updated = existing.copy(
            categoryId = categoryId,
            // An empty box means "no note", not "erase what I typed before".
            description = description?.trim()?.takeIf { it.isNotEmpty() } ?: existing.description,
            isConfirmed = true,
        )
        transactions.update(updated)

        val payeeKey = suggestionEngine.payeeKey(
            com.rudra.expensetracker.core.sms.ParsedTransaction(
                type = updated.type,
                amount = Money(updated.amountMinorUnits),
                bankCode = updated.bankCode,
                bankName = updated.bankName,
                accountTail = updated.accountTail,
                upiId = updated.upiId,
                merchant = updated.merchant,
            ),
        )
        mappings.record(payeeKey, categoryId, updated.description, clock.nowMillis())
        return updated
    }
}
