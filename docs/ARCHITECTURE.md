# Architecture

## Module layout

```
:core-parser     pure Kotlin/JVM, zero Android dependencies
:app             Android application
```

The split is the single most important decision in the project. Everything that
is hard to get right and cheap to test — SMS parsing, duplicate detection,
category learning, budget arithmetic, analytics aggregation — lives in
`:core-parser` and runs under a plain JVM test task in seconds, with no emulator.

## Layers

```
Compose UI
    ↓  StateFlow
ViewModel
    ↓
Use cases          IngestSmsUseCase, ConfirmTransactionUseCase, SmsBackfillUseCase
    ↓
Repositories       Transaction, Account, Category, Budget, MerchantMapping
    ↓
Room DAOs + DataStore
```

Two boundaries are enforced rather than merely encouraged:

- **Parsing knows nothing about the UI.** `TransactionSmsPipeline.ingest()` is
  pure and synchronous, takes a `RawSms` and returns an `IngestResult`.
- **Notification code knows nothing about business logic.** `TransactionNotifier`
  renders a stored entity; it never decides whether something is a duplicate or
  what category to suggest.

## The flow that defines the app

```
SMS arrives
   → SmsReceiver (goAsync, ~8s budget)
   → IngestSmsUseCase
        ├ TransactionSmsPipeline.ingest()      parse + filter + score
        ├ duplicate check                      strong keys, then fuzzy window
        ├ AccountRepository.resolveOrCreate()   account appears by itself
        ├ insert UNCONFIRMED
        └ CategorySuggestionEngine.suggest()
   → TransactionNotifier.promptForCategory()
   → user taps a category (notification action, or the quick-add sheet)
   → ConfirmTransactionUseCase
        ├ set category + description, mark confirmed
        └ MerchantMappingDao.record()          this is the learning
   → dashboard updates through Flow
```

A detected transaction is stored **unconfirmed** immediately. It is never lost if
the user ignores the prompt — it surfaces under "Waiting for a category" on the
home screen instead.

## Duplicate protection

Two independent strategies, in order:

1. **Strong identifiers** — RRN, bank reference, originating SMS id. Stored in a
   separate `transaction_keys` table with the key as the **primary key**, claimed
   in the same `@Transaction` as the insert. Prevention is enforced by SQLite,
   not just by Kotlin, so a race between the SMS receiver and a manual save
   cannot slip a second copy through. The `ON DELETE CASCADE` releases the keys
   when a transaction is deleted, so a re-sent alert can be recorded again.
2. **Fuzzy key + time window** — `type|amount|last4|normalised-payee`. Catches
   the same payment arriving twice in different wording (a bank alert and a card
   network alert). The window is 6 hours when a payee is known and only 3 minutes
   when it is not, because the key is far weaker without one.

## Money

`Money` is a `@JvmInline value class` over `Long` minor units (paise). There is
no `Double` anywhere in the ledger. Repeated addition of binary floating point
silently drifts, and a ledger that drifts is worse than no ledger.

## Background work

There is **no long-running foreground service**. The app is event-driven:

- `SmsReceiver` is manifest-declared, so the system starts the process when a
  message arrives — whether or not the app has ever been opened, and after a
  reboot, without any restoration work.
- Parsing plus one insert takes milliseconds, well inside the `goAsync()` budget.
- WorkManager is a **failure path only** (`SmsIngestFailureQueue`), for an alert
  that could not be processed on arrival, plus one daily maintenance job.

This is why the app needs no battery-optimisation exemption and no
`FOREGROUND_SERVICE` permission.

## Database

Entities: `TransactionEntity`, `TransactionKeyEntity`, `AccountEntity`,
`CategoryEntity`, `BudgetEntity`, `MerchantMappingEntity`, `IngestLogEntity`.

Design notes worth calling out:

- **`description` and `merchant`/`upiId` are separate columns.** The bank tells
  us who was paid; the user tells us why. Neither ever overwrites the other.
- **`availableBalanceMinorUnits`** stores only what the bank stated. The app
  never presents a computed figure as a bank balance — the UI labels it
  "Bank-reported balance".
- **Category ids are stable strings** (`"food"`, not a generated UUID), so the
  keyword rules in `:core-parser` and any exported file keep referring to the
  same category across devices and restores.
- **Built-in categories archive rather than delete**, so transactions already
  filed under one keep a readable label.
- `IngestLogEntity` stores the sender and the verdict — **never the message body**.

## Dependency injection

Hilt, with `AppModule` providing the database, DAOs, and the `:core-parser`
singletons. The database provider takes a `Provider<ExpenseDatabase>` rather than
the instance, because the seed callback fires while the instance is still being
constructed.

## Error handling

- A malformed alert cannot crash `SmsReceiver`; failures are queued to
  WorkManager with a bounded retry count.
- One unreadable row cannot abandon an entire backfill import.
- Room converters decay unknown enum values to a safe default so a database
  written by a newer build still opens after a downgrade or partial restore.
- CSV export neutralises spreadsheet formula injection: a merchant name the app
  never chose could begin with `=`, which Excel would execute on open.
