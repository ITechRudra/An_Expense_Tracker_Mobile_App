# SMS parsing architecture

Everything in this document lives in the `:core-parser` module. That module has
**no Android dependencies**, which is deliberate: parsing is the part of this app
most likely to be wrong and most expensive to get wrong, so it is kept runnable
on a plain JVM where it can be tested exhaustively in seconds.

## The pipeline

```
RawSms
  │
  ├─ SmsFilter.reject()          ── OTP? promo? balance-only? declined? → Ignored
  │
  ├─ BankRegistry.identify()     ── sender header first, message body as fallback
  │
  ├─ ParserRegistry              ── bank-specific parser, else the generic one
  │
  └─ confidence scoring          ── Transaction (prompt) | NeedsReview (queue)
```

`TransactionSmsPipeline.ingest()` is pure and synchronous. Nothing about it
knows what an Android notification is, and nothing about it knows what a Room
database is.

## Why filtering comes first

The filter is deliberately biased towards rejection. A missed transaction costs
the user one manual entry. A phantom transaction — invented from an OTP, an
offer, or a payment *request* that has not been paid — corrupts every total the
app shows and destroys trust in the numbers.

Rejected categories, each with a distinct `RejectionReason` visible in the
in-app diagnostics screen:

| Reason | Example |
| --- | --- |
| `OTP_OR_VERIFICATION` | "123456 is your OTP for a transaction of Rs 5000 at AMAZON" |
| `PROMOTIONAL` | pre-approved loans, lifetime-free cards, cashback offers |
| `PAYMENT_REQUEST_OR_REMINDER` | UPI collect requests, "will be debited on 05-09" autopay notices |
| `DECLINED_OR_FAILED` | "was declined due to insufficient balance" |
| `SECURITY_OR_LOGIN_ALERT` | YONO login from a new device |
| `BALANCE_ENQUIRY_ONLY` | "Your account balance … is Rs 11,213.98" |
| `NOT_FROM_BANK_SENDER` | a 10-digit personal mobile number |

Note the OTP rule wins outright, even when the message quotes an amount and a
merchant: an OTP describes a transaction that *has not happened yet*.

## Field extraction

`Extractors` is the shared toolkit. Every extractor returns `null` rather than a
plausible-looking guess.

Two rules in there exist because the tests caught real bugs:

**Balance masking.** Balance clauses are blanked out *before* the amount is
scanned for. Without this, `Avl Bal:11213.98` in the IndusInd alert wins over the
actual `Rs 32.00`.

**The grouped-number branch requires a comma.** The amount pattern is
`\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?`. With `*` instead of `+`,
regex alternation is ordered rather than longest-match, so `2000.00` matched the
first branch as the three digits `200` — a silently hundredfold-wrong amount.
This is exactly the class of bug that never shows up in a demo and ruins a real
ledger.

**Amount resolution order**, most trustworthy first:

1. Currency-anchored, adjacent to the debit/credit verb — `debited by Rs 32.00`
2. Currency-anchored, verb following — `Rs 500 has been debited`
3. Any currency-prefixed figure in the (balance-masked) message
4. Verb-adjacent with no currency token — `debited by 150.0` (SBI). Tried last,
   and the figure may not run into a date separator.

**Direction** is decided by whichever of the debit/credit verbs appears *first*.
This matters for Bank of Baroda, which words one payment as both:

> Rs.600.00 **debited** from A/c XX3344 and **credited** to rent.owner@okhdfcbank

The debit comes first and is the side that belongs to this account.

## Confidence and the review queue

```
base 0.50 (amount + direction are mandatory)
+0.15  bank identified          +0.12  account/card tail
+0.12  RRN or reference         +0.06  UPI id
+0.05  merchant                 +0.05  bank-reported balance
+0.05  payment method inferred
```

At or above `0.70`, **and** with a bank plus at least one of account/VPA present,
the pipeline returns `Transaction` and the user is prompted. Otherwise it returns
`NeedsReview` with a list of what was missing — stored, but never surfaced as a
confident figure.

## Supported banks

`BankRegistry` currently recognises: IndusInd, HDFC, SBI, ICICI, Axis, Kotak,
Yes, IDFC FIRST, Bank of Baroda, PNB, Canara, Union Bank, Bank of India, Central
Bank, Indian Bank, Federal, RBL, AU Small Finance, Bandhan, DBS, Paytm Payments
Bank, Airtel Payments Bank.

Recognition works off the DLT sender header (`AD-INDUSB-S` → `INDUSB`) and falls
back to matching the bank's name in the message body, which covers issuers
sending from an unregistered short code.

Dedicated parsers exist where an issuer's wording defeats the generic rules:

| Parser | Why it exists |
| --- | --- |
| `IndusIndParser` | strips the trailing helpline number so it cannot be read as a merchant |
| `AxisParser` | card spends put the merchant between the date and `Avl Lmt`, with no preposition |
| `IciciParser` | merchant follows a semicolon — `; BIGBASKET credited` |
| `SbiParser` | `transfer to <NAME> Ref No <n>` |
| `KotakParser` | glues the reference to the previous sentence with no space |

The generic parser handles everything else, so an unlisted bank is not
unsupported — it just has no special-casing.

## Adding a new bank

1. Add a `Bank(...)` entry to `BankRegistry.banks` with its DLT sender tokens.
   For many banks this alone is enough — try it and run the tests.
2. If the format defeats the generic rules, subclass `GenericIndianBankParser`
   and override only the one extractor that is wrong:

   ```kotlin
   class MyBankParser : GenericIndianBankParser("mybank", setOf("MYBANK")) {
       override fun extractMerchant(body: String): String? =
           MY_PATTERN.find(body)?.groupValues?.get(1) ?: super.extractMerchant(body)
   }
   ```

3. Register it in `ParserRegistry.DEFAULT_PARSERS`. The generic parser always
   runs last, so nothing is ever left unparsed merely because it has no
   dedicated implementation.
4. Add a fixture to `SmsFixtures` and a test asserting every field. **Do not
   skip this** — a regex that works on one message and breaks another is the
   normal failure mode here, and the fixture set is what catches it.

Users can check whether their bank is being recognised from
**Settings → Detection diagnostics**, which shows the verdict for each message
seen. Message bodies are never stored there — only the sender and the decision.
