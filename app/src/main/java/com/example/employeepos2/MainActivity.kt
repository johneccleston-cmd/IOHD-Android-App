package com.example.employeepos2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val BASE_URL = "https://integrity-backend-cr02.onrender.com"
        private const val UPDATE_FCM_URL = "$BASE_URL/update_fcm_token"
        private const val API_TOKEN = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

        // These MUST match LoginActivity exactly
        private const val PREFS_FILE = "UserPrefs"
        private const val PREF_ROLE = "ROLE"
        private const val PREF_NAME = "NAME"
        private const val PREF_CAN_COLLECT = "CAN_COLLECT"
        private const val PREF_LAST_FCM_TOKEN = "LAST_FCM_TOKEN"

        private const val NOTIFICATION_PERMISSION_REQUEST = 101
    }

    private lateinit var navView: BottomNavigationView

    // 🔥 THE DECODER RING: Initializing the Encrypted Reader
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            this,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🛡️ SAFE ZONE FIX: Prevents the main app fragments from sliding under the status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        navView = findViewById(R.id.bottom_navigation)

       // requestNotificationPermission()
        configureNavigation()

        // 🔥 Determine exactly who logged in
        val name = prefs.getString(PREF_NAME, "") ?: ""
        val cleanName = name.replace(" ", "").lowercase()
        val role = prefs.getString(PREF_ROLE, "") ?: ""

        // 1. STRICTLY ENFORCE VISIBILITY EVERY TIME THE APP DRAWS (Outside of savedInstanceState)
        if (cleanName == "warehouse1" || role.uppercase().contains("WAREHOUSE")) {
            navView.visibility = View.GONE
        } else {
            navView.visibility = View.VISIBLE
            setupCircularProfileIcon()
        }

        // 2. ROUTE THEM TO THE RIGHT STARTING PAGE
        if (savedInstanceState == null) {
            if (cleanName == "warehouse1" || role.uppercase().contains("WAREHOUSE")) {
                loadRootFragment(InventoryMenuFragment())
            } else {
                navView.selectedItemId = R.id.nav_home
            }
        }

        handleIntentRouting(intent)
        //registerFcmToken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentRouting(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    private fun configureNavigation() {
        val canCollect = prefs.getBoolean(PREF_CAN_COLLECT, false)
        val role = prefs.getString(PREF_ROLE, "INSTALLER") ?: "INSTALLER"

        navView.menu.findItem(R.id.nav_jobs)?.isVisible = canCollect
        navView.menu.findItem(R.id.nav_leaderboard)?.isVisible = true

        navView.setOnItemSelectedListener { item ->
            navView.findViewById<View>(item.itemId)?.let { tab ->
                tab.animate().cancel()
                tab.scaleX = 0.86f; tab.scaleY = 0.86f
                tab.animate().scaleX(1f).scaleY(1f).setDuration(320)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            when (item.itemId) {
                R.id.nav_jobs -> { loadRootFragment(PaymentFragment()); true }
                R.id.nav_inventory -> { loadRootFragment(InventoryMenuFragment()); true }
                R.id.nav_home -> { loadRootFragment(HomeFragmentV2()); true }
                R.id.nav_leaderboard -> { loadRootFragment(TeamFragment()); true }
                R.id.nav_profile -> { loadRootFragment(ProfileFragmentV2()); true }
                else -> false
            }
        }
    }

    private fun loadRootFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun loadFragment(fragment: Fragment, addToStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    private fun handleIntentRouting(intent: Intent?) {
        if (intent?.getStringExtra("NAVIGATE_TO") == "JOB_DETAILS") {
            val jobNum = intent.getStringExtra("SF_JOB_NUM")
            val fragment = JobDetailsFragment().apply {
                arguments = Bundle().apply {
                    intent.extras?.let { putAll(it) }
                    putString("JOB_NUMBER", jobNum)
                }
            }
            loadFragment(fragment)
        }
    }

    private fun setupCircularProfileIcon() {
        val name = prefs.getString(PREF_NAME, "") ?: ""
        if (name.isEmpty()) return

        val fileName = name.lowercase().trim().replace(" ", "_")
        val resId = resources.getIdentifier(fileName, "drawable", packageName)
        if (resId == 0) return

        val originalDrawable = ContextCompat.getDrawable(this, resId) ?: return
        val normalDrawable = getCircularDrawable(originalDrawable, 40, null) ?: return
        val selectedDrawable = getCircularDrawable(originalDrawable, 40, Color.parseColor("#CC0007")) ?: return

        val stateListDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), selectedDrawable)
            addState(intArrayOf(), normalDrawable)
        }

        navView.itemIconTintList = null
        navView.menu.findItem(R.id.nav_profile)?.icon = stateListDrawable
    }

    private fun getCircularDrawable(drawable: Drawable, dpSize: Int, borderColor: Int?): Drawable? {
        return try {
            val size = (dpSize * resources.displayMetrics.density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = size.toFloat() / minOf(width, height)
            val outWidth = (width * scale).toInt()
            val outHeight = (height * scale).toInt()
            val left = (size - outWidth) / 2
            val top = (size - outHeight) / 2

            drawable.setBounds(left, top, left + outWidth, top + outHeight)
            drawable.draw(canvas)

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(output)
            val paint = Paint().apply { isAntiAlias = true; color = Color.BLACK }

            finalCanvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            finalCanvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap.recycle()
            paint.xfermode = null

            if (borderColor != null) {
                val borderPaint = Paint().apply {
                    color = borderColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * resources.displayMetrics.density
                    isAntiAlias = true
                }
                val radius = (size / 2f) - (borderPaint.strokeWidth / 2f)
                finalCanvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)
            }
            BitmapDrawable(resources, output)
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating circular drawable", e)
            null
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener

            val techName = prefs.getString(PREF_NAME, "") ?: ""
            if (techName.isNotEmpty()) {
                sendTokenToServer(techName, token)
                prefs.edit().putString(PREF_LAST_FCM_TOKEN, token).apply()
            }
        }
    }

    private fun sendTokenToServer(techName: String, token: String) {
        val json = JSONObject().apply { put("techName", techName); put("token", token) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(UPDATE_FCM_URL).addHeader("Authorization", "Bearer $API_TOKEN").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e(TAG, "Failed sending FCM token", e) }
            override fun onResponse(call: Call, response: Response) {
                response.use { if (it.isSuccessful) Log.d(TAG, "FCM token registered") }
            }
        })
    }
}