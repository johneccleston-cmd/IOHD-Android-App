package com.example.employeepos2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

class MyDocumentsActivity : BaseActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_documents)

        // 🛡️ SAFE ZONE FIX: Keeps your top back button and document list out of the system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val techName = intent.getStringExtra("TECH_NAME") ?: ""

        findViewById<ImageView>(R.id.btnBackFromDocs).setOnClickListener {
            finish() // I just close this screen to go back to the profile
        }

        if (techName.isNotEmpty()) {
            fetchDocuments(techName)
        } else {
            findViewById<ProgressBar>(R.id.docLoader).visibility = View.GONE
            findViewById<TextView>(R.id.tvNoDocs).visibility = View.VISIBLE
        }
    }

    // I call my backend to see what files are in my Drive folder
    private fun fetchDocuments(techName: String) {
        val url = "https://integrity-backend-cr02.onrender.com/get_my_documents?techName=$techName"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.docLoader).visibility = View.GONE
                    Toast.makeText(this@MyDocumentsActivity, "Failed to load documents", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.docLoader).visibility = View.GONE

                    try {
                        val jsonArray = JSONArray(responseData)
                        val container = findViewById<LinearLayout>(R.id.llDocumentList)

                        if (jsonArray.length() == 0) {
                            findViewById<TextView>(R.id.tvNoDocs).visibility = View.VISIBLE
                        } else {
                            // I loop through each document and build a button for it!
                            for (i in 0 until jsonArray.length()) {
                                val doc = jsonArray.getJSONObject(i)
                                val docName = doc.getString("name")
                                val docUrl = doc.getString("url")

                                val btn = MaterialButton(this@MyDocumentsActivity).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        180 // 180px tall button
                                    ).apply { setMargins(0, 0, 0, 24) }

                                    text = docName
                                    isAllCaps = false
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    setTextColor(android.graphics.Color.parseColor("#CC0007"))
                                    strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#CC0007"))
                                    strokeWidth = 2
                                    cornerRadius = 16

                                    // When I click it, I open the PDF/Doc in the browser or Drive app!
                                    setOnClickListener {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                                        startActivity(browserIntent)
                                    }
                                }
                                container.addView(btn)
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MyDocumentsActivity, "Error parsing documents", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}