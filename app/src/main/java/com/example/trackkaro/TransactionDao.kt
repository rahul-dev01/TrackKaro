package com.example.trackkaro

import androidx.lifecycle.LiveData
import androidx.room.*

data class CategorySum(val category: String, val total: Double)
data class MonthlySum(val month: Int, val year: Int, val total: Double)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getAllTransactions(userId: String): LiveData<List<Transaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'Income' AND userId = :userId")
    fun getTotalIncome(userId: String): LiveData<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'Expense' AND userId = :userId")
    fun getTotalExpense(userId: String): LiveData<Double>

    @Query("""
        SELECT category, COALESCE(SUM(amount), 0) as total
        FROM transactions
        WHERE type = 'Expense' AND userId = :userId
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategory(userId: String): LiveData<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE type = 'Expense' AND userId = :userId ORDER BY amount DESC LIMIT 1")
    fun getHighestExpense(userId: String): LiveData<Transaction?>

    @Query("SELECT COUNT(DISTINCT date / 86400000) FROM transactions WHERE type = 'Expense' AND userId = :userId")
    fun getExpenseDayCount(userId: String): LiveData<Int>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    suspend fun getAllTransactionsOnce(userId: String): List<Transaction>

    // Duplicate guard — same amount + type + user within time window
    @Query("""
        SELECT * FROM transactions
        WHERE amount = :amount AND type = :type AND userId = :userId AND date >= :sinceMs
        LIMIT 1
    """)
    suspend fun getRecentByAmountAndType(
        amount: Double, type: String, userId: String, sinceMs: Long
    ): Transaction?

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    // Today's total expense (midnight to now)
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = 'Expense' AND userId = :userId AND date >= :midnightMs
    """)
    fun getTodayExpense(userId: String, midnightMs: Long): LiveData<Double>

    // Top expense category for this user
    @Query("""
        SELECT category FROM transactions
        WHERE type = 'Expense' AND userId = :userId
        GROUP BY category ORDER BY SUM(amount) DESC LIMIT 1
    """)
    fun getTopCategory(userId: String): LiveData<String?>

    // Most recent N transactions
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactions(userId: String, limit: Int): LiveData<List<Transaction>>
}
