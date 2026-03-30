package com.app.MonthlyTrackkaro

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val type: String,             // "Income" or "Expense"
    val date: Long = System.currentTimeMillis(),
    val source: String = "Manual",// "Manual" | "SMS" | "Notification"
    val userId: String = ""       // isolates data per user
)
