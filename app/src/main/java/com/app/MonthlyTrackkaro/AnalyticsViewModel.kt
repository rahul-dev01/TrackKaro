package com.app.MonthlyTrackkaro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Calendar

data class AnalyticsInsight(val emoji: String, val message: String)
data class MonthlyExpense(val monthLabel: String, val amount: Float)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs      = SharedPrefHelper.get(application)
    private val userId     = prefs.userId
    private val repository = TransactionRepository(application, userId)

    val totalIncome: LiveData<Double>              = repository.totalIncome
    val totalExpense: LiveData<Double>             = repository.totalExpense
    val expenseByCategory: LiveData<List<CategorySum>> = repository.expenseByCategory
    val highestExpense: LiveData<Transaction?>     = repository.highestExpense
    val expenseDayCount: LiveData<Int>             = repository.expenseDayCount

    // Net balance
    val netBalance = MediatorLiveData<Double>().apply {
        var income = 0.0; var expense = 0.0
        addSource(repository.totalIncome)  { income  = it ?: 0.0; value = income - expense }
        addSource(repository.totalExpense) { expense = it ?: 0.0; value = income - expense }
    }

    // Daily average expense
    val dailyAverage = MediatorLiveData<Double>().apply {
        var expense = 0.0; var days = 0
        addSource(repository.totalExpense)  { expense = it ?: 0.0; value = if (days > 0) expense / days else expense }
        addSource(repository.expenseDayCount) { days = it ?: 0;    value = if (days > 0) expense / days else expense }
    }

    // Monthly expenses bar chart (current year, all 12 months)
    val monthlyExpenses = MediatorLiveData<List<MonthlyExpense>>().apply {
        addSource(repository.allTransactions) { value = computeMonthlyExpenses(it ?: emptyList()) }
    }

    // Top spending category this month
    val topCategory = MediatorLiveData<String?>().apply {
        addSource(repository.allTransactions) { value = computeTopCategory(it ?: emptyList()) }
    }

    // Monthly comparison: this month vs last month expense delta (%)
    val monthlyComparison = MediatorLiveData<Int?>().apply {
        addSource(repository.allTransactions) { value = computeMonthlyComparison(it ?: emptyList()) }
    }

    // Weekly spending: last 7 days, one bar per day
    val weeklyTrend = MediatorLiveData<List<MonthlyExpense>>().apply {
        addSource(repository.allTransactions) { value = computeWeeklyTrend(it ?: emptyList()) }
    }

    // Smart insights
    val insights = MediatorLiveData<List<AnalyticsInsight>>().apply {
        addSource(repository.allTransactions) { value = computeInsights(it ?: emptyList()) }
    }

    // ── Computation helpers ───────────────────────────────────────────────────

    private fun computeMonthlyExpenses(transactions: List<Transaction>): List<MonthlyExpense> {
        val labels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val totals = FloatArray(12)
        transactions.filter { it.type == "Expense" }.forEach { tx ->
            cal.timeInMillis = tx.date
            if (cal.get(Calendar.YEAR) == currentYear)
                totals[cal.get(Calendar.MONTH)] += tx.amount.toFloat()
        }
        return labels.mapIndexed { i, label -> MonthlyExpense(label, totals[i]) }
    }

    private fun computeTopCategory(transactions: List<Transaction>): String? {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH); val year = cal.get(Calendar.YEAR)
        return transactions
            .filter { it.type == "Expense" && isSameMonthYear(it.date, month, year) }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }?.key
    }

    private fun computeMonthlyComparison(transactions: List<Transaction>): Int? {
        val cal = Calendar.getInstance()
        val thisMonth = cal.get(Calendar.MONTH); val thisYear = cal.get(Calendar.YEAR)
        val lastMonth = if (thisMonth == 0) 11 else thisMonth - 1
        val lastYear  = if (thisMonth == 0) thisYear - 1 else thisYear

        val thisTotal = transactions.filter { it.type == "Expense" && isSameMonthYear(it.date, thisMonth, thisYear) }.sumOf { it.amount }
        val lastTotal = transactions.filter { it.type == "Expense" && isSameMonthYear(it.date, lastMonth, lastYear) }.sumOf { it.amount }

        return if (lastTotal > 0) ((thisTotal - lastTotal) / lastTotal * 100).toInt() else null
    }

    private fun computeWeeklyTrend(transactions: List<Transaction>): List<MonthlyExpense> {
        val dayLabels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(Calendar.YEAR)
        val totals = FloatArray(7)

        transactions.filter { it.type == "Expense" }.forEach { tx ->
            cal.timeInMillis = tx.date
            val txYear = cal.get(Calendar.YEAR)
            val txDay  = cal.get(Calendar.DAY_OF_YEAR)
            val diff   = (today - txDay) + (todayYear - txYear) * 365
            if (diff in 0..6) {
                val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
                val idx = if (dow == 1) 6 else dow - 2  // Mon=0..Sun=6
                totals[idx] += tx.amount.toFloat()
            }
        }
        return dayLabels.mapIndexed { i, label -> MonthlyExpense(label, totals[i]) }
    }

    private fun computeInsights(transactions: List<Transaction>): List<AnalyticsInsight> {
        val insights = mutableListOf<AnalyticsInsight>()
        if (transactions.isEmpty()) return insights

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH); val year = cal.get(Calendar.YEAR)
        val sym = prefs.currencySymbol

        // Top category this month
        val topCat = computeTopCategory(transactions)
        if (topCat != null) insights.add(AnalyticsInsight("🛒", "Most spent on $topCat this month"))

        // Month-over-month
        val delta = computeMonthlyComparison(transactions)
        if (delta != null) {
            val emoji = if (delta > 0) "📈" else "📉"
            val dir   = if (delta > 0) "up" else "down"
            insights.add(AnalyticsInsight(emoji, "Expenses $dir ${Math.abs(delta)}% vs last month"))
        }

        // Highest single expense
        val highest = transactions.filter { it.type == "Expense" }.maxByOrNull { it.amount }
        if (highest != null)
            insights.add(AnalyticsInsight("💸", "Biggest spend: ${highest.title} — $sym%.2f".format(highest.amount)))

        // Savings rate this month
        val income  = transactions.filter { it.type == "Income"  && isSameMonthYear(it.date, month, year) }.sumOf { it.amount }
        val expense = transactions.filter { it.type == "Expense" && isSameMonthYear(it.date, month, year) }.sumOf { it.amount }
        if (income > 0) {
            val savingsRate = ((income - expense) / income * 100).toInt()
            val emoji = if (savingsRate >= 20) "✅" else if (savingsRate > 0) "⚠️" else "🔴"
            insights.add(AnalyticsInsight(emoji, "Savings rate this month: $savingsRate%%"))
        }

        return insights
    }

    private fun isSameMonthYear(dateMs: Long, month: Int, year: Int): Boolean {
        val c = Calendar.getInstance().also { it.timeInMillis = dateMs }
        return c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
    }

    fun insert(transaction: Transaction) = viewModelScope.launch {
        repository.insert(transaction.copy(userId = userId))
    }
    fun delete(transaction: Transaction) = viewModelScope.launch { repository.delete(transaction) }
    fun update(transaction: Transaction) = viewModelScope.launch { repository.update(transaction) }
}
