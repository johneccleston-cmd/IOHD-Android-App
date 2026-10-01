package com.example.employeepos2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout // 🔥 Added the FrameLayout import
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

// A simple data container for our Techs
data class TechData(val id: String, val name: String, val role: String)

class TeamCommissionFragment : Fragment(R.layout.fragment_team_commission) {

    // 🔥 Put your actual backend URL here that returns your list of techs!
    private val techsUrl = "https://integrity-backend-cr02.onrender.com/get_techs"

    private val client = OkHttpClient()
    private val realTechs = mutableListOf<TechData>()
    private lateinit var adapter: TechAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔥 THE UPGRADED BACK BUTTON
        // Make sure your fragment_team_commission.xml uses android:id="@+id/btnBack" for the FrameLayout!
        view.findViewById<FrameLayout>(R.id.btnBack)?.setOnClickListener {
            val fm = requireActivity().supportFragmentManager
            if (fm.backStackEntryCount > 0) {
                fm.popBackStack() // Safely returns to previous screen
            } else {
                // If Android forgot the history, manually load the screen you came from!
                // Example: (requireActivity() as MainActivity).loadFragment(AdminMenuFragment())
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        val rvTechList = view.findViewById<RecyclerView>(R.id.rvTechList)
        rvTechList.layoutManager = LinearLayoutManager(requireContext())

        // Set up the adapter with our (currently empty) list
        adapter = TechAdapter(realTechs) { selectedTech ->
            // Pass the Tech's name to the History screen
            val bundle = Bundle().apply {
                putString("TECH_NAME", selectedTech.name)
            }
            val historyFragment = CommissionHistoryFragment().apply {
                arguments = bundle
            }
            // Navigate to the History screen
            (requireActivity() as MainActivity).loadFragment(historyFragment)
        }

        rvTechList.adapter = adapter

        // Fetch the real data from your backend!
        fetchRealTechs()
    }

    private fun fetchRealTechs() {
        // Double check that this is the correct URL for your techs!
        val url = "https://integrity-backend-cr02.onrender.com/get_techs"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("TECH_DEBUG", "Network call completely failed!", e)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Network Error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                // 🔥 X-RAY VISION: This prints exactly what the server sent back into Android Studio!
                android.util.Log.d("TECH_DEBUG", "HTTP Status Code: ${response.code}")
                android.util.Log.d("TECH_DEBUG", "Raw JSON from server: $responseData")

                if (!response.isSuccessful || responseData.isNullOrBlank()) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                try {
                    // Let's see if the JSON parsing is what's crashing
                    val jsonArray = org.json.JSONArray(responseData)
                    val fetchedTechs = mutableListOf<TechData>()

                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)

                        // We will just grab whatever we can to see if it populates
                        val techName = jsonObject.optString("name", jsonObject.optString("techName", "No Name Found"))
                        val techId = jsonObject.optString("id", "N/A")

                        fetchedTechs.add(TechData(techId, techName, "Technician"))
                    }

                    android.util.Log.d("TECH_DEBUG", "Successfully parsed ${fetchedTechs.size} techs!")

                    activity?.runOnUiThread {
                        realTechs.clear()
                        realTechs.addAll(fetchedTechs)
                        adapter.notifyDataSetChanged()
                    }

                } catch (e: Exception) {
                    android.util.Log.e("TECH_DEBUG", "JSON Parsing Failed! The format doesn't match.", e)
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Data format error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // =====================================================================
    // RECYCLER VIEW ADAPTER
    // =====================================================================
    inner class TechAdapter(
        private val techList: List<TechData>,
        private val onTechClick: (TechData) -> Unit
    ) : RecyclerView.Adapter<TechAdapter.TechViewHolder>() {

        inner class TechViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvTechName)
            val tvRole: TextView = view.findViewById(R.id.tvTechRole)

            fun bind(tech: TechData) {
                tvName.text = tech.name
                tvRole.text = tech.role

                // Make the whole card clickable
                itemView.setOnClickListener { onTechClick(tech) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TechViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tech_card, parent, false)
            return TechViewHolder(view)
        }

        override fun onBindViewHolder(holder: TechViewHolder, position: Int) {
            holder.bind(techList[position])
        }

        override fun getItemCount(): Int = techList.size
    }
}