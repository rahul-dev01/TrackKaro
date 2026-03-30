package com.example.MonthlyTrackkaro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in, skip straight to the app
        val prefs = SharedPrefHelper.get(this)
        if (prefs.isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }

        setContentView(R.layout.activity_login_screen)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            when {
                email.isEmpty() -> Toast.makeText(this, "Enter your email", Toast.LENGTH_SHORT).show()
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show()
                password.isEmpty() -> Toast.makeText(this, "Enter your password", Toast.LENGTH_SHORT).show()
                password.length < 6 -> Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                else -> {
                    prefs.isLoggedIn = true
                    // Use email as stable userId — unique per user on this device
                    prefs.userId    = email.lowercase().trim()
                    prefs.userName  = email.substringBefore("@")
                    prefs.userEmail = email
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
        }

        btnGoogle.setOnClickListener {
            Toast.makeText(this, "Google Sign-In coming soon", Toast.LENGTH_SHORT).show()
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, SignUpScreen::class.java))
        }
    }
}
