package com.example.trackkaro

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPrefHelper
    private var selectedCurrency = "₹"   // default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = SharedPrefHelper.get(this)

        val etName   = findViewById<EditText>(R.id.etOnboardName)
        val etBudget = findViewById<EditText>(R.id.etOnboardBudget)
        val btnINR   = findViewById<MaterialButton>(R.id.btnCurrencyINR)
        val btnUSD   = findViewById<MaterialButton>(R.id.btnCurrencyUSD)
        val btnEUR   = findViewById<MaterialButton>(R.id.btnCurrencyEUR)
        val btnContinue = findViewById<MaterialButton>(R.id.btnOnboardContinue)
        val tvSkip   = findViewById<TextView>(R.id.tvOnboardSkip)

        // Pre-fill name if already set from signup
        etName.setText(prefs.userName.takeIf { it != "User" } ?: "")

        // Currency selection — highlight active button
        fun selectCurrency(symbol: String) {
            selectedCurrency = symbol
            val green  = getColor(R.color.accent_green)
            val grey   = getColor(R.color.divider)
            val greenTxt = getColor(R.color.accent_green)
            val greyTxt  = getColor(R.color.text_secondary)

            btnINR.strokeColor = android.content.res.ColorStateList.valueOf(if (symbol == "₹") green else grey)
            btnUSD.strokeColor = android.content.res.ColorStateList.valueOf(if (symbol == "$") green else grey)
            btnEUR.strokeColor = android.content.res.ColorStateList.valueOf(if (symbol == "€") green else grey)
            btnINR.setTextColor(if (symbol == "₹") greenTxt else greyTxt)
            btnUSD.setTextColor(if (symbol == "$") greenTxt else greyTxt)
            btnEUR.setTextColor(if (symbol == "€") greenTxt else greyTxt)
        }

        selectCurrency("₹")   // default highlight

        btnINR.setOnClickListener { selectCurrency("₹") }
        btnUSD.setOnClickListener { selectCurrency("$") }
        btnEUR.setOnClickListener { selectCurrency("€") }

        btnContinue.setOnClickListener {
            val name   = etName.text.toString().trim()
            val budget = etBudget.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveAndProceed(name, budget, selectedCurrency)
        }

        tvSkip.setOnClickListener {
            // Skip saves whatever is filled in (name at minimum from signup)
            val name = etName.text.toString().trim().ifEmpty { prefs.userName }
            saveAndProceed(name, "", selectedCurrency)
        }
    }

    private fun saveAndProceed(name: String, budgetStr: String, currency: String) {
        prefs.userName        = name
        prefs.currencySymbol  = currency
        prefs.onboardingComplete = true

        val budget = budgetStr.toDoubleOrNull()
        if (budget != null && budget > 0) prefs.monthlyBudget = budget

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
