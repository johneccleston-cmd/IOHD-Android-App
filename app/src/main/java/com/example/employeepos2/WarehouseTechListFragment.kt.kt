package com.example.employeepos2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

// 🔥 NEW: Data class to hold both the name and their status
data class TechItem(val name: String, val hasItems: Boolean)

class WarehouseTechListFragment : Fragment(R.layout.fragment_tech_list) {

    private lateinit var rvTechs: RecyclerView
    private lateinit var loaderTechs: ProgressBar
    private lateinit var adapter: TechListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTechs = view.findViewById(R.id.rvTechs)
        loaderTechs = view.findViewById(R.id.loaderTechs)

        rvTechs.layoutManager = LinearLayoutManager(requireContext())
        adapter = TechListAdapter { selectedTech ->
            // When a tech is clicked, open their Pick List!
            val pickListFragment = InventoryFragmentV2().apply {
                arguments = Bundle().apply {
                    putString("TARGET_TECH_NAME", selectedTech)
                }
            }
            (requireActivity() as MainActivity).loadFragment(pickListFragment, true)
        }
        rvTechs.adapter = adapter

        fetchTechs()
    }

    private fun fetchTechs() {
        val request = Request.Builder()
            .url("https://integrity-backend-cr02.onrender.com/get_techs")
            .header("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .get()
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    loaderTechs.visibility = View.GONE
                    Toast.makeText(context, "Network Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread { loaderTechs.visibility = View.GONE }

                if (response.isSuccessful) {
                    val responseData = response.body?.string() ?: "[]"
                    try {
                        val jsonArray = JSONArray(responseData)
                        val techList = mutableListOf<TechItem>()

                        for (i in 0 until jsonArray.length()) {
                            val techObj = jsonArray.getJSONObject(i)
                            val name = techObj.getString("name")

                            // 🔥 Look for hasItems (defaults to true if backend hasn't updated yet)
                            val hasItems = techObj.optBoolean("hasItems", true)

                            techList.add(TechItem(name, hasItems))
                        }

                        // Optional: Sort so techs WITH items are at the top, and grayed out techs are at the bottom
                        techList.sortByDescending { it.hasItems }

                        activity?.runOnUiThread {
                            if (techList.isEmpty()) {
                                Toast.makeText(context, "No techs found", Toast.LENGTH_LONG).show()
                            } else {
                                rvTechs.visibility = View.VISIBLE
                                adapter.submitList(techList)
                            }
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Data Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Server Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}

// --- ADAPTER ---
class TechListAdapter(private val onTechClick: (String) -> Unit) : RecyclerView.Adapter<TechListAdapter.ViewHolder>() {

    private var techList = listOf<TechItem>()

    fun submitList(list: List<TechItem>) {
        techList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tech, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tech = techList[position]
        holder.tvTechName.text = tech.name

        // 🔥 WAREHOUSE MAGIC: Gray out the entire row if they have no items
        if (tech.hasItems) {
            holder.itemView.alpha = 1.0f // Normal full color
        } else {
            holder.itemView.alpha = 0.4f // Grayed out and faded
        }

        holder.itemView.setOnClickListener { onTechClick(tech.name) }
    }

    override fun getItemCount() = techList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTechName: TextView = view.findViewById(R.id.tvTechName)
    }
}