package com.example.trackkaro

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.bottomnavigation.BottomNavigationView

class AnalyticsActivity : AppCompatActivity() {

    private val viewModel: AnalyticsViewModel by viewModels()
    private lateinit var prefs: SharedPrefHelper

    // Views
    private lateinit var tvNetBalance: TextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpense: TextView
    private lateinit var tvDailyAverage: TextView
    private lateinit var tvHighestAmount: TextView
    private lateinit var tvHighestTitle: TextView
    private lateinit var pieChart: PieChart
    private lateinit var categoryChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var categoryBreakdownContainer: LinearLayout
    private lateinit var insightsContainer: LinearLayout

    // Category colors
    private val categoryColors = listOf(
        "#22C55E", "#3B82F6", "#F59E0B", "#EF4444", "#8B5CF6",
        "#06B6D4", "#EC4899", "#14B8A6"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = SharedPrefHelper.get(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        bindViews()
        setupPieChart()
        setupCategoryChart()
        setupBarChart()
        observeData()
        setupBottomNavigation()
    }

    private fun bindViews() {
        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvTotalExpense = findViewById(R.id.tvTotalExpense)
        tvDailyAverage = findViewById(R.id.tvDailyAverage)
        tvHighestAmount = findViewById(R.id.tvHighestAmount)
        tvHighestTitle = findViewById(R.id.tvHighestTitle)
        pieChart = findViewById(R.id.pieChart)
        categoryChart = findViewById(R.id.categoryChart)
        barChart = findViewById(R.id.barChart)
        categoryBreakdownContainer = findViewById(R.id.categoryBreakdownContainer)
        insightsContainer = findViewById(R.id.insightsContainer)
    }

    // ── CHART SETUP ──────────────────────────────────────────────────────────

    private fun setupPieChart() {
        pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 54f
            transparentCircleRadius = 59f
            setHoleColor(Color.WHITE)
            setDrawCenterText(true)
            setCenterTextSize(13f)
            setCenterTextColor(Color.parseColor("#111827"))
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
            legend.isEnabled = false
            setUsePercentValues(true)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(11f)
        }
    }

