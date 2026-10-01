package com.example.employeepos2

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.content.Intent
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MasterScheduleActivity : BaseActivity() {

    private val client = OkHttpClient()
    private val allJobsForToday = mutableListOf<JSONObject>()
    private var currentActiveFilter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_schedule)

        findViewById<FrameLayout>(R.id.btnBack).setOnClickListener { finish() }

        // Date Header
        val tvDateHeader = findViewById<TextView>(R.id.tvMasterScheduleDate)
        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        tvDateHeader.text = sdf.format(Date())

        fetchMasterSchedule()
    }

    private fun fetchMasterSchedule() {
        val url = "https://integrity-backend-cr02.onrender.com/get_master_schedule"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.masterLoader).visibility = View.GONE
                    Toast.makeText(this@MasterScheduleActivity, "Failed to load schedule", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.masterLoader).visibility = View.GONE
                    try {
                        val jsonArray = JSONArray(responseData)
                        allJobsForToday.clear()

                        for (i in 0 until jsonArray.length()) {
                            val job = jsonArray.getJSONObject(i)
                            var techs = job.optString("techs", "")
                            techs = techs.split(",").map { it.trim() }
                                .filter { !it.equals("Integrity Overhead Door Collections", ignoreCase = true) }
                                .joinToString(", ")
                            if (techs.isEmpty()) techs = "Unassigned"

                            job.put("techs", techs)
                            allJobsForToday.add(job)
                        }

                        buildTechFilterButtons()
                        renderFilteredJobs("ALL")
                    } catch (e: Exception) {
                        Toast.makeText(this@MasterScheduleActivity, "Error parsing schedule", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun buildTechFilterButtons() {
        val filterContainer = findViewById<LinearLayout>(R.id.llTechFilters)
        filterContainer.removeAllViews()

        val uniqueTechs = mutableSetOf<String>()
        allJobsForToday.forEach { job ->
            val techString = job.optString("techs", "Unassigned")
            if (techString != "Unassigned") {
                techString.split(",").forEach { techName ->
                    val cleanName = techName.trim()
                    if (cleanName.isNotEmpty()) uniqueTechs.add(cleanName)
                }
            }
        }

        val buttonNames = mutableListOf("ALL")
        buttonNames.addAll(uniqueTechs.sorted())

        buttonNames.forEach { techName ->
            val btn = MaterialButton(this).apply {
                text = techName
                isAllCaps = true
                cornerRadius = 50
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 24, 0) }

                if (techName == currentActiveFilter) {
                    setBackgroundColor(Color.parseColor("#CC0007"))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                } else {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.parseColor("#CC0007"))
                    strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CC0007"))
                    strokeWidth = 3
                }

                setOnClickListener {
                    currentActiveFilter = techName
                    buildTechFilterButtons()
                    renderFilteredJobs(techName)
                }
            }
            filterContainer.addView(btn)
        }
    }

    private fun renderFilteredJobs(filterTech: String) {
        val container = findViewById<LinearLayout>(R.id.llMasterScheduleList)
        container.removeAllViews()

        val jobsToShow = if (filterTech == "ALL") allJobsForToday else allJobsForToday.filter { it.optString("techs", "").contains(filterTech, ignoreCase = true) }

        if (jobsToShow.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No jobs scheduled for $filterTech today."
                textSize = 16f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 50, 0, 0)
            }
            container.addView(emptyText)
            return
        }

        jobsToShow.forEach { job ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
                setCardBackgroundColor(Color.WHITE)
                radius = 16f
                cardElevation = 8f
                strokeColor = Color.parseColor("#E0E0E0")
                strokeWidth = 2
                isClickable = true
                isFocusable = true
            }

            val innerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
            val header = TextView(this).apply { text = "#${job.optString("number", "---")} - ${job.optString("status", "SCHEDULED")}"; setTextColor(Color.parseColor("#757575")); textSize = 14f }
            val customer = TextView(this).apply { text = job.optString("customer", "Unknown"); setTextColor(Color.BLACK); textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 8, 0, 8) }

            val techsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 16) }
            val techLabel = TextView(this).apply { text = "Techs: "; setTextColor(Color.parseColor("#CC0007")); textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 16, 0) }
            techsLayout.addView(techLabel)

            val techsString = job.optString("techs", "Unassigned")
            if (techsString == "Unassigned") {
                val unassignedText = TextView(this).apply { text = "Unassigned"; setTextColor(Color.DKGRAY); textSize = 14f; setTypeface(null, android.graphics.Typeface.ITALIC) }
                techsLayout.addView(unassignedText)
            } else {
                techsString.split(",").map { it.trim() }.forEach { techName ->
                    if (techName.isNotEmpty()) {
                        val fileName = techName.lowercase().replace(" ", "_")
                        val resId = resources.getIdentifier(fileName, "drawable", packageName)

                        if (resId != 0) {
                            val sizePx = (32 * resources.displayMetrics.density).toInt()
                            val marginPx = (8 * resources.displayMetrics.density).toInt()
                            val imageWrapper = MaterialCardView(this).apply {
                                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { setMargins(0, 0, marginPx, 0) }
                                radius = sizePx / 2f; cardElevation = 0f; strokeColor = Color.parseColor("#E0E0E0"); strokeWidth = 2
                            }
                            val imageView = ImageView(this).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                                scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(resId)
                            }
                            imageWrapper.addView(imageView)
                            techsLayout.addView(imageWrapper)
                        } else {
                            val nameTag = TextView(this).apply { text = techName; setTextColor(Color.DKGRAY); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 16, 0) }
                            techsLayout.addView(nameTag)
                        }
                    }
                }
            }

            val location = TextView(this).apply { text = job.optString("location", "No Location"); setTextColor(Color.DKGRAY); textSize = 14f }

            innerLayout.addView(header); innerLayout.addView(customer); innerLayout.addView(techsLayout); innerLayout.addView(location)
            card.addView(innerLayout); container.addView(card)

            card.setOnClickListener {
                val intent = Intent(this@MasterScheduleActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                    putExtra("NAVIGATE_TO", "JOB_DETAILS")
                    putExtra("SF_JOB_NUM", job.optString("number", "---"))
                    putExtra("CUSTOMER_NAME", job.optString("customer", "Unknown"))
                    putExtra("CONTACT_NAME", job.optString("contact", "No Contact"))
                    putExtra("JOB_DESCRIPTION", job.optString("description", ""))
                    putExtra("JOB_LOCATION", job.optString("location", ""))
                    putExtra("JOB_STATUS", job.optString("status", "SCHEDULED"))
                    putExtra("JOB_TOTAL", job.optString("total", "0.00"))
                    putExtra("ASSIGNED_TECHS", job.optString("techs", ""))

                    // 🔥 Pass pure notes
                    putExtra("JOB_NOTES", job.optString("notes", ""))
                }
                startActivity(intent)
            }
        }
    }
}