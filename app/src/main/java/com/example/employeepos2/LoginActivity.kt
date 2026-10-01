package com.example.employeepos2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LoginActivity : BaseActivity() {

    companion object {
        private const val SCRIPT_URL = "https://integrity-backend-cr02.onrender.com"
        private const val API_TOKEN = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

        object PrefKeys {
            const val NAME = "NAME"
            const val ROLE = "ROLE"
            const val JOIN_YEAR = "JOIN_YEAR"
            const val SAVED_PIN = "SAVED_PIN"
            const val SAVED_USERNAME = "SAVED_USERNAME"
            const val HAS_5STAR = "HAS_5STAR"
            const val HAS_NO_RECALLS = "HAS_NO_RECALLS"
            const val IS_LEAD = "IS_LEAD"
            const val TENURE_TIER = "TENURE_TIER" // 🔥 Replaced Y1-Y5
            const val CAN_COLLECT = "CAN_COLLECT"
            const val TOOL_ALLOWANCE = "TOOL_ALLOWANCE"
        }
    }

    private lateinit var usernameInput: TextInputEditText
    private lateinit var pinInput: TextInputEditText
    private lateinit var loginBtn: MaterialButton
    private lateinit var loader: ProgressBar

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val prefs by lazy {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                deleteSharedPreferences("UserPrefs")
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
            } catch (clearError: Exception) {
                clearError.printStackTrace()
            }
            val newMasterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                this,
                "UserPrefs",
                newMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 🛡️ SAFE ZONE FIX: Prevents layout from sliding under the status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        usernameInput = findViewById(R.id.loginUsername)
        pinInput = findViewById(R.id.loginPin)
        loginBtn = findViewById(R.id.btnLogin)
        loader = findViewById(R.id.loginLoader)

        val savedPin = prefs.getString(PrefKeys.SAVED_PIN, "")
        val savedUsername = prefs.getString(PrefKeys.SAVED_USERNAME, "")

        // Autofill saved credentials
        if (!savedUsername.isNullOrEmpty()) {
            usernameInput.text = android.text.Editable.Factory.getInstance().newEditable(savedUsername)
        }
        if (!savedPin.isNullOrEmpty()) {
            pinInput.text = android.text.Editable.Factory.getInstance().newEditable(savedPin)
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val user = usernameInput.text.toString().trim()
                    val pin = pinInput.text.toString().trim()
                    verifyCredentials(user, pin)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    // If the user manually hit "Cancel", just stop.
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        return
                    }

                    // If the device has no biometrics configured, just log them in via the PIN!
                    val user = usernameInput.text.toString().trim()
                    val pin = pinInput.text.toString().trim()
                    verifyCredentials(user, pin)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@LoginActivity, "Biometric failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        loginBtn.setOnClickListener {
            if (loader.visibility == View.VISIBLE) return@setOnClickListener

            val username = usernameInput.text.toString().trim()
            val pin = pinInput.text.toString().trim()

            if (username.isEmpty()) {
                usernameInput.error = "Username Required"
                return@setOnClickListener
            }
            if (pin.isEmpty()) {
                pinInput.error = "PIN Required"
                return@setOnClickListener
            }

            // Attempt biometric login. If hardware fails, onAuthenticationError catches it and logs them in normally!
            biometricPrompt.authenticate(promptInfo)
        }
    }

    override fun onResume() {
        super.onResume()
        loginBtn.isEnabled = true
        loginBtn.isClickable = true
        loader.visibility = View.GONE
    }

    private fun verifyCredentials(username: String, pin: String) {
        loader.visibility = View.VISIBLE
        loginBtn.isEnabled = false

        val request = Request.Builder()
            .url("$SCRIPT_URL/login")
            .addHeader("X-Username", username)
            .addHeader("X-Pin", pin)
            .addHeader("Authorization", "Bearer $API_TOKEN")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loader.visibility = View.GONE
                    val savedPin = prefs.getString(PrefKeys.SAVED_PIN, "")
                    val savedUser = prefs.getString(PrefKeys.SAVED_USERNAME, "")

                    if (pin == savedPin && username == savedUser && !savedPin.isNullOrEmpty()) {
                        Toast.makeText(this@LoginActivity, "Offline Mode Activated", Toast.LENGTH_SHORT).show()

                        // 🔥 FIX: Reset the timer before launching!
                        resetActivityTimer()
                        launchMain()
                    } else {
                        loginBtn.isEnabled = true
                        Toast.makeText(this@LoginActivity, "No internet. First login requires connection.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    runOnUiThread {
                        loader.visibility = View.GONE
                        try {
                            if (!it.isSuccessful) {
                                loginBtn.isEnabled = true
                                if (it.code == 429) {
                                    try {
                                        val errorJson = JSONObject(body)
                                        Toast.makeText(this@LoginActivity, errorJson.optString("error", "Too many attempts. Please try again later."), Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(this@LoginActivity, "Too many login attempts. Please try again in 15 minutes.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(this@LoginActivity, "Server Error: ${it.code}", Toast.LENGTH_SHORT).show()
                                }
                                return@runOnUiThread
                            }

                            val json = JSONObject(body)

                            if (json.optString("status") == "success") {
                                prefs.edit().apply {
                                    putString(PrefKeys.NAME, json.optString("name"))
                                    putString(PrefKeys.ROLE, json.optString("role"))
                                    putString(PrefKeys.JOIN_YEAR, json.optString("join_year"))
                                    putString(PrefKeys.SAVED_PIN, pin)
                                    putString(PrefKeys.SAVED_USERNAME, username)
                                    putBoolean(PrefKeys.HAS_5STAR, json.optBoolean("has_5star"))
                                    putBoolean(PrefKeys.HAS_NO_RECALLS, json.optBoolean("has_no_recalls"))
                                    putBoolean(PrefKeys.IS_LEAD, json.optBoolean("is_lead"))
                                    putInt(PrefKeys.TENURE_TIER, json.optInt("tenure_tier", 0)) // 🔥 Replaced Booleans with Int
                                    putBoolean(PrefKeys.CAN_COLLECT, json.optBoolean("can_collect_payment", false))
                                    putString(PrefKeys.TOOL_ALLOWANCE, json.optString("tool_allowance", "1000"))
                                    apply()
                                }

                                if (json.optBoolean("needs_pin_change", false)) {
                                    showMaterialPinChangeDialog(pin, username)
                                } else {
                                    // 🔥 FIX: Reset the timer before launching!
                                    resetActivityTimer()
                                    launchMain()
                                }
                            } else {
                                loginBtn.isEnabled = true
                                Toast.makeText(this@LoginActivity, json.optString("error", "Invalid Credentials"), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            loginBtn.isEnabled = true
                            Toast.makeText(this@LoginActivity, "Data Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun showMaterialPinChangeDialog(oldPin: String, assignedUsername: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_pin, null)
        val etNewPin = dialogView.findViewById<TextInputEditText>(R.id.etNewPin)
        val etConfirmPin = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPin)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmitPin)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSubmit.setOnClickListener {
            val newPin = etNewPin.text.toString()
            val confirm = etConfirmPin.text.toString()

            if (newPin.length != 4) {
                Toast.makeText(this@LoginActivity, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPin != confirm) {
                Toast.makeText(this@LoginActivity, "PINs do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "SAVING..."
            updatePinOnServer(oldPin, newPin, assignedUsername, dialog, btnSubmit)
        }

        dialog.show()
    }

    private fun updatePinOnServer(oldPin: String, newPin: String, assignedUsername: String, dialog: AlertDialog, btnSubmit: MaterialButton) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {

                val jsonPayload = JSONObject().apply {
                    put("newPin", newPin)
                    put("username", assignedUsername)
                }

                val body = jsonPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$SCRIPT_URL/update_pin")
                    .addHeader("Authorization", "Bearer $API_TOKEN")
                    .addHeader("x-pin", oldPin)
                    .addHeader("x-username", assignedUsername)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseData = response.body?.string() ?: ""
                val resultJson = JSONObject(responseData)

                withContext(Dispatchers.Main) {
                    if (resultJson.optString("status") == "success") {
                        Toast.makeText(this@LoginActivity, "PIN successfully updated!", Toast.LENGTH_LONG).show()

                        prefs.edit().apply {
                            putString(PrefKeys.SAVED_PIN, newPin)
                            putString(PrefKeys.SAVED_USERNAME, assignedUsername)
                            apply()
                        }

                        dialog.dismiss()

                        // 🔥 FIX: Reset the timer before launching!
                        resetActivityTimer()
                        launchMain()
                    } else {
                        Toast.makeText(this@LoginActivity, "Error: ${resultJson.optString("error")}", Toast.LENGTH_LONG).show()
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "SAVE NEW PIN"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Network Error. Please try again.", Toast.LENGTH_LONG).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "SAVE NEW PIN"
                }
            }
        }
    }

    private fun launchMain() {
        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
    }
}