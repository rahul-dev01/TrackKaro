package com.app.MonthlyTrackkaro

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPrefHelper

    private lateinit var tvAvatarLetter: TextView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvNameDetail: TextView
    private lateinit var tvEmailDetail: TextView
    private lateinit var tvBudget: TextView
    private lateinit var tvCurrency: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = SharedPrefHelper.get(this)

        tvAvatarLetter = findViewById(R.id.tvProfileAvatarLetter)
        tvName         = findViewById(R.id.tvProfileName)
        tvEmail        = findViewById(R.id.tvProfileEmail)
        tvNameDetail   = findViewById(R.id.tvProfileNameDetail)
        tvEmailDetail  = findViewById(R.id.tvProfileEmailDetail)
        tvBudget       = findViewById(R.id.tvProfileBudget)
        tvCurrency     = findViewById(R.id.tvProfileCurrency)

        findViewById<android.view.View>(R.id.btnProfileBack).setOnClickListener { finish() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProfileEdit)
            .setOnClickListener { showEditDialog() }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        val name   = prefs.userName.ifBlank { "User" }
        val email  = prefs.userEmail.ifBlank { "Not set" }
        val sym    = prefs.currencySymbol
        val budget = prefs.monthlyBudget

        tvAvatarLetter.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        tvName.text         = name
        tvEmail.text        = email
        tvNameDetail.text   = name
        tvEmailDetail.text  = email
        tvBudget.text       = if (budget > 0) "$sym%.2f / month".format(budget) else "Not set"
        tvCurrency.text     = when (sym) {
            "$"  -> "$ US Dollar"
            "€"  -> "€ Euro"
            else -> "₹ Indian Rupee"
        }
    }

    private fun showEditDialog() {
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
        val etName  = view.findViewById<EditText>(R.id.etDialogName)
        val etEmail = view.findViewById<EditText>(R.id.etDialogEmail)
        etName.setText(prefs.userName)
        etEmail.setText(prefs.userEmail)

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name  = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.userName  = name
                prefs.userEmail = email
                loadProfile()
                Toast.makeText(this, "Profile updated ✓", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
