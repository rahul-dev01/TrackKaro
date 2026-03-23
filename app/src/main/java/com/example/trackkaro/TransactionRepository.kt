package com.example.trackkaro

import android.content.Context
import androidx.lifecycle.LiveData

class TransactionRepository(context: Context, private val userId: String) {

    private val dao = AppDatabase.getDatabase(context).transactionDao()

    val allTransactions: LiveData<List<Transaction>> = dao.getAllTransactions(userId)
    val totalIncome: LiveData<Double>                = dao.getTotalIncome(userId)
    val totalExpense: LiveData<Double>               = dao.getTotalExpense(userId)
    val expenseByCategory: LiveData<List<CategorySum>> = dao.getExpenseByCategory(userId)
    val highestExpense: LiveData<Transaction?>       = dao.getHighestExpense(userId)
    val expenseDayCount: LiveData<Int>               = dao.getExpenseDayCount(userId)
    val topCategory: LiveData<String?>               = dao.getTopCategory(userId)
    val recentTransactions: LiveData<List<Transaction>> = dao.getRecentTransactions(userId, 5)

    fun getTodayExpense(): LiveData<Double> {
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.getTodayExpense(userId, midnight)
    }

    suspend fun insert(transaction: Transaction) = dao.insert(transaction)
    suspend fun delete(transaction: Transaction) = dao.delete(transaction)
    suspend fun update(transaction: Transaction) = dao.update(transaction)
    suspend fun deleteAll()                      = dao.deleteAll(userId)
    suspend fun getAllOnce(): List<Transaction>  = dao.getAllTransactionsOnce(userId)

    /** Returns true if a matching transaction was inserted within [windowMs] for this user. */
    suspend fun isDuplicate(amount: Double, type: String, windowMs: Long = 30_000L): Boolean =
        dao.getRecentByAmountAndType(
            amount, type, userId, System.currentTimeMillis() - windowMs
        ) != null
}
