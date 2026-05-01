package com.app.MonthlyTrackkaro

import com.google.firebase.firestore.Exclude

data class Transaction(
    var id: Int = 0, // Keep for Room local cache if needed, but Firestore will use documentId
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val type: String = "",             // "Income" or "Expense"
    val date: Long = System.currentTimeMillis(),
    val source: String = "Manual",// "Manual" | "SMS" | "Notification"
    val userId: String = "",       // isolates data per user
    @get:Exclude @set:Exclude
    var firestoreId: String = ""   // Firestore document ID
)

data class CategorySum(val category: String, val total: Double)
data class MonthlySum(val month: Int, val year: Int, val total: Double)
