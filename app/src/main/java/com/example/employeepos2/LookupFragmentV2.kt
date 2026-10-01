package com.example.employeepos2

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LookupFragmentV2 : Fragment(R.layout.activity_lookup) {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val jobIdInput = view.findViewById<TextInputEditText>(R.id.lookupJobId)
        val fetchBtn = view.findViewById<MaterialButton>(R.id.btnFetchJob)

        fetchBtn.setOnClickListener {
            val jobId = jobIdInput.text.toString().trim()
            if (jobId.isEmpty()) {
                Toast.makeText(requireContext(), "Enter Job #", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fetchBtn.isEnabled = false
            fetchBtn.text = "SEARCHING..."

            viewLifecycleOwner.lifecycleScope.launch {
                fetchJobData(jobId, fetchBtn)
            }
        }
    }

    private suspend fun fetchJobData(jobId: String, fetchBtn: MaterialButton) = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/fetch_sf_job?jobNum=$jobId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful || !body.trim().startsWith("{")) {
                throw Exception("Code: ${response.code} | Response: ${body.take(60)}")
            }

            val json = JSONObject(body)

            withContext(Dispatchers.Main) {
                if (json.optString("status") == "success") {
                    val customer = json.optString("customer", "Unknown")
                    val total = json.optString("total", "0.00")
                    val verifiedJobNum = json.optString("jobNum", jobId)

                    // Pass data safely using Bundles
                    val bundle = Bundle().apply {
                        // 🔥 FIXED: Synced keys globally across your components
                        putString("PREFILL_CUSTOMER_NAME", customer)
                        putString("PREFILL_JOB_NUM",       verifiedJobNum)
                        putString("SF_TOTAL",              total)
                    }

                    val jobsFragment = JobsFragmentV2().apply { arguments = bundle }
                    (requireActivity() as MainActivity).loadFragment(jobsFragment, true)

                } else {
                    val msg = json.optString("error", "Job Not Found")
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    fetchBtn.isEnabled = true
                    fetchBtn.text = "FETCH DETAILS"
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                fetchBtn.isEnabled = true
                fetchBtn.text = "FETCH DETAILS"
            }
        }
    }
}