    private fun setupCategoryChart() {
        categoryChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 48f
            transparentCircleRadius = 53f
            setHoleColor(Color.WHITE)
            setDrawCenterText(true)
            setCenterText("Categories")
            setCenterTextSize(12f)
            setCenterTextColor(Color.parseColor("#111827"))
            legend.isEnabled = false
            setUsePercentValues(true)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(10f)
        }
    }

    private fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#6B7280")
                textSize = 10f
                granularity = 1f
                labelRotationAngle = -30f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F3F4F6")
                textColor = Color.parseColor("#6B7280")
                textSize = 10f
                axisMinimum = 0f
            }
        }
    }

    // ── OBSERVE DATA ─────────────────────────────────────────────────────────

    private fun sym() = prefs.currencySymbol

    private fun observeData() {
        // Overview card
        viewModel.netBalance.observe(this) { balance ->
            tvNetBalance.text = "${sym()}%.2f".format(balance ?: 0.0)
            tvNetBalance.setTextColor(
                if ((balance ?: 0.0) >= 0)
                    ContextCompat.getColor(this, R.color.text_white)
                else
                    ContextCompat.getColor(this, R.color.expense_red_light)
            )
        }
        viewModel.totalIncome.observe(this) { tvTotalIncome.text = "${sym()}%.2f".format(it ?: 0.0) }
        viewModel.totalExpense.observe(this) { expense ->
            tvTotalExpense.text = "${sym()}%.2f".format(expense ?: 0.0)
            // Budget vs expense banner
            val budget = prefs.monthlyBudget
            if (budget > 0) {
                val exp = expense ?: 0.0
                val pct = (exp / budget * 100).toInt()
                val msg = when {
                    pct >= 100 -> "⚠️ Over budget! Spent ${sym()}%.2f of ${sym()}%.2f budget".format(exp, budget)
                    pct >= 80  -> "🔶 ${pct}%% of budget used — ${sym()}%.2f left".format(budget - exp)
                    else       -> "✅ ${pct}%% of budget used — ${sym()}%.2f remaining".format(budget - exp)
                }
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Daily average
        viewModel.dailyAverage.observe(this) { avg ->
            tvDailyAverage.text = "${sym()}%.2f".format(avg ?: 0.0)
        }

        // Highest expense
        viewModel.highestExpense.observe(this) { tx ->
            if (tx != null) {
                tvHighestAmount.text = "${sym()}%.2f".format(tx.amount)
                tvHighestTitle.text = tx.title
            } else {
                tvHighestAmount.text = "${sym()}0.00"
                tvHighestTitle.text = "No expenses yet"
            }
        }

        // Income vs Expense pie chart
        var latestIncome = 0.0
        var latestExpense = 0.0
        viewModel.totalIncome.observe(this) { income ->
            latestIncome = income ?: 0.0
            updateIncomeExpensePie(latestIncome, latestExpense)
        }
        viewModel.totalExpense.observe(this) { expense ->
            latestExpense = expense ?: 0.0
            updateIncomeExpensePie(latestIncome, latestExpense)
        }

        // Category donut chart
        viewModel.expenseByCategory.observe(this) { categories ->
            updateCategoryChart(categories ?: emptyList())
            updateCategoryBreakdown(categories ?: emptyList(), latestExpense)
        }

        // Top category + monthly comparison feed into the insights card automatically.
        // Observers below keep the LiveData chain alive so computeInsights() fires.
        viewModel.topCategory.observe(this) { /* consumed by insights */ }
        viewModel.monthlyComparison.observe(this) { /* consumed by insights */ }

        // Weekly trend bar chart
        viewModel.weeklyTrend.observe(this) { weekly ->
            updateBarChart(weekly ?: emptyList())
        }

        // Monthly bar chart (fallback when weekly has no data)
        viewModel.monthlyExpenses.observe(this) { monthly ->
            // Only use monthly if weekly is all zeros
            val weeklyData = viewModel.weeklyTrend.value
            if (weeklyData == null || weeklyData.all { it.amount == 0f })
                updateBarChart(monthly ?: emptyList())
        }

        // Smart insights
        viewModel.insights.observe(this) { insights ->
            updateInsights(insights ?: emptyList())
        }
    }

    // ── CHART UPDATERS ────────────────────────────────────────────────────────

    private fun updateIncomeExpensePie(income: Double, expense: Double) {
        val total = income + expense
        if (total == 0.0) {
            pieChart.clear()
            pieChart.centerText = "No Data"
            pieChart.invalidate()
            return
        }

        val entries = listOf(
            PieEntry(income.toFloat(), "Income"),
            PieEntry(expense.toFloat(), "Expense")
        )
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(this@AnalyticsActivity, R.color.accent_green),
                ContextCompat.getColor(this@AnalyticsActivity, R.color.expense_red)
            )
            sliceSpace = 3f
            selectionShift = 5f
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            valueFormatter = PercentFormatter(pieChart)
        }
        pieChart.data = PieData(dataSet)
        pieChart.centerText = "${sym()}%.0f\nTotal".format(total)
        pieChart.animateY(800)
        pieChart.invalidate()
    }

    private fun updateCategoryChart(categories: List<CategorySum>) {
        if (categories.isEmpty()) {
            categoryChart.clear()
            categoryChart.centerText = "No Data"
            categoryChart.invalidate()
            return
        }

        val entries = categories.map { PieEntry(it.total.toFloat(), it.category) }
        val colors = categories.mapIndexed { i, _ ->
            Color.parseColor(categoryColors[i % categoryColors.size])
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 3f
            selectionShift = 5f
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            valueFormatter = PercentFormatter(categoryChart)
        }
        categoryChart.data = PieData(dataSet)
        categoryChart.animateY(800)
        categoryChart.invalidate()
    }

    private fun updateCategoryBreakdown(categories: List<CategorySum>, totalExpense: Double) {
        categoryBreakdownContainer.removeAllViews()
        if (categories.isEmpty()) return

        categories.forEachIndexed { index, cat ->
            val pct = if (totalExpense > 0) (cat.total / totalExpense * 100).toInt() else 0
            val color = Color.parseColor(categoryColors[index % categoryColors.size])

            // Row: dot + name + spacer + amount + pct
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dpToPx(10)
                layoutParams = lp
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Color dot
            val dot = TextView(this).apply {
                text = "●"
                textSize = 14f
                setTextColor(color)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dpToPx(8)
                layoutParams = lp
            }

            // Category name
            val name = TextView(this).apply {
                text = cat.category
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.text_primary))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }

            // Amount
            val amount = TextView(this).apply {
                text = "${sym()}%.2f  (%d%%)".format(cat.total, pct)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.text_secondary))
                typeface = Typeface.DEFAULT_BOLD
            }

            labelRow.addView(dot)
            labelRow.addView(name)
            labelRow.addView(amount)

            // Progress bar
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6)
                )
                lp.topMargin = dpToPx(5)
                layoutParams = lp
                max = 100
                this.progress = pct
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    progressDrawable.colorFilter = android.graphics.BlendModeColorFilter(color, android.graphics.BlendMode.SRC_IN)
                } else {
                    @Suppress("DEPRECATION")
                    progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }

            row.addView(labelRow)
            row.addView(progress)
            categoryBreakdownContainer.addView(row)
        }
    }

    private fun updateBarChart(monthly: List<MonthlyExpense>) {
        val hasData = monthly.any { it.amount > 0 }
        if (!hasData) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        val entries = monthly.mapIndexed { i, m -> BarEntry(i.toFloat(), m.amount) }
        val labels = monthly.map { it.monthLabel }

        val dataSet = BarDataSet(entries, "Monthly Expenses").apply {
            color = ContextCompat.getColor(this@AnalyticsActivity, R.color.accent_green)
            valueTextColor = Color.parseColor("#6B7280")
            valueTextSize = 9f
            setDrawValues(false)
        }

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.labelCount = labels.size
        barChart.data = BarData(dataSet).apply { barWidth = 0.6f }
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun updateInsights(insights: List<AnalyticsInsight>) {
        insightsContainer.removeAllViews()

        if (insights.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Add transactions to see insights"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.text_secondary))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dpToPx(8)
                layoutParams = lp
            }
            insightsContainer.addView(empty)
            return
        }

        insights.forEach { insight ->
            // Card-like row
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_white)
                elevation = dpToPx(2).toFloat()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dpToPx(8)
                layoutParams = lp
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            }

            val emoji = TextView(this).apply {
                text = insight.emoji
                textSize = 22f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dpToPx(12)
                layoutParams = lp
            }

            val message = TextView(this).apply {
                text = insight.message
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.text_primary))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }

            card.addView(emoji)
            card.addView(message)
            insightsContainer.addView(card)
        }
    }

    // ── BOTTOM NAV ────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_analytics
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.nav_analytics -> true
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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
