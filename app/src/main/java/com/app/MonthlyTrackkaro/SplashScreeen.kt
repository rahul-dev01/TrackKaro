package com.app.MonthlyTrackkaro

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_TrackKaro_Splash)
        setContentView(R.layout.activity_splash_screen)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = SharedPrefHelper.get(this)
            val destination = if (prefs.isLoggedIn) MainActivity::class.java
                              else GetStartedScreen::class.java
            startActivity(Intent(this, destination))
            finish()
        }, 2500)
    }
}
