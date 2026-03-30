package com.example.MonthlyTrackkaro

import android.content.Context
import android.content.SharedPreferences

class SharedPrefHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("trackkaro_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_NAME    = "user_name"
        const val KEY_USER_EMAIL   = "user_email"
        const val KEY_CURRENCY     = "currency_symbol"
        const val KEY_DARK_MODE    = "dark_mode"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_BUDGET       = "monthly_budget"
        const val KEY_AUTO_DETECT   = "auto_detect_enabled"
        const val KEY_SMS_DETECT    = "sms_detect_enabled"
        const val KEY_IS_LOGGED_IN  = "is_logged_in"
        const val KEY_USER_ID       = "user_id"
        const val KEY_ONBOARDING    = "onboarding_complete"

        // Convenience singleton so any Activity can call SharedPrefHelper.get(context)
        @Volatile private var INSTANCE: SharedPrefHelper? = null
        fun get(context: Context): SharedPrefHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedPrefHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── User Profile ──────────────────────────────────────────────────────────
    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "User") ?: "User"
        set(v) = prefs.edit().putString(KEY_USER_NAME, v).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_EMAIL, v).apply()

    // ── Currency ──────────────────────────────────────────────────────────────
    /** Returns the currency symbol, e.g. "₹", "$", "€" */
    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, "₹") ?: "₹"
        set(v) = prefs.edit().putString(KEY_CURRENCY, v).apply()

    // ── Theme ─────────────────────────────────────────────────────────────────
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_DARK_MODE, v).apply()

    // ── Notifications ─────────────────────────────────────────────────────────
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply()

    // ── Budget ────────────────────────────────────────────────────────────────
    var monthlyBudget: Double
        get() = prefs.getFloat(KEY_BUDGET, 0f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_BUDGET, v.toFloat()).apply()

    // ── Auto-detect transactions from notifications ────────────────────────────
    var autoDetectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_DETECT, v).apply()

    var smsDetectEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_DETECT, false)
        set(v) = prefs.edit().putBoolean(KEY_SMS_DETECT, v).apply()

    // ── Login session ─────────────────────────────────────────────────────────
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(v) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, v).apply()

    /** Stable unique ID for this user — set once on first login/signup, never changes. */
    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USER_ID, v).apply()

    /** True once the user has completed the post-signup onboarding form. */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING, v).apply()

    /** Clears session state on logout — does NOT wipe user profile or transactions. */
    fun clearSession() {
        isLoggedIn = false
        autoDetectEnabled = false
        smsDetectEnabled = false
        // NOTE: userId is intentionally NOT cleared — it persists so the user's
        // data is still there if they log back in on the same device.
    }
}
