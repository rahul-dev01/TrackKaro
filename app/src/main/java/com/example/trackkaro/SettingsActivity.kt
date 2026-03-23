package com.example.trackkaro

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var prefs: SharedPrefHelper

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvAvatarLetter: TextView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvCurrencyValue: TextView
    private lateinit var tvBudgetValue: TextView
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchAutoDetect: SwitchCompat
    private lateinit var switchSmsDetect: SwitchCompat
    private lateinit var bannerNotificationAccess: View

    // SMS permission launcher
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.READ_SMS] == true &&
                      results[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            prefs.smsDetectEnabled = true
            switchSmsDetect.setOnCheckedChangeListener(null)
            switchSmsDetect.isChecked = true
            attachSmsDetectListener()
            Toast.makeText(this, "SMS auto-detect enabled ✓", Toast.LENGTH_SHORT).show()
        } else {
            prefs.smsDetectEnabled = false
            switchSmsDetect.setOnCheckedChangeListener(null)
            switchSmsDetect.isChecked = false
            attachSmsDetectListener()
            Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ── File picker launchers ─────────────────────────────────────────────────
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportToUri(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFromUri(it) } }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SharedPrefHelper.get(this)
        applyTheme()
        setContentView(R.layout.activity_settings)
        bindViews()
        loadPreferences()
        setupListeners()
        setupBottomNavigation()
    }

    // ── Bind views ────────────────────────────────────────────────────────────
    private fun bindViews() {
        tvAvatarLetter           = findViewById(R.id.tvAvatarLetter)
        tvProfileName            = findViewById(R.id.tvProfileName)
        tvProfileEmail           = findViewById(R.id.tvProfileEmail)
        tvCurrencyValue          = findViewById(R.id.tvCurrencyValue)
        tvBudgetValue            = findViewById(R.id.tvBudgetValue)
        switchDarkMode           = findViewById(R.id.switchDarkMode)
        switchNotifications      = findViewById(R.id.switchNotifications)
        switchAutoDetect         = findViewById(R.id.switchAutoDetect)
        switchSmsDetect          = findViewById(R.id.switchSmsDetect)
        bannerNotificationAccess = findViewById(R.id.bannerNotificationAccess)
    }

    // ── Load saved preferences into UI ────────────────────────────────────────
    private fun loadPreferences() {
        val name = prefs.userName
        tvProfileName.text  = name
        tvAvatarLetter.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        tvProfileEmail.text = prefs.userEmail.ifEmpty { "Tap Edit to add email" }
        tvCurrencyValue.text = currencyLabel(prefs.currencySymbol)

        val budget = prefs.monthlyBudget
        tvBudgetValue.text = if (budget > 0)
            "${prefs.currencySymbol}%.2f / month".format(budget)
        else "Not set — tap to configure"

        // Set switch states without triggering listeners
        switchDarkMode.setOnCheckedChangeListener(null)
        switchNotifications.setOnCheckedChangeListener(null)
        switchAutoDetect.setOnCheckedChangeListener(null)
        switchDarkMode.isChecked      = prefs.isDarkMode
        switchNotifications.isChecked = prefs.notificationsEnabled
        switchAutoDetect.isChecked    = prefs.autoDetectEnabled &&
                NotificationService.isNotificationAccessGranted(this)
        switchSmsDetect.setOnCheckedChangeListener(null)
        switchSmsDetect.isChecked = prefs.smsDetectEnabled && hasSmsPermission()
        refreshAutoDetectBanner()
    }

    // ── All listeners ─────────────────────────────────────────────────────────
    private fun setupListeners() {

        // 1. Edit Profile
        findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            showEditProfileDialog()
        }

        // 2. Currency
        findViewById<LinearLayout>(R.id.rowCurrency).setOnClickListener {
            showCurrencyDialog()
        }

        // 3. Dark Mode
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.isDarkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Recreate so the new theme applies to all views immediately
            recreate()
        }

        // 4. Notifications reminder
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.notificationsEnabled = isChecked
            if (isChecked) {
                createNotificationChannel()
                sendReminderNotification()
            } else {
                Toast.makeText(this, "Reminders disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Budget
        findViewById<LinearLayout>(R.id.rowBudget).setOnClickListener { showBudgetDialog() }

        // 6. Export
        findViewById<LinearLayout>(R.id.rowExport).setOnClickListener {
            exportLauncher.launch("trackkaro_backup.json")
        }

        // 7. Import
        findViewById<LinearLayout>(R.id.rowImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        // 8. Reset
        findViewById<LinearLayout>(R.id.rowReset).setOnClickListener { showResetDialog() }

        // 9. Privacy Policy
        findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://trackkaro.example.com/privacy")))
        }

        // 10. Logout
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { showLogoutDialog() }

        // 11. Auto-detect toggle
        attachAutoDetectListener()

        // 12. SMS auto-detect toggle
        attachSmsDetectListener()

        // 13. Banner "Enable" button — secondary path
        findViewById<MaterialButton>(R.id.btnGrantAccess).setOnClickListener {
            NotificationService.openNotificationAccessSettings(this)
        }
    }

    // ── Auto-detect toggle listener (extracted so it can be re-attached) ──────
    private fun attachAutoDetectListener() {
        switchAutoDetect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !NotificationService.isNotificationAccessGranted(this)) {
                // Revert switch without re-triggering the listener
                switchAutoDetect.setOnCheckedChangeListener(null)
                switchAutoDetect.isChecked = false
                prefs.autoDetectEnabled    = false
                attachAutoDetectListener()          // re-attach after programmatic change

                // Show the in-app permission dialog immediately
                showNotificationAccessDialog()
            } else {
                prefs.autoDetectEnabled = isChecked
                refreshAutoDetectBanner()
                Toast.makeText(
                    this,
                    if (isChecked) "Auto-detect enabled ✓" else "Auto-detect disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── SMS detect toggle listener ────────────────────────────────────────────
    private fun attachSmsDetectListener() {
        switchSmsDetect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasSmsPermission()) {
                    prefs.smsDetectEnabled = true
                    Toast.makeText(this, "SMS auto-detect enabled ✓", Toast.LENGTH_SHORT).show()
                } else {
                    switchSmsDetect.setOnCheckedChangeListener(null)
                    switchSmsDetect.isChecked = false
                    attachSmsDetectListener()
                    showSmsPermissionRationale()
                }
            } else {
                prefs.smsDetectEnabled = false
                Toast.makeText(this, "SMS auto-detect disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun showSmsPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("📱 Allow SMS Access")
            .setMessage(
                "TrackKaro needs SMS permission to detect transactions from bank messages.\n\n" +
                "• Only messages from known bank senders are read\n" +
                "• Raw SMS content is never stored\n" +
                "• Only amount, type & category are saved\n" +
                "• You can disable this at any time"
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                smsPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            }
            .setNegativeButton("Not Now", null)
            .setCancelable(true)
            .show()
    }

    // ── In-app notification permission dialog ─────────────────────────────────
    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔔 Allow Notification Access")
            .setMessage(
                "TrackKaro needs Notification Access to automatically detect " +
                "transactions from your bank notifications.\n\n" +
                "• No notification content is stored\n" +
                "• Only amount, type & category are saved\n" +
                "• You can revoke access at any time\n\n" +
                "Tap \"Enable Now\" → find TrackKaro → turn it ON."
            )
            .setPositiveButton("Enable Now") { _, _ ->
                // Open system Notification Access settings directly
                NotificationService.openNotificationAccessSettings(this)
            }
            .setNegativeButton("Not Now", null)
            .setCancelable(true)
            .show()
    }

    // ── Banner visibility ─────────────────────────────────────────────────────
    private fun refreshAutoDetectBanner() {
        val accessGranted = NotificationService.isNotificationAccessGranted(this)
        bannerNotificationAccess.visibility =
            if (prefs.autoDetectEnabled && !accessGranted) View.VISIBLE else View.GONE
    }

    // ── BroadcastReceiver — shows toast when a transaction is auto-added ──────
    private val autoDetectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val amount   = intent?.getDoubleExtra(NotificationService.EXTRA_AMOUNT, 0.0) ?: return
            val type     = intent.getStringExtra(NotificationService.EXTRA_TYPE) ?: return
            val category = intent.getStringExtra(NotificationService.EXTRA_CATEGORY) ?: ""
            val sym      = prefs.currencySymbol
            val label    = if (type == "Income") "income" else "expense"
            Toast.makeText(
                this@SettingsActivity,
                "$sym%.2f $label auto-added ($category)".format(amount),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time user returns — they may have just granted access
        val accessGranted = NotificationService.isNotificationAccessGranted(this)
        if (accessGranted && prefs.autoDetectEnabled) {
            switchAutoDetect.setOnCheckedChangeListener(null)
            switchAutoDetect.isChecked = true
            attachAutoDetectListener()
        }
        refreshAutoDetectBanner()

        // Register broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this, autoDetectReceiver,
                IntentFilter(NotificationService.ACTION_TRANSACTION_DETECTED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                autoDetectReceiver,
                IntentFilter(NotificationService.ACTION_TRANSACTION_DETECTED)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(autoDetectReceiver) }
    }

    // ── Edit Profile dialog ───────────────────────────────────────────────────
    private fun showEditProfileDialog() {
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null, false)
        val etName  = view.findViewById<EditText>(R.id.etDialogName)
        val etEmail = view.findViewById<EditText>(R.id.etDialogEmail)
        etName.setText(prefs.userName)
        etEmail.setText(prefs.userEmail)

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name  = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.userName  = name
                prefs.userEmail = email
                loadPreferences()
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Currency dialog ───────────────────────────────────────────────────────
    private fun showCurrencyDialog() {
        val options = arrayOf("₹  Indian Rupee", "$  US Dollar", "€  Euro")
        val symbols = arrayOf("₹", "$", "€")
        val current = symbols.indexOf(prefs.currencySymbol).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.currencySymbol = symbols[which]
                tvCurrencyValue.text = currencyLabel(symbols[which])
                val budget = prefs.monthlyBudget
                tvBudgetValue.text = if (budget > 0)
                    "${prefs.currencySymbol}%.2f / month".format(budget)
                else "Not set — tap to configure"
                Toast.makeText(this, "Currency set to ${options[which].trim()}",
                    Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun currencyLabel(symbol: String) = when (symbol) {
        "$"  -> "$  US Dollar"
        "€"  -> "€  Euro"
        else -> "₹  Indian Rupee"
    }

    // ── Budget dialog ─────────────────────────────────────────────────────────
    private fun showBudgetDialog() {
        val input = EditText(this).apply {
            hint = "e.g. 15000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(48, 32, 48, 16)
            val current = prefs.monthlyBudget
            if (current > 0) setText("%.2f".format(current))
        }
        AlertDialog.Builder(this)
            .setTitle("Set Monthly Budget")
            .setMessage("Enter your spending limit for this month (${prefs.currencySymbol})")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toDoubleOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.monthlyBudget = value
                tvBudgetValue.text  = "${prefs.currencySymbol}%.2f / month".format(value)
                Toast.makeText(this, "Budget saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear") { _, _ ->
                prefs.monthlyBudget = 0.0
                tvBudgetValue.text  = "Not set — tap to configure"
                Toast.makeText(this, "Budget cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Reminder notification ─────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Expense Reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Daily expense tracking reminders" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun sendReminderNotification() {
        try {
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("TrackKaro Reminder 💰")
                .setContentText("Don't forget to track today's expenses!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(1001, n)
            Toast.makeText(this, "Reminders enabled", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Grant notification permission in Settings",
                Toast.LENGTH_LONG).show()
        }
    }

    // ── Export JSON ───────────────────────────────────────────────────────────
    private fun exportToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val transactions = viewModel.getAllOnce()
                val array = JSONArray()
                transactions.forEach { tx ->
                    array.put(JSONObject().apply {
                        put("id",       tx.id)
                        put("title",    tx.title)
                        put("amount",   tx.amount)
                        put("category", tx.category)
                        put("type",     tx.type)
                        put("date",     tx.date)
                    })
                }
                contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(array.toString(2)) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity,
                        "Exported ${transactions.size} transactions ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity,
                        "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Import JSON ───────────────────────────────────────────────────────────
    private fun importFromUri(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Import Transactions")
            .setMessage("This will ADD the imported transactions to your existing data. Continue?")
            .setPositiveButton("Import") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val raw = contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        } ?: throw Exception("Could not read file")
                        val array = JSONArray(raw)
                        var count = 0
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            viewModel.insert(Transaction(
                                title    = obj.getString("title"),
                                amount   = obj.getDouble("amount"),
                                category = obj.getString("category"),
                                type     = obj.getString("type"),
                                date     = obj.getLong("date")
                            ))
                            count++
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity,
                                "Imported $count transactions ✓", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity,
                                "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Reset dialog ──────────────────────────────────────────────────────────
    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Clear All Data")
            .setMessage("This will permanently delete ALL transactions. This action cannot be undone.")
            .setPositiveButton("Delete Everything") { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Logout dialog ─────────────────────────────────────────────────────────
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                prefs.clearSession()   // sets isLoggedIn=false, autoDetectEnabled=false
                startActivity(Intent(this, LoginScreen::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────────
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_settings
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "trackkaro_reminders"
    }
}
