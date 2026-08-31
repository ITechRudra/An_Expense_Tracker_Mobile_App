# Testing

## What actually runs, and where

| Suite | Location | Needs | Status |
| --- | --- | --- | --- |
| Core logic (61 tests) | `core-parser/src/test` | JVM only | **Executed, all passing** |
| Ingestion use cases | `app/src/test` | JVM + AndroidX | Written, needs Android SDK |
| Database | `app/src/androidTest` | device/emulator | Written, needs Android SDK |
| Compose UI | `app/src/androidTest` | device/emulator | Written, needs Android SDK |

The environment this project was built in has no access to `dl.google.com`, so
the Android SDK, AGP and AndroidX could not be downloaded and the `:app` module
could not be compiled here. See the "Build environment" note in the README.

`:core-parser` has no Android dependencies and resolves entirely from Maven
Central, which is exactly why the highest-risk logic was put there. It was
compiled and run for real.

## Running the tests

```bash
./gradlew :core-parser:test          # JVM, seconds, no emulator
./gradlew :app:testDebugUnitTest     # JVM
./gradlew :app:connectedDebugAndroidTest   # needs a device or emulator
./gradlew test                       # all JVM tests
```

## Core suite results

```
AnalyticsTest                      7 tests   0 failures
BudgetCalculatorTest               5 tests   0 failures
CategorySuggestionEngineTest       6 tests   0 failures
DuplicateDetectorTest              7 tests   0 failures
MoneyTest                          6 tests   0 failures
BankRegistryTest                   4 tests   0 failures
SmsFilterTest                     10 tests   0 failures
TransactionSmsPipelineTest        16 tests   0 failures
------------------------------------------------------
TOTAL                             61 tests   0 failures
```

## Bugs the tests caught

These were real defects in the first implementation, not hypotheticals. They are
listed because they are the argument for the module split.

1. **Hundredfold-wrong amounts.** The amount pattern's grouped branch used `*`
   for the comma group. Regex alternation is *ordered*, not longest-match, so
   `Rs.2000.00` matched the first branch as `200` and produced ₹200.00. Found by
   the ATM withdrawal fixture. Fixed by requiring at least one comma in that
   branch.
2. **Balance read as the transaction amount.** `Avl Bal:11213.98` was parsed as
   `112.00` by the same alternation bug, and would have beaten the real amount
   in other messages. Fixed by masking balance clauses before scanning, plus (1).
3. **Currency-less amounts missed entirely.** SBI writes `debited by 150.0` with
   no `Rs`. The message was rejected as `NO_AMOUNT_FOUND`. Fixed with a
   last-resort currency-optional pattern that cannot run into a date separator.

## The specified scenario

`TransactionSmsPipelineTest."parses the IndusInd UPI debit exactly as specified"`
asserts the exact case from the brief:

```
"A/C *XX7375 debited by Rs 32.00 towards Q528800175@ybl. RRN:155948254191.
 Avl Bal:11213.98. Not you? Call 18602677777 - IndusInd bank"
```

| Field | Expected | Asserted |
| --- | --- | --- |
| type | EXPENSE | ✓ |
| amount | 32.00 | ✓ (3200 minor units) |
| account | XX7375 | ✓ |
| UPI | Q528800175@ybl | ✓ |
| RRN | 155948254191 | ✓ |
| available balance | 11213.98 | ✓ |
| bank | IndusInd Bank | ✓ |
| payment method | UPI | ✓ |

A companion test asserts the helpline number `18602677777` is picked up as
neither the amount nor the reference.

## Fixtures

`SmsFixtures` holds 21 realistic messages. Account numbers, references and VPAs
are fabricated; the wording, punctuation and field ordering mirror what each
issuer actually sends, which is the part the parser has to survive.

- **Debits:** IndusInd UPI, HDFC UPI, SBI UPI (no currency symbol), ICICI
  (semicolon merchant), Axis card spend, Kotak, Bank of Baroda (debit *and*
  credit in one message), IDFC (`Info:` merchant), PNB ATM withdrawal, Canara NEFT
- **Credits:** salary, UPI from a friend, refund
- **Must never parse:** OTP quoting an amount, pre-approved loan, credit-card
  offer, YONO login alert, balance enquiry, declined payment, UPI collect
  request, future autopay notice, a message from a personal mobile number

`SmsFilterTest` asserts every genuine fixture survives the filter — a filter that
rejects everything would otherwise pass all the rejection tests.

## Coverage by area

**Parsing** — every fixture, field by field; confidence scoring; the review queue
for an unrecognised bank.

**Money** — Indian digit grouping, currency prefixes, trailing sentence
punctuation, single-decimal amounts (`150.0` is ₹150.00, not ₹1.50), rejection of
non-amounts, and a 1000-addition exactness check that a `Double` would fail.

**Duplicates** — identical SMS twice; same RRN in reworded text; delayed re-send
caught by the fuzzy window; two genuine same-amount payments a day apart kept
distinct; different amounts kept; debit and credit of the same amount kept.

**Learning** — keyword rules; a learned mapping beating a keyword rule; a
repeatedly confirmed mapping scoring higher than a one-off; an opaque UPI id
getting no suggestion until taught; income never keyword-guessed into an expense
category.

**Budgets** — under, near-limit at 80%, exceeded, exact-limit, and a zero limit
that must not divide by zero.

**Analytics** — summary totals, category grouping with shares, top merchants,
dense daily series including zero days, largest expenses, average daily spend
over the calendar span rather than active days, and empty input.

**Ingestion (app module)** — stores unconfirmed with all fields; creates the
account on first sight and reuses it; the same alert twice stores one row; OTP
never stored; confirming records the description *and* teaches the payee, with
the bank's UPI id surviving; a later payment to a taught payee arrives with a
suggestion but still unconfirmed; the diagnostic log records the verdict and the
body *length* but never the body.

**Database (instrumented)** — insert/read/update/delete; the strong-key primary
key rejecting a second claim; the cascade releasing keys on delete; fuzzy match
respecting its window; totals counting only confirmed rows; category seeding;
built-ins archiving while custom categories delete; merchant mapping hit counts
incrementing on agreement and resetting on a change of mind.

**UI (instrumented)** — category picker reports the tapped category; an
uncategorised row is marked "Needs category" and still shows the bank's UPI id;
a confirmed row leads with the user's own description while keeping the account
label.
