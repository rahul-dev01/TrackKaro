package com.example.trackkaro

/**
 * Stateless parser for bank / payment notification text.
 * Returns a [ParsedTransaction] when the text looks like a financial event,
 * or null when it should be ignored.
 *
 * Play-Store safe: no SMS permission used — only notification text is read.
 * Only the extracted fields (amount, type, category, title) are stored;
 * the raw notification body is never persisted.
 */
object NotificationParser {

    // ── Amount regex ──────────────────────────────────────────────────────────
    // Matches: ₹500  ₹1,000  Rs.500  Rs 500  INR 500  INR2000
    private val AMOUNT_REGEX = Regex(
        """(?:₹|Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // ── Transaction type keywords (whole-word, case-insensitive) ──────────────
    //
    // IMPORTANT: Use \b word boundaries so "credited" does NOT match "debit",
    // and "credit" does NOT match "credited" ambiguously.
    // Priority: specific long-form words beat short-form (credited > credit).
    //
    // Checked in ORDER — first match wins. More-specific patterns come first.
    private val CREDIT_PATTERNS = listOf(
        Regex("""\bcredited\b""",  RegexOption.IGNORE_CASE),
        Regex("""\breceived\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bdeposited\b""", RegexOption.IGNORE_CASE),
        Regex("""\brefund\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bcashback\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bsalary\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bcredit\b""",    RegexOption.IGNORE_CASE)   // least specific — last
    )

    private val DEBIT_PATTERNS = listOf(
        Regex("""\bdebited\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bwithdrawn\b""",  RegexOption.IGNORE_CASE),
        Regex("""\bdeducted\b""",   RegexOption.IGNORE_CASE),
        Regex("""\bspent\b""",      RegexOption.IGNORE_CASE),
        Regex("""\bpurchase\b""",   RegexOption.IGNORE_CASE),
        Regex("""\bcharged\b""",    RegexOption.IGNORE_CASE),
        Regex("""\bpayment of\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaid\b""",       RegexOption.IGNORE_CASE),
        Regex("""\bdebit\b""",      RegexOption.IGNORE_CASE)   // least specific — last
    )

    // ── Category rules (checked in order — first match wins) ─────────────────
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

    // ── Duplicate guard ───────────────────────────────────────────────────────
    // Key = "amount|type|30sBucket" — evicted when set grows beyond 100 entries
    private val recentHashes = mutableSetOf<String>()

    // ── Public API ────────────────────────────────────────────────────────────

    data class ParsedTransaction(
        val title: String,
        val amount: Double,
        val type: String,       // "Income" | "Expense"
        val category: String
    )

    /**
     * @param text  The notification title + body concatenated with a space.
     * @param nowMs Current time in milliseconds (injectable for testing).
     * @return      A [ParsedTransaction] or null if the text is not a financial event
     *              or is a duplicate within the last 30 s.
     */
    fun parse(text: String, nowMs: Long = System.currentTimeMillis()): ParsedTransaction? {

        // 1. Must contain a recognisable amount
        val amountMatch = AMOUNT_REGEX.find(text) ?: return null
        val amount = amountMatch.groupValues[1]
            .replace(",", "")
            .toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        // 2. Determine transaction type
        //    Credit patterns are checked FIRST so "credited" is never shadowed
        //    by the shorter "debit" substring match.
        val isCredit = CREDIT_PATTERNS.any { it.containsMatchIn(text) }
        val isDebit  = DEBIT_PATTERNS.any  { it.containsMatchIn(text) }

        val type = when {
            isCredit && isDebit -> resolveConflict(text)  // both present — use context
            isCredit            -> "Income"
            isDebit             -> "Expense"
            else                -> return null            // ambiguous — skip
        }

        // 3. Duplicate check — bucket to nearest 30-second window
        val bucket = nowMs / 30_000
        val hash   = "$amount|$type|$bucket"
        if (hash in recentHashes) return null
        recentHashes.add(hash)
        if (recentHashes.size > 100) recentHashes.clear()

        // 4. Category
        val lower = text.lowercase()
        val category = CATEGORY_RULES.firstOrNull { (_, keywords) ->
            keywords.any { lower.contains(it) }
        }?.first ?: "Other"

        // 5. Title — merchant name heuristic
        val merchantRegex = Regex("""(?:at|to|from|by)\s+([A-Za-z0-9 &]+?)(?:\s+on|\s+for|\.|,|$)""")
        val merchant = merchantRegex.find(lower)
            ?.groupValues?.get(1)
            ?.trim()
            ?.replaceFirstChar { it.uppercaseChar() }

        val title = when {
            !merchant.isNullOrBlank() && merchant.length > 2 -> merchant
            type == "Income" -> "Auto Income"
            else             -> "Auto Expense"
        }

        return ParsedTransaction(title = title, amount = amount, type = type, category = category)
    }

    /**
     * Called when BOTH credit and debit keywords are present in the same text.
     * Uses positional heuristic: whichever keyword appears closer to the amount wins.
     * Falls back to "Expense" (safer — avoids inflating income).
     */
    private fun resolveConflict(text: String): String {
        val lower = text.lowercase()
        val amountPos = AMOUNT_REGEX.find(text)?.range?.first ?: return "Expense"

        val nearestCreditPos = CREDIT_PATTERNS
            .mapNotNull { it.find(lower)?.range?.first }
            .minOrNull() ?: Int.MAX_VALUE

        val nearestDebitPos = DEBIT_PATTERNS
            .mapNotNull { it.find(lower)?.range?.first }
            .minOrNull() ?: Int.MAX_VALUE

        // Whichever keyword is closer to the amount position wins
        return if (Math.abs(nearestCreditPos - amountPos) <= Math.abs(nearestDebitPos - amountPos))
            "Income" else "Expense"
    }
}
