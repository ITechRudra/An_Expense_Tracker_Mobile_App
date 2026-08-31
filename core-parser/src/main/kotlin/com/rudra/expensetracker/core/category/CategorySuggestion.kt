package com.rudra.expensetracker.core.category

import com.rudra.expensetracker.core.dedupe.TransactionFingerprint
import com.rudra.expensetracker.core.sms.ParsedTransaction
import com.rudra.expensetracker.core.sms.TransactionType
import java.util.Locale

/** Stable ids for the categories the app ships with. Users may add their own. */
object DefaultCategories {
    const val FOOD = "food"
    const val TRAVEL = "travel"
    const val SHOPPING = "shopping"
    const val ENTERTAINMENT = "entertainment"
    const val EDUCATION = "education"
    const val BILLS = "bills"
    const val HEALTH = "health"
    const val WORK = "work"
    const val PERSONAL = "personal"
    const val GROCERIES = "groceries"
    const val RENT = "rent"
    const val OTHER_EXPENSE = "other_expense"

    const val SALARY = "salary"
    const val REFUND = "refund"
    const val GIFT = "gift"
    const val TRANSFER = "transfer"
    const val INTEREST = "interest"
    const val OTHER_INCOME = "other_income"

    val expense = listOf(
        FOOD, TRAVEL, SHOPPING, ENTERTAINMENT, EDUCATION, BILLS,
        HEALTH, WORK, PERSONAL, GROCERIES, RENT, OTHER_EXPENSE,
    )
    val income = listOf(SALARY, REFUND, GIFT, TRANSFER, INTEREST, OTHER_INCOME)
}

/**
 * What the user previously chose for a given payee.
 *
 * [key] is the normalised VPA or merchant name; [hitCount] lets a repeatedly
 * confirmed choice outrank a one-off.
 */
data class MerchantMapping(
    val key: String,
    val categoryId: String,
    val description: String?,
    val hitCount: Int,
    val lastUsedAtEpochMillis: Long,
)

enum class SuggestionSource {
    /** Learned from this user's own past confirmations. */
    LEARNED,

    /** Matched a well-known payee name shipped with the app. */
    KEYWORD,
}

data class CategorySuggestion(
    val categoryId: String,
    val description: String?,
    val source: SuggestionSource,
    val confidence: Float,
)

/**
 * Suggests, never decides.
 *
 * Everything this produces is pre-filled into the confirmation UI where the
 * user can change it in one tap. The app deliberately does not auto-file a
 * transaction on a suggestion alone, because a wrong category that is never
 * seen is worse than a prompt that is.
 */
class CategorySuggestionEngine(
    private val keywordRules: List<KeywordRule> = DEFAULT_KEYWORD_RULES,
) {

    data class KeywordRule(val categoryId: String, val keywords: List<String>)

    /**
     * @param mappings the user's learned mappings; only those matching this
     *        transaction's payee key need be supplied.
     */
    fun suggest(
        parsed: ParsedTransaction,
        mappings: List<MerchantMapping>,
    ): CategorySuggestion? {
        val key = payeeKey(parsed)
        if (key.isNotEmpty()) {
            val learned = mappings.filter { it.key == key }
                .maxByOrNull { it.hitCount.toLong() * 1_000_000 + it.lastUsedAtEpochMillis / 1000 }
            if (learned != null) {
                return CategorySuggestion(
                    categoryId = learned.categoryId,
                    description = learned.description,
                    source = SuggestionSource.LEARNED,
                    // Two or more confirmations is a habit; one is a guess.
                    confidence = if (learned.hitCount >= 2) 0.9f else 0.65f,
                )
            }
        }

        if (parsed.type == TransactionType.INCOME) return null

        val haystack = listOfNotNull(parsed.merchant, parsed.upiId, parsed.counterparty)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (haystack.isBlank()) return null

        val rule = keywordRules.firstOrNull { r -> r.keywords.any { haystack.contains(it) } }
            ?: return null
        return CategorySuggestion(rule.categoryId, null, SuggestionSource.KEYWORD, 0.6f)
    }

    /** The key under which a confirmation is remembered for next time. */
    fun payeeKey(parsed: ParsedTransaction): String =
        TransactionFingerprint.normaliseCounterparty(parsed.upiId ?: parsed.merchant)

    companion object {
        val DEFAULT_KEYWORD_RULES = listOf(
            KeywordRule(
                DefaultCategories.FOOD,
                listOf("zomato", "swiggy", "dominos", "mcdonald", "kfc", "starbucks", "cafe",
                    "restaurant", "biryani", "pizza", "chai", "canteen", "bakery", "eatery"),
            ),
            KeywordRule(
                DefaultCategories.TRAVEL,
                listOf("uber", "ola", "rapido", "irctc", "redbus", "indigo", "makemytrip",
                    "goibibo", "petrol", "fuel", "hpcl", "iocl", "bharatpetro", "metro", "toll", "fastag"),
            ),
            KeywordRule(
                DefaultCategories.GROCERIES,
                listOf("bigbasket", "blinkit", "zepto", "dmart", "instamart", "grofers",
                    "reliancefresh", "kirana", "supermarket"),
            ),
            KeywordRule(
                DefaultCategories.SHOPPING,
                listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon", "ikea"),
            ),
            KeywordRule(
                DefaultCategories.ENTERTAINMENT,
                listOf("netflix", "spotify", "hotstar", "prime video", "bookmyshow", "pvr",
                    "inox", "youtube", "jiocinema"),
            ),
            KeywordRule(
                DefaultCategories.BILLS,
                listOf("electricity", "recharge", "airtel", "jio", "vodafone", "vi ", "bsnl",
                    "gas", "broadband", "insurance", "lic", "dth"),
            ),
            KeywordRule(
                DefaultCategories.HEALTH,
                listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "diagnostic",
                    "1mg", "pharmeasy", "practo"),
            ),
            KeywordRule(
                DefaultCategories.EDUCATION,
                listOf("college", "university", "school", "tuition", "coursera", "udemy",
                    "byju", "unacademy", "exam fee", "library"),
            ),
        )
    }
}
