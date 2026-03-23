package com.example.trackkaro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var spType: Spinner
    private lateinit var btnSave: Button

    private lateinit var iconFood: View
    private lateinit var iconTravel: View
    private lateinit var iconShopping: View
    private lateinit var iconSalary: View

    private var selectedCategory = "Other"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        spType = findViewById(R.id.spType)
        btnSave = findViewById(R.id.btnSave)

        iconFood = findViewById(R.id.iconFood)
        iconTravel = findViewById(R.id.iconTravel)
        iconShopping = findViewById(R.id.iconShopping)
        iconSalary = findViewById(R.id.iconSalary)

        spType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Income", "Expense")
        )

        setupCategoryClicks()

        btnSave.setOnClickListener { saveTransaction() }
    }

    private fun setupCategoryClicks() {
        iconFood.setOnClickListener {
            etTitle.setText("Food")
            selectedCategory = "Food"
            highlightCategory(iconFood)
        }
        iconTravel.setOnClickListener {
            etTitle.setText("Travel")
            selectedCategory = "Travel"
            highlightCategory(iconTravel)
        }
        iconShopping.setOnClickListener {
            etTitle.setText("Shopping")
            selectedCategory = "Shopping"
            highlightCategory(iconShopping)
        }
        iconSalary.setOnClickListener {
            etTitle.setText("Salary")
            selectedCategory = "Salary"
            highlightCategory(iconSalary)
        }
    }

    private fun highlightCategory(selected: View) {
        listOf(iconFood, iconTravel, iconShopping, iconSalary).forEach {
            it.alpha = 0.5f
        }
        selected.alpha = 1.0f
    }

    private fun saveTransaction() {
        val title = etTitle.text.toString().trim()
        val amountText = etAmount.text.toString().trim()
        val type = spType.selectedItem.toString()

        if (title.isEmpty() || amountText.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        setResult(RESULT_OK, Intent().apply {
            putExtra("title", title)
            putExtra("amount", amount)
            putExtra("type", type)
            putExtra("category", selectedCategory)
        })
        finish()
    }
}
