package com.app.MonthlyTrackkaro

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class TransactionRepository(context: Context, private val userId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val transactionsCollection = db.collection("transactions")
    private val usersCollection = db.collection("users")

    private val _allTransactions = MutableLiveData<List<Transaction>>()
    val allTransactions: LiveData<List<Transaction>> = _allTransactions

    private val _totalIncome = MutableLiveData<Double>()
    val totalIncome: LiveData<Double> = _totalIncome

    private val _totalExpense = MutableLiveData<Double>()
    val totalExpense: LiveData<Double> = _totalExpense

    private val _expenseByCategory = MutableLiveData<List<CategorySum>>()
    val expenseByCategory: LiveData<List<CategorySum>> = _expenseByCategory

    private val _highestExpense = MutableLiveData<Transaction?>()
    val highestExpense: LiveData<Transaction?> = _highestExpense

    private val _expenseDayCount = MutableLiveData<Int>()
    val expenseDayCount: LiveData<Int> = _expenseDayCount

    private val _topCategory = MutableLiveData<String?>()
    val topCategory: LiveData<String?> = _topCategory

    private val _recentTransactions = MutableLiveData<List<Transaction>>()
    val recentTransactions: LiveData<List<Transaction>> = _recentTransactions

    init {
        observeTransactions()
    }

    // ── Profile Logic ─────────────────────────────────────────────────────────

    suspend fun saveProfile(name: String, email: String, budget: Double, currency: String) {
        if (userId.isEmpty()) return
        val profile = mapOf(
            "name" to name,
            "email" to email,
            "budget" to budget,
            "currency" to currency
        )
        try {
            usersCollection.document(userId).set(profile).await()
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error saving profile", e)
        }
    }

    fun observeProfile(onUpdate: (name: String, email: String, budget: Double, currency: String) -> Unit) {
        if (userId.isEmpty()) return
        usersCollection.document(userId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val name = snapshot.getString("name") ?: ""
                val email = snapshot.getString("email") ?: ""
                val budget = snapshot.getDouble("budget") ?: 0.0
                val currency = snapshot.getString("currency") ?: "₹"
                onUpdate(name, email, budget, currency)
            }
        }
    }

    // ── Transaction Logic ─────────────────────────────────────────────────────

    private fun observeTransactions() {
        if (userId.isEmpty()) return

        transactionsCollection
            .whereEqualTo("userId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("TransactionRepository", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val transactions = snapshot.toObjects(Transaction::class.java)
                    // Set firestoreId manually since it's not in the doc fields
                    snapshot.documents.forEachIndexed { index, doc ->
                        transactions[index].firestoreId = doc.id
                    }
                    
                    _allTransactions.postValue(transactions)
                    _recentTransactions.postValue(transactions.take(5))
                    
                    // Aggregations
                    calculateAggregations(transactions)
                }
            }
    }

    private fun calculateAggregations(transactions: List<Transaction>) {
        var income = 0.0
        var expense = 0.0
        val categoryMap = mutableMapOf<String, Double>()
        var highest: Transaction? = null
        val uniqueDays = mutableSetOf<Long>()

        for (t in transactions) {
            if (t.type == "Income") {
                income += t.amount
            } else if (t.type == "Expense") {
                expense += t.amount
                categoryMap[t.category] = (categoryMap[t.category] ?: 0.0) + t.amount
                
                if (highest == null || t.amount > highest.amount) {
                    highest = t
                }
                
                // Day count calculation (simplified)
                uniqueDays.add(t.date / 86400000)
            }
        }

        _totalIncome.postValue(income)
        _totalExpense.postValue(expense)
        _highestExpense.postValue(highest)
        _expenseDayCount.postValue(uniqueDays.size)
        
        val categorySums = categoryMap.map { CategorySum(it.key, it.value) }
            .sortedByDescending { it.total }
        _expenseByCategory.postValue(categorySums)
        _topCategory.postValue(categorySums.firstOrNull()?.category)
    }

    fun getTodayExpense(): LiveData<Double> {
        val todayTotal = MutableLiveData<Double>()
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        transactionsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("type", "Expense")
            .whereGreaterThanOrEqualTo("date", midnight)
            .addSnapshotListener { snapshot, _ ->
                val total = snapshot?.documents?.sumOf { it.getDouble("amount") ?: 0.0 } ?: 0.0
                todayTotal.postValue(total)
            }
        return todayTotal
    }

    suspend fun insert(transaction: Transaction) {
        try {
            transactionsCollection.add(transaction).await()
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error inserting", e)
        }
    }

    suspend fun delete(transaction: Transaction) {
        try {
            if (transaction.firestoreId.isNotEmpty()) {
                transactionsCollection.document(transaction.firestoreId).delete().await()
            } else {
                // Fallback: find by fields if firestoreId is missing
                val snapshot = transactionsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("date", transaction.date)
                    .whereEqualTo("amount", transaction.amount)
                    .get().await()
                snapshot.documents.firstOrNull()?.reference?.delete()?.await()
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error deleting", e)
        }
    }

    suspend fun update(transaction: Transaction) {
        try {
            if (transaction.firestoreId.isNotEmpty()) {
                transactionsCollection.document(transaction.firestoreId).set(transaction).await()
            }
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error updating", e)
        }
    }

    suspend fun deleteAll() {
        try {
            val snapshot = transactionsCollection.whereEqualTo("userId", userId).get().await()
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error deleting all", e)
        }
    }

    suspend fun getAllOnce(): List<Transaction> {
        return try {
            val snapshot = transactionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()
            val list = snapshot.toObjects(Transaction::class.java)
            snapshot.documents.forEachIndexed { index, doc ->
                list[index].firestoreId = doc.id
            }
            list
        } catch (e: Exception) {
            Log.e("TransactionRepository", "Error getting all once", e)
            emptyList()
        }
    }

    suspend fun isDuplicate(amount: Double, type: String, windowMs: Long = 30_000L): Boolean {
        return try {
            val sinceMs = System.currentTimeMillis() - windowMs
            val snapshot = transactionsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("amount", amount)
                .whereEqualTo("type", type)
                .whereGreaterThanOrEqualTo("date", sinceMs)
                .limit(1)
                .get().await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }
}
