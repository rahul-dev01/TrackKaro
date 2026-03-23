package com.example.trackkaro

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shared, stateless parser for bank transaction text.
 * Used by BOTH [SMSReceiver] and [NotificationService].
 *
 * Two-stage pipeline:
 *   Stage 1 — [parseBankSms]: structured bank SMS (PNB, SBI, HDFC, etc.)
 *   Stage 2 — [parseGeneral]: fallback for notifications and unstructured text.
 *
 * Security: raw message text is never stored — only extracted fields are returned.
 */
object TransactionParser {

    const val TAG = "SMS_DEBUG"

    // ── Amount regexes ────────────────────────────────────────────────────────
    // Matches: INR 100.00 / INR100.00 / INR 1,000.00
    private val AMOUNT_INR = Regex(
        """INR\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Matches: ₹500 / Rs.500 / Rs 500
    private val AMOUNT_SYMBOL = Regex(
        """(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Combined — used in conflict resolution
    private val AMOUNT_REGEX = Regex(
        """(?:INR|₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // ── Bank SMS body keyword pre-screen ──────────────────────────────────────
    // Intentionally broad — any one match passes the message to the full parser.
    // All keywords stored lowercase for direct comparison against lowercased body.
    private val BANK_SMS_KEYWORDS = listOf(
        "credited", "debited",          // transaction type words — highest priority
        "a/c", "acct", "account",       // account reference
        "inr", "rs.", "₹",              // currency markers (lowercase)
        "txn", "transaction",
        "upi", "neft", "imps", "rtgs"
    )

    // ── Strict 3-condition AND gate ───────────────────────────────────────────
    // ALL three conditions must be true for a message to be treated as a bank SMS.
    // This eliminates OTPs, promotional messages, and random texts.
    //
    // Condition 1: must contain a transaction type keyword
    private val TYPE_KEYWORDS    = listOf("credited", "debited")
    // Condition 2: must contain a currency marker
    private val CURRENCY_MARKERS = listOf("inr", "rs ", "rs.", "₹")
    // Condition 3: must contain a bank/account context keyword
    private val BANK_CONTEXT     = listOf("a/c", "account", "txn", "upi", "neft", "imps", "rtgs", "acct")

    // ── Bank sender keyword list ──────────────────────────────────────────────
    private val BANK_SENDER_KEYWORDS = listOf(
        "PNB", "PNBSMS", "PNJB",
        "HDFC", "HDFCBK",
        "SBI", "SBIINB", "SBIPSG",
        "ICICI", "ICICIB",
        "AXIS", "AXISBK",
        "KOTAK", "KOTAKB",
        "BOB", "BANKOFB",
        "CANARA", "CANARAB",
        "UNION", "UNIONB",
        "IDBI",
        "YESBNK", "YESBK",
        "INDUSB", "INDUSL",
        "FEDERAL", "FEDBK",
        "RBL", "RBLBK",
        "PAYTM", "PYTM",
        "PHONEPE", "PHPE",
        "GPAY", "GOOGLEPAY",
        "AMAZON", "AMZNPAY",
        "AIRTEL", "AIRTLPAY",
        "JIOMNY", "JIOB",
        "NEFT", "IMPS", "UPI",
        "BANK", "ALERT", "TXNALRT", "ACTALRT"
    )

    // ── Date formats seen in PNB / Indian bank SMS ────────────────────────────
    // PNB uses: "19-03-26 01:54:02"  (DD-MM-YY HH:mm:ss)
    //           "17-03-26 20:47:06"
    private val DATE_FORMATS = listOf(
        SimpleDateFormat("dd-MM-yy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd-MM-yy", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MMM-yyyy", Locale.US)
    )

    private val DATE_REGEX = Regex(
        """\b(\d{1,2}[-/]\d{2}[-/]\d{2,4}(?:\s+\d{2}:\d{2}:\d{2})?|\d{1,2}-[A-Za-z]{3}-\d{4})\b"""
    )

    // ── Category rules ────────────────────────────────────────────────────────
    private val CATEGORY_RULES: List<Pair<String, List<String>>> = listOf(
        "Food"      to listOf("swiggy", "zomato", "food", "restaurant", "cafe",
                               "dominos", "pizza", "burger", "dunzo", "blinkit"),
        "Travel"    to listOf("uber", "ola", "rapido", "irctc", "flight",
                               "airline", "metro", "bus", "cab", "taxi"),
        "Shopping"  to listOf("amazon", "flipkart", "myntra", "meesho", "nykaa",
                               "ajio", "shopping", "mall", "store"),
        "Utilities" to listOf("electricity", "water", "gas", "broadband",
                               "internet", "recharge", "mobile", "dth", "bill"),
        "Health"    to listOf("pharmacy", "hospital", "clinic", "doctor",
                               "medicine", "apollo", "medplus", "health"),
        "Salary"    to listOf("salary", "payroll", "stipend", "wages"),
        "Transfer"  to listOf("transfer", "neft", "imps", "upi", "rtgs",
                               "sent to", "received from")
    )

    // ── Phrase patterns (general parser) ─────────────────────────────────────
    private val INCOME_PHRASES = listOf(
        Regex("""\bpaid\s+you\b""",                       RegexOption.IGNORE_CASE),
        Regex("""\bsent\s+you\b""",                       RegexOption.IGNORE_CASE),
        Regex("""\badded\s+to\s+(your\s+)?account\b""",   RegexOption.IGNORE_CASE),
        Regex("""\breceived\s+from\b""",                  RegexOption.IGNORE_CASE),
        Regex("""\bmoney\s+received\b""",                 RegexOption.IGNORE_CASE),
        Regex("""\bcredited\s+to\s+(your\s+)?a/?c\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bis\s+credited\b""",                    RegexOption.IGNORE_CASE)
    )

    private val EXPENSE_PHRASES = listOf(
        Regex("""\bpaid\s+to\b""",        RegexOption.IGNORE_CASE),
        Regex("""\bsent\s+to\b""",        RegexOption.IGNORE_CASE),
        Regex("""\bpayment\s+to\b""",     RegexOption.IGNORE_CASE),
        Regex("""\bpayment\s+of\b""",     RegexOption.IGNORE_CASE),
        Regex("""\btransferred\s+to\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdeducted\s+from\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bdebited\s+from\b""",   RegexOption.IGNORE_CASE),
        Regex("""\ba/?c\s+\w+\s+debited\b""", RegexOption.IGNORE_CASE)
    )

    private val INCOME_WORDS = listOf(
        Regex("""\bcredited\b""",  RegexOption.IGNORE_CASE),
        Regex("""\breceived\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bdeposited\b""", RegexOption.IGNORE_CASE),
        Regex("""\brefund\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bcashback\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bsalary\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bcredit\b""",    RegexOption.IGNORE_CASE)
    )

    private val EXPENSE_WORDS = listOf(
        Regex("""\bdebited\b""",   RegexOption.IGNORE_CASE),
        Regex("""\bwithdrawn\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdeducted\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bspent\b""",     RegexOption.IGNORE_CASE),
        Regex("""\bpurchase\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bcharged\b""",   RegexOption.IGNORE_CASE),
        Regex("""\bpaid\b""",      RegexOption.IGNORE_CASE),
        Regex("""\bdebit\b""",     RegexOption.IGNORE_CASE)
    )

    // ── Result ────────────────────────────────────────────────────────────────

    data class ParsedTransaction(
        val title: String,
        val amount: Double,
        val type: String,       // "Income" | "Expense"
        val category: String,
        val dateMs: Long = 0L   // 0 = no date found in text; caller uses SMS PDU time
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun isBankSender(sender: String): Boolean {
        val upper    = sender.uppercase().trim()
        val stripped = if (upper.length > 3 && upper[2] == '-') upper.substring(3) else upper
        return BANK_SENDER_KEYWORDS.any { stripped.startsWith(it) || upper.startsWith(it) }
    }

    /**
     * Lightweight pre-screen: returns true if [body] contains ANY bank-transaction keyword.
     * All comparisons are case-insensitive via lowercase conversion.
     */
    fun isBankSmsBody(body: String): Boolean {
        val lower = body.lowercase()
        val matched = BANK_SMS_KEYWORDS.firstOrNull { lower.contains(it) }
        if (matched != null) {
            Log.d(TAG, "isBankSmsBody PASS — matched keyword: \"$matched\"")
            return true
        }
        Log.d(TAG, "isBankSmsBody FAIL — no bank keyword found in: ${body.take(80)}")
        return false
    }

    /**
     * STRICT 3-condition AND gate for SMS messages.
     * ALL three conditions must be true — eliminates OTPs, promos, and random texts.
     *
     *   Condition 1: contains "credited" or "debited"
     *   Condition 2: contains a currency marker (INR / Rs / ₹)
     *   Condition 3: contains a bank/account context word (a/c, account, txn, upi…)
     *
     * Use this for SMS. [isBankSmsBody] is kept for the broader notification path.
     */
    fun isStrictBankSms(body: String): Boolean {
        val lower = body.lowercase()

        val hasType = TYPE_KEYWORDS.any { lower.contains(it) }
        if (!hasType) {
            Log.d(TAG, "isStrictBankSms FAIL — Condition 1: no 'credited'/'debited'")
            return false
        }

        val hasCurrency = CURRENCY_MARKERS.any { lower.contains(it) }
        if (!hasCurrency) {
            Log.d(TAG, "isStrictBankSms FAIL — Condition 2: no currency marker (INR/Rs/₹)")
            return false
        }

        val hasBankContext = BANK_CONTEXT.any { lower.contains(it) }
        if (!hasBankContext) {
            Log.d(TAG, "isStrictBankSms FAIL — Condition 3: no bank context (a/c/account/txn/upi…)")
            return false
        }

        Log.d(TAG, "isStrictBankSms PASS — all 3 conditions met")
        return true
    }

    /**
     * Main entry point. Tries structured bank SMS fast-path first,
     * then falls back to the general keyword parser.
     */
    fun parse(text: String): ParsedTransaction? {
        Log.d(TAG, "parse() called — text length=${text.length}")
        val result = parseBankSms(text) ?: parseGeneral(text)
        if (result == null) Log.d(TAG, "parse() → null (no transaction detected)")
        return result
    }

    // ── Stage 1: Structured bank SMS ─────────────────────────────────────────
    //
    // Handles real PNB formats:
    //   CREDIT: "Your a/c XX0073 is credited for INR 100.00 on 17-03-26 20:47:06 through UPI"
    //   DEBIT:  "A/c XX0073 debited INR 1.00 Dt 19-03-26 01:54:02 thru UPI"
    //
    // Detection rules (plain contains(), case-insensitive via lowercase):
    //   "credited" → Income
    //   "debited"  → Expense
    //
    // The a/c check is now OPTIONAL — it improves precision but is not required,
    // so PNB SMS that omit "a/c" (e.g. some alert formats) are still caught.
    //
    private fun parseBankSms(text: String): ParsedTransaction? {
        val lower = text.lowercase()

        // Must contain a transaction type keyword
        val hasCredit = lower.contains("credited")
        val hasDebit  = lower.contains("debited")

        if (!hasCredit && !hasDebit) {
            Log.d(TAG, "parseBankSms → skip (no 'credited' or 'debited')")
            return null
        }

        // Must contain a currency amount
        val amount = extractAmount(text)
        if (amount == null) {
            Log.d(TAG, "parseBankSms → skip (no amount found)")
            return null
        }

        // Determine type — "credited" takes priority if both present
        val type = when {
            hasCredit -> "Income"
            else      -> "Expense"
        }

        Log.d(TAG, "parseBankSms → type=$type  amount=$amount")

        val dateMs   = extractDateMs(text)
        val category = detectCategory(lower)
        val title    = extractBankTitle(text, type)

        Log.d(TAG, "parseBankSms → title=$title  category=$category  dateMs=$dateMs")

        return ParsedTransaction(
            title    = title,
            amount   = amount,
            type     = type,
            category = category,
            dateMs   = dateMs
        )
    }

    // ── Stage 2: General keyword parser ──────────────────────────────────────

    private fun parseGeneral(text: String): ParsedTransaction? {
        val amount = extractAmount(text) ?: run {
            Log.d(TAG, "parseGeneral → skip (no amount)")
            return null
        }

        val incomeByPhrase  = INCOME_PHRASES.any  { it.containsMatchIn(text) }
        val expenseByPhrase = EXPENSE_PHRASES.any { it.containsMatchIn(text) }

        val type: String = when {
            incomeByPhrase  && !expenseByPhrase -> "Income"
            expenseByPhrase && !incomeByPhrase  -> "Expense"
            incomeByPhrase  && expenseByPhrase  -> resolveConflict(text)
            else -> {
                val incomeByWord  = INCOME_WORDS.any  { it.containsMatchIn(text) }
                val expenseByWord = EXPENSE_WORDS.any { it.containsMatchIn(text) }
                when {
                    incomeByWord  && !expenseByWord -> "Income"
                    expenseByWord && !incomeByWord  -> "Expense"
                    incomeByWord  && expenseByWord  -> resolveConflict(text)
                    else -> {
                        Log.d(TAG, "parseGeneral → skip (no type keyword)")
                        return null
                    }
                }
            }
        }

        Log.d(TAG, "parseGeneral → type=$type  amount=$amount")

        val lower    = text.lowercase()
        val category = detectCategory(lower)
        val title    = extractGeneralTitle(lower, type)

        return ParsedTransaction(
            title    = title,
            amount   = amount,
            type     = type,
            category = category,
            dateMs   = 0L   // no date in notification text; caller uses current time
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractAmount(text: String): Double? {
        val match = AMOUNT_INR.find(text) ?: AMOUNT_SYMBOL.find(text)
        if (match == null) {
            Log.d(TAG, "extractAmount → no match in: ${text.take(80)}")
            return null
        }
        val raw    = match.groupValues[1].replace(",", "")
        val amount = raw.toDoubleOrNull()
        Log.d(TAG, "extractAmount → raw=\"$raw\"  parsed=$amount")
        return amount?.takeIf { it > 0 }
    }

    /**
     * Parses the date embedded in the SMS body.
     * Returns 0L if no date is found or parsing fails — caller uses SMS PDU timestamp.
     */
    private fun extractDateMs(text: String): Long {
        val match = DATE_REGEX.find(text) ?: return 0L
        val raw   = match.groupValues[1]
        Log.d(TAG, "extractDateMs → raw date string: \"$raw\"")
        for (fmt in DATE_FORMATS) {
            try {
                val date = fmt.parse(raw)
                if (date != null) {
                    Log.d(TAG, "extractDateMs → parsed: ${date.time}")
                    return date.time
                }
            } catch (_: Exception) { /* try next format */ }
        }
        Log.d(TAG, "extractDateMs → all formats failed, returning 0L")
        return 0L
    }

    private fun detectCategory(lower: String): String {
        return CATEGORY_RULES.firstOrNull { (_, kw) -> kw.any { lower.contains(it) } }?.first
            ?: if (lower.contains("upi") || lower.contains("neft") ||
                   lower.contains("imps") || lower.contains("rtgs")) "Transfer"
               else "Bank"
    }

    private fun extractBankTitle(text: String, type: String): String {
        val lower = text.lowercase()
        val method = when {
            lower.contains("upi")  -> "UPI"
            lower.contains("neft") -> "NEFT"
            lower.contains("imps") -> "IMPS"
            lower.contains("rtgs") -> "RTGS"
            lower.contains("atm")  -> "ATM"
            else                   -> null
        }

        val upiNameRegex = Regex(
            """(?:to|from|by)\s+([A-Za-z0-9._@-]{3,30})""",
            RegexOption.IGNORE_CASE
        )
        val upiName = upiNameRegex.find(text)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.length > 2 && !it.equals("upi", ignoreCase = true) }
            ?.replaceFirstChar { it.uppercaseChar() }

        return when {
            upiName != null && method != null -> "$method - $upiName"
            upiName != null                   -> upiName
            method != null && type == "Income"  -> "$method Credit"
            method != null && type == "Expense" -> "$method Debit"
            type == "Income"  -> "Bank Credit"
            else              -> "Bank Debit"
        }
    }

    private fun extractGeneralTitle(lower: String, type: String): String {
        val merchantRegex = Regex(
            """(?:at|to|from|by)\s+([A-Za-z0-9 &]{3,30}?)(?:\s+on|\s+for|\.|,|$)"""
        )
        val merchant = merchantRegex.find(lower)?.groupValues?.get(1)
            ?.trim()?.replaceFirstChar { it.uppercaseChar() }
        return when {
            !merchant.isNullOrBlank() && merchant.length > 2 -> merchant
            type == "Income" -> "Auto Income"
            else             -> "Auto Expense"
        }
    }

    private fun resolveConflict(text: String): String {
        val amountPos      = AMOUNT_REGEX.find(text)?.range?.first ?: return "Expense"
        val allIncome      = INCOME_PHRASES + INCOME_WORDS
        val allExpense     = EXPENSE_PHRASES + EXPENSE_WORDS
        val nearestIncome  = allIncome.mapNotNull  { it.find(text)?.range?.first }.minOrNull() ?: Int.MAX_VALUE
        val nearestExpense = allExpense.mapNotNull { it.find(text)?.range?.first }.minOrNull() ?: Int.MAX_VALUE
        return if (Math.abs(nearestIncome - amountPos) <= Math.abs(nearestExpense - amountPos))
            "Income" else "Expense"
    }
}
