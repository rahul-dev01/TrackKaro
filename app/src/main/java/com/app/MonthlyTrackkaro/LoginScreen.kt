package com.app.MonthlyTrackkaro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginScreen : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // If already logged in, skip straight to the app
        val prefs = SharedPrefHelper.get(this)
        if (auth.currentUser != null || prefs.isLoggedIn) {
            // Ensure prefs are synced if firebase user exists but prefs say not logged in
            if (auth.currentUser != null) {
                prefs.isLoggedIn = true
                prefs.userId = auth.currentUser!!.uid
                prefs.userEmail = auth.currentUser!!.email ?: ""
            }
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
                    btnLogin.isEnabled = false
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                prefs.isLoggedIn = true
                                prefs.userId    = user?.uid ?: email.lowercase().trim()
                                prefs.userEmail = email
                                // For login, we might not have the name immediately if it's not in Auth profile
                                // But we can set it to part of email if needed
                                if (prefs.userName == "User") {
                                    prefs.userName = email.substringBefore("@")
                                }
                                
                                startActivity(Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                            } else {
                                btnLogin.isEnabled = true
                                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
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
