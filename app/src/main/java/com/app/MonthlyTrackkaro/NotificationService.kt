package com.app.MonthlyTrackkaro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to all status-bar notifications and auto-detects financial transactions.
 *
 * Lifecycle: bound by the system — survives app close. Stops only when the user
 * revokes Notification Access in system settings.
 *
 * Parsing is fully delegated to [TransactionParser] — the same engine used by
 * [SMSReceiver] — so classification fixes apply to both sources simultaneously.
 *
 * Guards:
 *   1. User must be logged in
 *   2. Notification auto-detect toggle must be ON
 *   3. Notification must contain a currency marker (pre-filter)
 *   4. DB-backed duplicate check (same amount + type within 30 s)
 */
class NotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TransactionRepository
    private lateinit var prefs: SharedPrefHelper

    override fun onCreate() {
        super.onCreate()
        prefs      = SharedPrefHelper.get(applicationContext)
        repository = TransactionRepository(applicationContext, prefs.userId)
        createAutoDetectChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        if (!prefs.isLoggedIn)        return
        if (!prefs.autoDetectEnabled) return
        if (sbn.packageName == packageName) return

        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence("android.title")?.toString()   ?: ""
        val body    = extras.getCharSequence("android.text")?.toString()    ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title ${if (bigText.isNotBlank()) bigText else body}"

        // Quick pre-filter — skip if no currency marker present
        if (!fullText.contains("₹") &&
            !fullText.contains("Rs", ignoreCase = true) &&
            !fullText.contains("INR", ignoreCase = true)) return

        serviceScope.launch {
            val parsed = TransactionParser.parse(fullText) ?: return@launch

            // DB-backed duplicate check — catches SMS+Notification duplicates too
            if (repository.isDuplicate(parsed.amount, parsed.type)) return@launch

            repository.insert(
                Transaction(
                    title    = parsed.title,
                    amount   = parsed.amount,
                    type     = parsed.type,
                    category = parsed.category,
                    date     = parsed.dateMs,
                    source   = "Notification",
                    userId   = prefs.userId
                )
            )

            applicationContext.sendBroadcast(
                Intent(ACTION_TRANSACTION_DETECTED).apply {
                    putExtra(EXTRA_AMOUNT,   parsed.amount)
                    putExtra(EXTRA_TYPE,     parsed.type)
                    putExtra(EXTRA_CATEGORY, parsed.category)
                    putExtra(SMSReceiver.EXTRA_SOURCE, "Notification")
                    setPackage(packageName)
                }
            )

            showAutoDetectNotification(parsed)
        }
    }

    private fun showAutoDetectNotification(parsed: TransactionParser.ParsedTransaction) {
        val sym   = prefs.currencySymbol
        val label = if (parsed.type == "Income") "income" else "expense"
        val text  = "$sym%.2f $label detected (${parsed.category})".format(parsed.amount)

        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_AUTO_DETECT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("TrackKaro — Auto Detected 💰")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(AUTO_DETECT_NOTIF_ID, notification)
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS not granted */ }
    }

    private fun createAutoDetectChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_AUTO_DETECT,
                "Auto-Detected Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Confirms automatically detected bank transactions" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_TRANSACTION_DETECTED = "com.example.trackkaro.AUTO_TRANSACTION"
        const val EXTRA_AMOUNT   = "extra_amount"
        const val EXTRA_TYPE     = "extra_type"
        const val EXTRA_CATEGORY = "extra_category"

        private const val CHANNEL_AUTO_DETECT  = "trackkaro_auto_detect"
        private const val AUTO_DETECT_NOTIF_ID = 2001

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val component = ComponentName(context, NotificationService::class.java)
            return flat.split(":").any {
                runCatching { ComponentName.unflattenFromString(it) == component }.getOrDefault(false)
            }
        }

        fun openNotificationAccessSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
