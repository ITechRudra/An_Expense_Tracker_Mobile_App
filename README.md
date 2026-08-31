# Expense Tracker

An Android app that reads your bank's SMS alerts on-device, works out the
amount, account, bank and payee, and asks you the only thing it cannot know:

> **What was this payment for?**

Everything stays on the phone. The app has **no internet permission at all**.

---

## Build environment note — read this first

This project was developed in a sandbox whose egress policy blocks
`dl.google.com` and `maven.google.com`. That means the **Android SDK, the Android
Gradle Plugin and every AndroidX artifact were unreachable**, so the `:app`
module could not be compiled and **no APK was produced here**. Maven Central was
reachable.

What that changes, honestly:

- **`:core-parser` was genuinely compiled and tested.** 61 unit tests, all
  passing, run for real — see [docs/TESTING.md](docs/TESTING.md). Three real
  bugs were found and fixed that way, including an amount regex that read
  `Rs.2000.00` as ₹200.00.
- **`:app` is complete source that has not been through a compiler.** It is a
  standard Android Studio project; open it and run `./gradlew assembleDebug` on a
  machine with normal network access. Expect to fix the ordinary friction of a
  first build — a dependency version in `gradle/libs.versions.toml` that has
  moved on, an import nudged by a newer AndroidX release. The version catalog
  pins AGP 8.9.2 / Kotlin 2.1.20 / compileSdk 36; bump them if your toolchain is
  newer.

Nothing here is stubbed or faked to paper over that. Where a feature could not
be verified, this README says so rather than claiming it works.

---

## What it does

```
BANK SMS ARRIVES
      ↓  parsed on-device: amount, debit/credit, bank, account, UPI, RRN, balance
"₹32 spent · Q528800175@ybl · What was this payment for?"
      ↓  one tap, from the notification
[ Food ]  ← suggested, because you chose Food for this payee before
      ↓
Saved. Dashboard updates.
```

**Automated:** amount, direction, bank, account, UPI id, merchant, transaction
id, RRN, date/time, bank-reported balance, payment method.

**Asked:** the purpose, and a description if you want one.

**Learned:** which category you pick for which payee. Suggestions are always
shown and always changeable — the app never files something under a category you
did not confirm.

### Features

- Automatic detection across 22 Indian banks and payment providers
- Filtering of OTPs, offers, loan ads, login alerts, balance enquiries, declined
  payments and payment *requests*
- Duplicate protection enforced by the database, not just in code
- Manual entry, editing and deletion
- Dashboard: month income/expense/net, today, this week, category breakdown,
  largest expenses, recent activity
- Analytics: week/month/year, spend by day, by category, top merchants, average
  daily spend, period-over-period comparison
- Budgets, overall and per category, with once-per-threshold alerts
- Search across merchant, description, amount, UPI id, reference, bank, account;
  filters by type, date, category, account, payment method and amount range
- Multiple accounts, detected and named automatically, renameable
- CSV and JSON export; duplicate-safe JSON restore
- Optional PIN and biometric app lock
- Material 3, edge-to-edge, dynamic colour, light and dark

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Room · Hilt · Coroutines + Flow ·
DataStore · WorkManager · Glance · AndroidX Biometric · Security-Crypto ·
kotlinx.serialization

```
:core-parser   pure Kotlin/JVM, no Android deps — parsing, dedupe, learning,
               budget and analytics maths. Runs under a plain JVM test task.
:app           Compose UI, Room, notifications, widget, QS tile.
```

Full detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Requirements

- **minSdk 26** (Android 8.0) · **targetSdk / compileSdk 36** (Android 16)
- JDK 17 · Android Gradle Plugin 8.9.2 · Kotlin 2.1.20

## Setup

