package com.example.employeepos2

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// 1. Data Class preserved
data class CommissionRecord(val jobNum: String, val date: String, val amount: String)

class CommissionHistoryFragment : Fragment(R.layout.fragment_commission_history) {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var adapter: HistoryAdapter
    private lateinit var tvWeekly: TextView
    private lateinit var tvMonthly: TextView
    private lateinit var tvYTD: TextView

    // 🔥 THE DECODER RING: Decrypts tech identity if needed
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val techName = arguments?.getString("TECH_NAME") ?: "TECHNICIAN"

        // Setup UI
        view.findViewById<TextView>(R.id.tvHistoryHeader).text = "${techName.uppercase()}'S COMMISSIONS"

        // Back Button Logic
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            (requireActivity() as MainActivity).loadFragment(TeamCommissionFragment(), false)
        }

        tvWeekly = view.findViewById(R.id.tvWeeklyPayout)
        tvMonthly = view.findViewById(R.id.tvMonthlyPayout)
        tvYTD = view.findViewById(R.id.tvYtdPayout)

        // Initialize RecyclerView
        val rvHistoryList = view.findViewById<RecyclerView>(R.id.rvHistoryList)
        rvHistoryList.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter()
        rvHistoryList.adapter = adapter

        loadCommissions(techName)
    }

    private fun loadCommissions(techName: String) {
        lifecycleScope.launch {
            fetchCommissionData(techName)
        }
    }

    private suspend fun fetchCommissionData(techName: String) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/get_tech_commissions?techName=${java.net.URLEncoder.encode(techName, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")

                // Parse stats
                val weekly = json.optString("weekly", "$0.00")
                val monthly = json.optString("monthly", "$0.00")
                val ytd = json.optString("ytd", "$0.00")

                // Parse records array
                val recordsArray = json.optJSONArray("records") ?: JSONArray()
                val freshRecords = mutableListOf<CommissionRecord>()
                for (i in 0 until recordsArray.length()) {
                    val obj = recordsArray.getJSONObject(i)
                    freshRecords.add(CommissionRecord(
                        obj.optString("jobNum"),
                        obj.optString("date"),
                        obj.optString("amount")
                    ))
                }

                withContext(Dispatchers.Main) {
                    tvWeekly.text = weekly
                    tvMonthly.text = monthly
                    tvYTD.text = ytd
                    adapter.submitList(freshRecords)
                }
            }
        } catch (e: Exception) {
            Log.e("COMMISSIONS", "Fetch failed", e)
        }
    }


    inner class HistoryAdapter : ListAdapter<CommissionRecord, HistoryAdapter.RecordViewHolder>(RecordDiffCallback()) {

        inner class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvJob: TextView = view.findViewById(R.id.tvRecordJobNum)
            val tvDate: TextView = view.findViewById(R.id.tvRecordDate)
            val tvAmount: TextView = view.findViewById(R.id.tvRecordAmount)

            fun bind(record: CommissionRecord) {
                tvJob.text = "Job #${record.jobNum}"
                tvDate.text = record.date
                tvAmount.text = record.amount
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_commission_record, parent, false)
            return RecordViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    class RecordDiffCallback : DiffUtil.ItemCallback<CommissionRecord>() {
        override fun areItemsTheSame(oldItem: CommissionRecord, newItem: CommissionRecord) = oldItem.jobNum == newItem.jobNum
        override fun areContentsTheSame(oldItem: CommissionRecord, newItem: CommissionRecord) = oldItem == newItem
    }
}