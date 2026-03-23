package com.example.trackkaro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvAvatarLetter: TextView
    private lateinit var tvSummaryLine: TextView
    private lateinit var tvTodayExpense: TextView
    private lateinit var tvTopCategory: TextView
    private lateinit var tvSeeAll: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnAdd: FloatingActionButton

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter
    private lateinit var prefs: SharedPrefHelper

    // SMS permission launcher — used when smsDetectEnabled=true but permission missing
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.READ_SMS] == true &&
                      results[Manifest.permission.RECEIVE_SMS] == true
        Log.d(TransactionParser.TAG, "SMS permission result: granted=$granted")
        if (!granted) prefs.smsDetectEnabled = false
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = SharedPrefHelper.get(this)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Status bar inset — prevents content overlapping notch/status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollView)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }

        bindViews()
        setupRecyclerView()
        setupSwipeToDelete()
        observeData()
        setupClickListeners()
        setupBottomNavigation()

        // Redirect to onboarding if not yet completed (e.g. user skipped or came from old install)
        if (!prefs.onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUserInfo()
        checkSmsPermission()
    }

    /**
     * If the user has SMS auto-detect ON but hasn't granted the runtime permission yet
     * (e.g. they enabled the toggle then cleared app permissions), request it now.
     * This ensures the receiver actually works without requiring the user to visit Settings.
     */
    private fun checkSmsPermission() {
        if (!prefs.smsDetectEnabled) return
        val readGranted    = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)    == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!readGranted || !receiveGranted) {
            Log.d(TransactionParser.TAG, "SMS detect ON but permission missing — requesting now")
            smsPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            )
        }
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private fun bindViews() {
        tvBalance      = findViewById(R.id.tvBalance)
        tvIncome       = findViewById(R.id.tvIncome)
        tvExpense      = findViewById(R.id.tvExpense)
        tvGreeting     = findViewById(R.id.tvGreeting)
        tvAvatarLetter = findViewById(R.id.tvAvatarLetter)
        tvSummaryLine  = findViewById(R.id.tvSummaryLine)
        tvTodayExpense = findViewById(R.id.tvTodayExpense)
        tvTopCategory  = findViewById(R.id.tvTopCategory)
        tvSeeAll       = findViewById(R.id.tvSeeAll)
        rvTransactions = findViewById(R.id.rvTransactions)
        emptyState     = findViewById(R.id.emptyState)
        btnAdd         = findViewById(R.id.btnAdd)

        // Avatar click → ProfileActivity
        findViewById<android.view.View>(R.id.avatarContainer).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(emptyList())
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter
        rvTransactions.isNestedScrollingEnabled = false
    }

    // ── Swipe to delete ───────────────────────────────────────────────────────

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position    = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return
                val transaction = adapter.getItemAt(position)

                // Delete — LiveData removes it from the list automatically
                viewModel.delete(transaction)

                // Snackbar with Undo
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "\"${transaction.title}\" deleted",
                    Snackbar.LENGTH_LONG
                )
                    .setAction("Undo") { viewModel.insert(transaction) }
                    .setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_green))
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTransactions)
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeData() {
        val sym = prefs.currencySymbol

        // Recent 5 transactions for dashboard list
        viewModel.recentTransactions.observe(this) { list ->
            val safeList = list ?: emptyList()
            adapter.updateList(safeList)
            emptyState.visibility     = if (safeList.isEmpty()) View.VISIBLE else View.GONE
            rvTransactions.visibility = if (safeList.isEmpty()) View.GONE    else View.VISIBLE
        }

        // Balance card
        viewModel.totalIncome.observe(this)  { updateBalanceCard() }
        viewModel.totalExpense.observe(this) { updateBalanceCard() }

        // Today's spend
        viewModel.todayExpense.observe(this) { today ->
            val amount = today ?: 0.0
            tvTodayExpense.text = "$sym%.2f".format(amount)
            tvSummaryLine.text  = if (amount > 0)
                "You spent $sym%.2f today".format(amount)
            else
                "No spending today 🎉"
        }

        // Top category
        viewModel.topCategory.observe(this) { cat ->
            tvTopCategory.text = cat ?: "—"
        }
    }

    private fun updateBalanceCard() {
        val sym     = prefs.currencySymbol
        val income  = viewModel.totalIncome.value  ?: 0.0
        val expense = viewModel.totalExpense.value ?: 0.0
        val balance = income - expense

        tvBalance.text = "$sym%.2f".format(balance)
        tvIncome.text  = "$sym%.2f".format(income)
        tvExpense.text = "$sym%.2f".format(expense)
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        btnAdd.setOnClickListener {
            addTransactionLauncher.launch(Intent(this, AddTransactionActivity::class.java))
        }

        tvSeeAll.setOnClickListener {
            startActivity(Intent(this, AllTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.btnSeeStatistics).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
    }

    // ── Add transaction result ────────────────────────────────────────────────

    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data     = result.data ?: return@registerForActivityResult
            val title    = data.getStringExtra("title")    ?: return@registerForActivityResult
            val amount   = data.getDoubleExtra("amount", 0.0)
            val type     = data.getStringExtra("type")     ?: return@registerForActivityResult
            val category = data.getStringExtra("category") ?: "Other"
            // ViewModel stamps userId automatically
            viewModel.insert(Transaction(title = title, amount = amount,
                category = category, type = type))
            // LiveData observers update the UI automatically — no manual refresh needed
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshUserInfo() {
        val name = prefs.userName.ifBlank { "User" }
        tvGreeting.text     = "Hello, $name! 👋"
        tvAvatarLetter.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    // ── Bottom navigation ─────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                else -> false
            }
        }
    }
}
