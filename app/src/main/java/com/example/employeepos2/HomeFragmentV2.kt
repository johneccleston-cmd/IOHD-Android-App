package com.example.employeepos2

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit

@SuppressLint("SetTextI18n")
class HomeFragmentV2 : Fragment(R.layout.fragment_home_v2) {

    private lateinit var rvSchedule: RecyclerView
    private lateinit var jobAdapter: JobScheduleAdapter

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            requireContext(),
            "UserPrefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val welcomeText  = view.findViewById<TextView>(R.id.welcomeText)
        val adminSection = view.findViewById<LinearLayout>(R.id.adminSection)
        rvSchedule       = view.findViewById(R.id.rvSchedule)

        setupRecyclerView()

        val name  = encryptedPrefs.getString("NAME", "Team Member") ?: "Team Member"
        val role  = encryptedPrefs.getString("ROLE", "INSTALLER") ?: "INSTALLER"
        val lastSummary = encryptedPrefs.getString("LAST_SUMMARY", "Analyzing your day...")

        view.findViewById<TextView>(R.id.tvDailySummary).text = lastSummary
        welcomeText.text = "${getGreeting()}, $name"
        view.findViewById<TextView>(R.id.tvDateLabel).text =
            java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date())

        val cleanName = name.trim()
        val cleanRole = role.trim().uppercase()

        val isManagement = cleanName.equals("Ryan Schwartz", ignoreCase = true) ||
                cleanRole == "ADMIN" ||
                cleanRole == "OWNER" ||
                cleanRole == "OFFICE MANAGER" ||
                cleanRole == "OFFICE"

        adminSection.visibility = if (cleanRole == "ADMIN") View.VISIBLE else View.GONE

        val earningsPanel = view.findViewById<LinearLayout>(R.id.btnEarningsPanel)
        val tvEarnings = view.findViewById<TextView>(R.id.tvEarningsTotal)

        if (isManagement) {
            earningsPanel?.visibility = View.GONE
        } else {
            earningsPanel?.visibility = View.VISIBLE
            tvEarnings?.text = "$0.00"

            earningsPanel?.setOnClickListener {
                (requireActivity() as MainActivity).loadFragment(CommissionFragment(), true)
            }
        }

        loadCachedData(name, view)

        viewLifecycleOwner.lifecycleScope.launch {
            launch { fetchWeather(view) }
            launch { fetchDailySchedule(name) }
            launch { fetchAiBriefing(name) }
        }
    }

    private fun setupRecyclerView() {
        jobAdapter = JobScheduleAdapter { clickedJob ->
            val bundle = Bundle().apply {
                putInt("INTERNAL_SF_ID",     clickedJob.internalSfId)
                putString("JOB_NUMBER",      clickedJob.number)
                putString("CUSTOMER_NAME",   clickedJob.customer)
                putString("CONTACT_NAME",    clickedJob.contact)
                putString("JOB_STATUS",      clickedJob.status)
                putString("JOB_DESCRIPTION", clickedJob.description)
                putString("JOB_LOCATION",    clickedJob.location)
                putString("ASSIGNED_TECHS",  clickedJob.techs)
                putString("JOB_TOTAL",       clickedJob.total)
                putString("JOB_NOTES",       clickedJob.notes)
                putString("PO_NUMBER",       clickedJob.poNumber)

                // 🔥 PASS THE ESTIMATE FLAG TO THE DETAILS SCREEN
                putBoolean("IS_ESTIMATE",    clickedJob.isEstimate)
            }
            val detailsFragment = JobDetailsFragment().apply { arguments = bundle }
            (requireActivity() as MainActivity).loadFragment(detailsFragment, true)
        }

        rvSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = jobAdapter
        }
    }

    private fun loadCachedData(techName: String, rootView: View) {
        val wPrefs = requireContext().getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE)
        val temp = wPrefs.getString("temp", "--")
        val outlook = wPrefs.getString("outlook", "Refreshing...")
        val highTemp = wPrefs.getString("high", "--")
        val lowTemp = wPrefs.getString("low", "--")
        val iconUrl = wPrefs.getString("iconUrl", "")

        rootView.findViewById<TextView>(R.id.tvWeatherTemp)?.text = "$temp°F"
        rootView.findViewById<TextView>(R.id.tvWeatherOutlook)?.text = outlook
        rootView.findViewById<TextView>(R.id.tvWeatherHigh)?.text = "H: $highTemp°"
        rootView.findViewById<TextView>(R.id.tvWeatherLow)?.text = "L: $lowTemp°"

        if (!iconUrl.isNullOrEmpty() && isAdded) {
            rootView.findViewById<ImageView>(R.id.ivWeatherIcon)?.let { Glide.with(this).load(iconUrl).into(it) }
        }

        val sPrefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        val cachedJson = sPrefs.getString("CACHED_SCHEDULE_$techName", "[]") ?: "[]"
        if (cachedJson.length > 5) {
            parseAndSubmitJobs(cachedJson)
        }
    }

    private suspend fun fetchWeather(rootView: View) = withContext(Dispatchers.IO) {
        val url = "https://api.weatherapi.com/v1/forecast.json?key=c6c7ec80273240c0ba8151603261604&q=Springdale&days=1&aqi=no&alerts=no"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val current = json.getJSONObject("current")
                val condition = current.getJSONObject("condition")
                val forecastDay = json.getJSONObject("forecast").getJSONArray("forecastday").getJSONObject(0).getJSONObject("day")

                val temp = current.getDouble("temp_f").toInt().toString()
                val outlook = condition.getString("text")
                val highTemp = forecastDay.getDouble("maxtemp_f").toInt().toString()
                val lowTemp = forecastDay.getDouble("mintemp_f").toInt().toString()
                val iconUrl = "https:" + condition.getString("icon")

                requireContext().getSharedPreferences("WeatherPrefs", Context.MODE_PRIVATE).edit().apply {
                    putString("temp", temp)
                    putString("outlook", outlook)
                    putString("high", highTemp)
                    putString("low", lowTemp)
                    putString("iconUrl", iconUrl)
                    apply()
                }

                withContext(Dispatchers.Main) {
                    rootView.findViewById<TextView>(R.id.tvWeatherTemp)?.text = "$temp°F"
                    rootView.findViewById<TextView>(R.id.tvWeatherOutlook)?.text = outlook
                    rootView.findViewById<TextView>(R.id.tvWeatherHigh)?.text = "H: $highTemp°"
                    rootView.findViewById<TextView>(R.id.tvWeatherLow)?.text = "L: $lowTemp°"
                    rootView.findViewById<ImageView>(R.id.ivWeatherIcon)?.let { Glide.with(this@HomeFragmentV2).load(iconUrl).into(it) }
                }
            }
        } catch (e: Exception) { Log.e("HOME", "Weather refresh failed", e) }
    }

    private suspend fun fetchDailySchedule(techName: String) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/get_schedule?techName=${Uri.encode(techName)}"
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiToken").build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val freshJson = response.body?.string() ?: "[]"
                requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
                    .edit().putString("CACHED_SCHEDULE_$techName", freshJson).apply()

                withContext(Dispatchers.Main) {
                    parseAndSubmitJobs(freshJson)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Offline Mode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchAiBriefing(techName: String) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/get_briefing?techName=${Uri.encode(techName)}"
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $apiToken").build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val summary = JSONObject(response.body?.string() ?: "{}").optString("summary", "Ready!")
                encryptedPrefs.edit().putString("LAST_SUMMARY", summary).apply()
                withContext(Dispatchers.Main) {
                    view?.findViewById<TextView>(R.id.tvDailySummary)?.text = summary
                }
            }
        } catch (e: Exception) { }
    }

    private fun parseAndSubmitJobs(jsonString: String) {
        try {
            val jsonArray = JSONArray(jsonString)
            val jobList = mutableListOf<ScheduledJob>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                jobList.add(ScheduledJob(
                    obj.optInt("internal_sf_id", 0),
                    obj.optString("number", "---"),
                    obj.optString("customer", "Unknown"),
                    obj.optString("contact", "No Contact"),
                    obj.optString("status", "SCHEDULED"),
                    obj.optString("description", "No description provided."),
                    obj.optString("location", ""),
                    obj.optString("techs", ""),
                    obj.optString("total", "0.00"),
                    obj.optString("notes", ""),
                    obj.optString("poNumber", ""),

                    // 🔥 PARSE THE IS_ESTIMATE FLAG FROM THE BACKEND
                    obj.optBoolean("isEstimate", false)
                ))
            }
            jobAdapter.submitList(jobList)
            view?.findViewById<TextView>(R.id.tvScheduleCount)?.apply {
                text = jobList.size.toString()
                visibility = if (jobList.isEmpty()) View.GONE else View.VISIBLE
            }
            view?.findViewById<View>(R.id.tvScheduleEmpty)?.visibility =
                if (jobList.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("HOME", "Parsing error", e)
        }
    }

    private fun getGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..23 -> "Good Evening"
        else      -> "Hey"
    }
}