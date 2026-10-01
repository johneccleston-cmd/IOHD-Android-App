package com.example.employeepos2

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout // 🔥 Added the FrameLayout import
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class TechAllowance(val name: String, val allowance: Int)

class TeamToolAllowanceFragment : Fragment(R.layout.fragment_team_tool_allowance) {

    private val client = OkHttpClient()
    private val allowanceList = mutableListOf<TechAllowance>()
    private lateinit var adapter: AllowanceAdapter
    private lateinit var loader: ProgressBar
    private lateinit var rvAllowances: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔥 THE UPGRADED BACK BUTTON
        view.findViewById<FrameLayout>(R.id.btnBack).setOnClickListener {
            // This safely pops the screen off the stack instead of hard-loading a new one
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loader = view.findViewById(R.id.loaderAllowances)
        rvAllowances = view.findViewById(R.id.rvTeamAllowances)

        rvAllowances.layoutManager = LinearLayoutManager(requireContext())
        adapter = AllowanceAdapter(allowanceList)
        rvAllowances.adapter = adapter

        fetchTeamAllowances()
    }

    private fun fetchTeamAllowances() {
        loader.visibility = View.VISIBLE
        rvAllowances.visibility = View.GONE

        val url = "https://integrity-backend-cr02.onrender.com/team_tool_allowances"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    loader.visibility = View.GONE
                    Toast.makeText(requireContext(), "Connection Error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                activity?.runOnUiThread {
                    loader.visibility = View.GONE
                    rvAllowances.visibility = View.VISIBLE
                    try {
                        val json = JSONObject(body)
                        if (json.optString("status") == "success") {
                            allowanceList.clear()
                            val dataArray = json.optJSONArray("data") ?: JSONArray()
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i)
                                if (item != null) {
                                    allowanceList.add(
                                        TechAllowance(
                                            name = item.optString("name", "Unknown"),
                                            allowance = item.optInt("allowance", 1000)
                                        )
                                    )
                                }
                            }
                            adapter.notifyDataSetChanged()
                        } else {
                            Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Data Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // --- RECYCLERVIEW ADAPTER ---
    inner class AllowanceAdapter(private val list: List<TechAllowance>) :
        RecyclerView.Adapter<AllowanceAdapter.AllowanceViewHolder>() {

        inner class AllowanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvTechName)
            val tvAmount: TextView = view.findViewById(R.id.tvTechAllowance)
            val pbAllowance: LiquidProgressBar = view.findViewById(R.id.pbTechAllowance)

            fun bind(tech: TechAllowance) {
                tvName.text = tech.name
                tvAmount.text = "$${tech.allowance} Left"

                // Animate each liquid bar!
                val targetProgress = (tech.allowance.toFloat() / 1000f).coerceIn(0f, 1f)
                ObjectAnimator.ofFloat(pbAllowance, "progress", 0f, targetProgress).apply {
                    duration = 1500
                    interpolator = android.view.animation.DecelerateInterpolator()
                    start()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllowanceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_team_tool_allowance, parent, false)
            return AllowanceViewHolder(view)
        }

        override fun onBindViewHolder(holder: AllowanceViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size
    }
}