```bash
git clone <this repo>
cd An_Expense_Tracker_Mobile_App
./gradlew :core-parser:test     # verify the parser first — fast, no emulator
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Or open the project root in Android Studio (Ladybug or newer) and hit Run.

## Permissions

| Permission | Why | Without it |
| --- | --- | --- |
| `RECEIVE_SMS` | read incoming bank alerts | no automatic detection; manual entry still works |
| `READ_SMS` | one-off import of the last 30 days | history starts from today |
| `POST_NOTIFICATIONS` | ask what a payment was for | transactions wait in-app under "Waiting for a category" |
| `RECEIVE_BOOT_COMPLETED` | re-schedule daily housekeeping | — |
| `POST_PROMOTED_NOTIFICATIONS` | Android 16 Live Updates / Now Bar | ordinary notification instead |
| `USE_BIOMETRIC` | optional app lock | PIN only |

**No `INTERNET` permission**, and no location, contacts, camera, storage or
foreground-service permissions. Full rationale in
[docs/PERMISSIONS.md](docs/PERMISSIONS.md).

### Google Play

`READ_SMS`/`RECEIVE_SMS` are restricted permissions, and **automatic expense
tracking from bank SMS is not an approved use case**. Assume this app cannot be
published on Google Play as built. It is intended for sideloading and personal
use, which is entirely legitimate — you are reading your own messages on your own
device.

The ingestion layer is deliberately pluggable: only `IngestSmsUseCase` knows an
SMS exists, so a compliant source (Account Aggregator, a bank API, statement
import) can be added by writing one class that emits a `ParsedTransaction`, with
no change to the UI, database or business logic. See
[docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## SMS parsing

A modular registry: a generic Indian-bank parser handles most formats, with
bank-specific parsers overriding only the field their issuer writes differently.
Adding a bank is usually one entry in `BankRegistry` plus a test fixture.

Supported: IndusInd, HDFC, SBI, ICICI, Axis, Kotak, Yes, IDFC FIRST, Bank of
Baroda, PNB, Canara, Union, Bank of India, Central Bank, Indian Bank, Federal,
RBL, AU Small Finance, Bandhan, DBS, Paytm Payments Bank, Airtel Payments Bank.

See [docs/SMS_PARSING.md](docs/SMS_PARSING.md) for the pipeline, the confidence
model, and how to add a bank.

## Samsung and live notifications

| Feature | Status |
| --- | --- |
| Live Updates (chip + lock screen) | Android 16+, detected at runtime |
| Samsung Now Bar | One UI 8+; consumes Live Updates — no separate SDK exists |
| Quick Settings tile | all versions |
| Home-screen widget | all versions |
| True lock-screen widget | **not app-controllable** — the system offers it, the user places it |

`DeviceCapabilities` checks every one of these at runtime and Settings tells the
user plainly what their device supports and what they get instead. No overlay
hacks, no accessibility abuse, no root. Details in
[docs/SAMSUNG_FEATURES.md](docs/SAMSUNG_FEATURES.md).

## Privacy

- No `INTERNET` permission — enforced by the platform, not by promises
- Everything in an app-private Room database
- Excluded from cloud backup and device-to-device transfer; you move your data
  deliberately, via the JSON export
- Message bodies are never logged, never stored, and never written to the
  diagnostics log (which keeps the sender and the verdict only)
- No bank credentials, UPI PIN or card PIN is ever requested — the app could not
  use them if you gave them
- Optional PIN, salted and SHA-256 hashed inside EncryptedSharedPreferences,
  verified in constant time; optional biometric unlock

## Export and backup

**CSV** for spreadsheets — with formula injection neutralised, so a merchant name
starting with `=` cannot execute in Excel. **JSON** for a complete backup
(transactions, accounts, categories, budgets, learned mappings). Restore is
additive and duplicate-safe: restoring the same file twice cannot double a
balance.

## Testing

61 core tests, executed and passing. App-module unit, Room and Compose tests are
written and run once an Android SDK is available.

```bash
./gradlew :core-parser:test
```

[docs/TESTING.md](docs/TESTING.md) lists coverage and the three real bugs the
suite caught.

## Known limitations

1. **No APK was built here** — the sandbox blocks Google's Maven and SDK hosts.
   `:app` compiles on a normal machine; expect minor first-build friction.
2. **Not publishable on Google Play** as built (restricted SMS permissions).
3. **Detection depends on the bank sending an SMS.** Some banks have moved to
   push notifications only; those are invisible to this app by design, since
   scraping notifications is unreliable and policy-violating.
4. **Cash is invisible** until you add it — there is no message to read.
5. **Unknown SMS formats** go to a review queue rather than being guessed at.
   Settings → Detection diagnostics shows why a message was skipped.
6. **Notification actions are capped at three** by Android: two categories plus
   "More…".
7. **Now Bar needs One UI 8+**; older Samsung devices get a standard notification.
8. **Lock-screen widget placement is the system's decision** — the app can only
   declare itself eligible.
9. **Multi-SIM** is handled as one stream; per-SIM attribution is not modelled.
10. **INR only.** `Money` is currency-agnostic but formatting and parsing are
    Indian-specific.
11. **A forgotten app-lock PIN cannot be recovered** — there is no account and no
    server. Keep a JSON backup.

## Documentation

- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — modules, layers, data flow, database
- [PERMISSIONS.md](docs/PERMISSIONS.md) — each permission, and the Play policy position
- [SMS_PARSING.md](docs/SMS_PARSING.md) — pipeline, confidence, adding a bank
- [SAMSUNG_FEATURES.md](docs/SAMSUNG_FEATURES.md) — Live Updates, Now Bar, lock screen
- [TESTING.md](docs/TESTING.md) — what runs, what it covers, what it caught
