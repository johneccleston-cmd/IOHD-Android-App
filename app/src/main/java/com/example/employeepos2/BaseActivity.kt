package com.example.employeepos2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

open class BaseActivity : AppCompatActivity() {

    // 1 Hour in milliseconds
    private val TIMEOUT_MS = 60 * 60 * 1000L

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "UserPrefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onResume() {
        super.onResume()

        // Don't trigger a timeout if we are already on the Login screen!
        if (this is LoginActivity) return

        val lastActiveTime = securePrefs.getLong("LAST_ACTIVE_TIME", System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastActiveTime > TIMEOUT_MS) {
            forceLogout()
        }
    }

    override fun onPause() {
        super.onPause()
        updateLastActiveTime()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Resets the 1-hour clock every time they touch the screen!
        updateLastActiveTime()
    }

    private fun updateLastActiveTime() {
        if (this !is LoginActivity) {
            securePrefs.edit().putLong("LAST_ACTIVE_TIME", System.currentTimeMillis()).apply()
        }
    }

    // 🔥 NEW: Call this from LoginActivity the moment they successfully unlock!
    fun resetActivityTimer() {
        securePrefs.edit().putLong("LAST_ACTIVE_TIME", System.currentTimeMillis()).apply()
    }

    private fun forceLogout() {
        // We don't wipe their saved PIN/Username here, we just kick them to the lock screen.
        // They can use Biometrics/PIN to quickly get back in.
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}