package com.example.trackkaro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives ALL incoming SMS messages and auto-detects bank transactions.
 *
 * Filter strategy (in order — each gate logs its decision):
 *   1. Intent action must be SMS_RECEIVED
 *   2. User must be logged in
 *   3. SMS auto-detect toggle must be ON  (Settings → SMS Auto-Detect)
 *   4. Message body must contain a bank-transaction keyword
 *   5. [TransactionParser.parse] must return a valid result
 *   6. DB-backed duplicate check (same amount + type + user within 30 s)
 *
 * The sender-ID check is informational only — some carriers deliver PNB SMS
 * from numeric senders (+91XXXXXXXXXX) which would fail an alphanumeric check.
 *
 * Debug: filter logcat by tag "SMS_DEBUG" to trace every decision.
 */
class SMSReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {

        // ── Gate 1: correct action ────────────────────────────────────────────
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "onReceive() action=${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SKIP — wrong action")
            return
        }

        val prefs = SharedPrefHelper.get(context)

        // ── Gate 2: user logged in ────────────────────────────────────────────
        if (!prefs.isLoggedIn) {
            Log.d(TAG, "SKIP — user not logged in (isLoggedIn=false)")
            return
        }

        // ── Gate 3: SMS auto-detect enabled ───────────────────────────────────
        if (!prefs.smsDetectEnabled) {
            Log.d(TAG, "SKIP — SMS auto-detect is OFF (go to Settings → SMS Auto-Detect)")
            return
        }

        // ── Extract PDUs ──────────────────────────────────────────────────────
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "SKIP — getMessagesFromIntent returned empty/null")
            return
        }

        // Log every PDU individually (multi-part SMS arrives as separate PDUs)
        Log.d(TAG, "PDU count: ${messages.size}")
        messages.forEachIndexed { i, msg ->
            Log.d(TAG, "PDU[$i] sender=${msg.originatingAddress}  body=${msg.messageBody}")
        }

        // Reconstruct full body by joining all PDU bodies in order
        val sender    = messages[0].originatingAddress ?: "unknown"
        val body      = messages.joinToString("") { it.messageBody ?: "" }
        val smsTimeMs = messages[0].timestampMillis

        Log.d(TAG, "────────────────────────────────────────")
        Log.d(TAG, "Sender  : $sender")
        Log.d(TAG, "Body    : $body")
        Log.d(TAG, "PDU time: $smsTimeMs")
        Log.d(TAG, "Known bank sender: ${TransactionParser.isBankSender(sender)}")

        // ── Gate 4: strict 3-condition bank SMS filter ───────────────────────
        // ALL of: (credited|debited) + (INR|Rs|₹) + (a/c|account|txn|upi…)
        // Rejects OTPs, promotional messages, and random texts.
        if (!TransactionParser.isStrictBankSms(body)) {
            Log.d(TAG, "SKIP — failed strict bank SMS filter (not a real bank transaction)")
            return
        }

        val userId = prefs.userId
        Log.d(TAG, "userId  : $userId")

        scope.launch {

            // ── Gate 5: parse ─────────────────────────────────────────────────
            val parsed = TransactionParser.parse(body)
            if (parsed == null) {
                Log.d(TAG, "SKIP — parser returned null")
                return@launch
            }

            Log.d(TAG, "Parsed  → type=${parsed.type}  amount=${parsed.amount}" +
                       "  title=${parsed.title}  category=${parsed.category}" +
                       "  dateMs=${parsed.dateMs}")

            val repository = TransactionRepository(context, userId)

            // ── Gate 6: duplicate check ───────────────────────────────────────
            if (repository.isDuplicate(parsed.amount, parsed.type)) {
                Log.d(TAG, "SKIP — duplicate (amount=${parsed.amount}, type=${parsed.type})")
                return@launch
            }

            // Use date from SMS body when available (dateMs > 0),
            // otherwise fall back to the SMS PDU timestamp.
            // NOTE: dateMs default is 0L (not System.currentTimeMillis()) so this
            //       comparison is reliable — no race condition.
            val transactionDate = if (parsed.dateMs > 0L) parsed.dateMs else smsTimeMs

            Log.d(TAG, "Inserting transaction — date=$transactionDate")

            repository.insert(
                Transaction(
                    title    = parsed.title,
                    amount   = parsed.amount,
                    type     = parsed.type,
                    category = parsed.category,
                    date     = transactionDate,
                    source   = "SMS",
                    userId   = userId
                )
            )

            Log.d(TAG, "✅ SAVED → ${parsed.type} ₹${parsed.amount} [${parsed.category}] userId=$userId")

            // Notify any active screen
            context.sendBroadcast(
                Intent(NotificationService.ACTION_TRANSACTION_DETECTED).apply {
                    putExtra(NotificationService.EXTRA_AMOUNT,   parsed.amount)
                    putExtra(NotificationService.EXTRA_TYPE,     parsed.type)
                    putExtra(NotificationService.EXTRA_CATEGORY, parsed.category)
                    putExtra(EXTRA_SOURCE, "SMS")
                    setPackage(context.packageName)
                }
            )
        }
    }

    companion object {
        private const val TAG  = "SMS_DEBUG"
        const val EXTRA_SOURCE = "extra_source"
    }
}
