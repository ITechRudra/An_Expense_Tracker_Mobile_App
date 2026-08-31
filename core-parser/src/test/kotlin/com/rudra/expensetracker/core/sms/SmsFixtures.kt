package com.rudra.expensetracker.core.sms

/**
 * Real-world-shaped alert texts from Indian banks and payment providers.
 *
 * Account numbers, references and VPAs are fabricated, but the wording,
 * punctuation and field ordering mirror what each issuer actually sends —
 * which is the part the parser has to survive.
 */
object SmsFixtures {

    fun sms(sender: String, body: String, id: String = "sms-1", receivedAt: Long = 1_756_000_000_000L) =
        RawSms(id = id, sender = sender, body = body, receivedAtEpochMillis = receivedAt)

    // ------------------------------------------------------------ debits

    val INDUSIND_UPI_DEBIT = sms(
        "AD-INDUSB-S",
        "A/C *XX7375 debited by Rs 32.00 towards Q528800175@ybl. RRN:155948254191. " +
            "Avl Bal:11213.98. Not you? Call 18602677777 - IndusInd bank",
    )

    val HDFC_UPI_DEBIT = sms(
        "VM-HDFCBK",
        "Sent Rs.450.00 From HDFC Bank A/C x1234 To ZOMATO LTD On 31/08/26 " +
            "Ref 512345678901 Not You? Call 18002586161",
    )

    val SBI_UPI_DEBIT = sms(
        "JD-SBIUPI-S",
        "Dear UPI user A/C X8912 debited by 150.0 on date 31Aug26 trf to SWIGGY " +
            "Refno 623456789012. If not u? call 1800111109. -SBI",
    )

    val ICICI_UPI_DEBIT = sms(
        "AD-ICICIB-S",
        "Acct XX4455 debited with Rs 1,250.00 on 30-Aug-26; BIGBASKET credited. " +
            "UPI:723456789012. Call 18002662 for dispute.",
    )

    val AXIS_CARD_SPEND = sms(
        "AD-AXISBK-S",
        "Spent Card no. XX9012 INR 2,499.00 30-08-26 MYNTRA DESIGNS Avl Lmt INR 47501.00. " +
            "SMS BLOCKCARD to 918691000002 if not you.",
    )

    val KOTAK_UPI_DEBIT = sms(
        "VK-KOTAKB-S",
        "Sent Rs.85.00 from Kotak Bank AC X6677 to chaiwala@okaxis on 31-08-26." +
            "UPI Ref 823456789012. Not you, kotak.com/fraud",
    )

    val BOB_UPI_DEBIT = sms(
        "AD-BOBTXN-S",
        "Rs.600.00 debited from A/c XX3344 and credited to rent.owner@okhdfcbank " +
            "(UPI Ref no 923456789012) - Bank of Baroda",
    )

    val IDFC_DEBIT = sms(
        "AD-IDFCFB-S",
        "Your A/c XXXXX2211 is debited by INR 199.00 on 29-08-26. Info: NETFLIX SUBSCRIPTION. " +
            "Avl Bal INR 8,420.55 - IDFC FIRST Bank",
    )

    val ATM_WITHDRAWAL = sms(
        "AD-PNBSMS-S",
        "Rs.2000.00 withdrawn from A/c XX7788 at ATM on 28-08-26 14:22. " +
            "Avl Bal Rs.15300.00. Txn No 111222333444 -Punjab National Bank",
    )

    val NEFT_TRANSFER_OUT = sms(
        "AD-CANBNK-S",
        "Your A/c XX5566 is debited by Rs.10,000.00 on 27-08-26 towards NEFT " +
            "transfer to RAHUL SHARMA. Ref No N123456789012. -Canara Bank",
    )

    // ----------------------------------------------------------- credits

    val SALARY_CREDIT = sms(
        "AD-ICICIB-S",
        "Dear Customer, Acct XX4455 credited with Rs 45,000.00 on 01-08-26. " +
            "Info: NEFT-ACME PAYROLL. Avl Bal Rs 56,213.98 -ICICI Bank",
    )

    val UPI_CREDIT = sms(
        "VM-HDFCBK",
        "Rs.500.00 credited to HDFC Bank A/C x1234 on 31/08/26 by VPA friend@okicici " +
            "Ref 512999888777. Not You? Call 18002586161",
    )

    val REFUND_CREDIT = sms(
        "AD-AXISBK-S",
        "INR 799.00 credited to A/c XX9012 on 29-08-26. Info: REFUND AMAZON SELLER. " +
            "Avl Bal INR 12300.00 - Axis Bank",
    )

    // ------------------------------------------------- must NOT be parsed

    val OTP = sms(
        "AD-HDFCBK-S",
        "123456 is your OTP for transaction of Rs 5000.00 at AMAZON on HDFC Bank Debit Card. " +
            "Valid for 10 mins. Do not share with anyone.",
    )

    val PROMOTIONAL_LOAN = sms(
        "AD-HDFCBK-P",
        "Congratulations! You are eligible for a pre-approved Personal Loan of Rs 5,00,000 " +
            "at lowest interest. Apply now: hdfcbk.io/x1y2 T&C apply",
    )

    val CREDIT_CARD_OFFER = sms(
        "AD-ICICIB-P",
        "Get a lifetime free ICICI Bank Credit Card with cashback up to Rs 5,000. " +
            "Click here to apply: icicibk.co/abc",
    )

    val LOGIN_ALERT = sms(
        "AD-SBIINB-S",
        "Dear Customer, you have logged in to SBI YONO from a new device on 31-08-26 at 10:15. " +
            "If not you, call 1800111109. -SBI",
    )

    val BALANCE_ENQUIRY = sms(
        "AD-INDUSB-S",
        "Your account balance for A/C XX7375 as on 31-08-26 is Rs 11,213.98. -IndusInd bank",
    )

    val DECLINED = sms(
        "VM-HDFCBK",
        "Your transaction of Rs 2,500.00 on HDFC Bank Card xx1234 at FLIPKART was declined " +
            "due to insufficient balance.",
    )

    val PAYMENT_REQUEST = sms(
        "AD-IDFCFB-S",
        "ravi@okaxis has requested Rs 300.00 from you. Approve in your UPI app before it expires.",
    )

    val AUTOPAY_REMINDER = sms(
        "AD-KOTAKB-S",
        "Rs 499.00 will be debited on 05-09-26 from A/c XX6677 towards SPOTIFY autopay mandate.",
    )

    val PERSONAL_SMS = sms(
        "+919876543210",
        "I sent you Rs 500 for the trip, please check",
    )
}
