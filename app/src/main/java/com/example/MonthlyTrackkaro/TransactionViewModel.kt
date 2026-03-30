package com.example.MonthlyTrackkaro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val userId     = SharedPrefHelper.get(application).userId
    private val repository = TransactionRepository(application, userId)

    // Full list — used by AllTransactionsActivity
    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions

    // Dashboard summary
    val totalIncome: LiveData<Double>               = repository.totalIncome
    val totalExpense: LiveData<Double>              = repository.totalExpense
    val topCategory: LiveData<String?>              = repository.topCategory
    val recentTransactions: LiveData<List<Transaction>> = repository.recentTransactions
    val todayExpense: LiveData<Double>              = repository.getTodayExpense()

    fun insert(transaction: Transaction) = viewModelScope.launch {
        repository.insert(transaction.copy(userId = userId))
    }
    fun delete(transaction: Transaction) = viewModelScope.launch { repository.delete(transaction) }
    fun update(transaction: Transaction) = viewModelScope.launch { repository.update(transaction) }
    fun deleteAll()                      = viewModelScope.launch { repository.deleteAll() }
    suspend fun getAllOnce(): List<Transaction> = repository.getAllOnce()
}
