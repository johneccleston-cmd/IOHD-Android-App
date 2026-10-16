package com.example.employeepos2

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

class StockSearchFragment : Fragment(R.layout.fragment_stock_search) {

    private val masterInventory = mutableListOf<StockItem>()
    private var filteredInventory = mutableListOf<StockItem>()
    private lateinit var adapter: StockSearchAdapter
    private var selectedCategory = "ALL"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<EditText>(R.id.etSearchBar)
        val rvResults = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        val loader    = view.findViewById<ProgressBar>(R.id.searchLoader)
        val btnBack   = view.findViewById<FrameLayout>(R.id.btnBack)

        // Ensure Search text is Black
        

        btnBack?.setOnClickListener {
            (requireActivity() as MainActivity).loadFragment(InventoryMenuFragment())
        }

        adapter = StockSearchAdapter(filteredInventory)
        rvResults.layoutManager = LinearLayoutManager(requireContext())
        rvResults.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilters(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fetchInventory(loader)
    }

    private fun fetchInventory(loader: ProgressBar) {
        val request = Request.Builder()
            .url("https://integrity-backend-cr02.onrender.com/get_full_inventory")
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { loader.visibility = View.GONE }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                try {
                    val jsonArray = JSONArray(body)
                    masterInventory.clear()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)

                        val cat = obj.optString("category", "OTHER").trim().uppercase()
                        val name = obj.optString("name", "Unknown").trim()

                        // 🔥 THE FIREWALL: Skip the Google Sheets header row entirely!
                        if (cat == "CATEGORY" || name.uppercase() == "NAME") {
                            continue
                        }

                        masterInventory.add(StockItem(name, cat, obj.optInt("qty", 0)))
                    }
                    masterInventory.sortBy { it.name }

                    activity?.runOnUiThread {
                        if (isAdded) {
                            loader.visibility = View.GONE
                            buildCategoryButtons()
                            applyFilters(view?.findViewById<EditText>(R.id.etSearchBar)?.text.toString())
                        }
                    }
                } catch (e: Exception) { Log.e("STOCK", "Error", e) }
            }
        })
    }

    private fun buildCategoryButtons() {
        val container = view?.findViewById<LinearLayout>(R.id.llTechFilters) ?: return
        container.removeAllViews()

        // 🔥 DOUBLE CHECK: Filter the word out just in case it snuck through
        val uniqueCats = masterInventory.map { it.category }
            .filter { !it.equals("CATEGORY", ignoreCase = true) }
            .distinct()
            .sorted()

        val buttons = mutableListOf("ALL")
        buttons.addAll(uniqueCats)

        buttons.forEach { catName ->
            val btn = MaterialButton(requireContext()).apply {
                text = catName
                isAllCaps = true
                cornerRadius = 50
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 16, 0) }

                if (catName == selectedCategory) {
                    setBackgroundColor(Color.parseColor("#CC0007"))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                } else {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.parseColor("#4B5563"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#E5E7EB"))
                    strokeWidth = 3
                }

                setOnClickListener {
                    selectedCategory = catName
                    buildCategoryButtons()
                    applyFilters(view?.findViewById<EditText>(R.id.etSearchBar)?.text.toString())
                }
            }
            container.addView(btn)
        }
    }

    private fun applyFilters(query: String) {
        val q = query.trim().lowercase()
        val filtered = masterInventory.filter { item ->
            val matchesCat = (selectedCategory == "ALL" || item.category == selectedCategory)
            val matchesSearch = (q.isEmpty() || item.name.lowercase().contains(q))
            matchesCat && matchesSearch
        }
        filteredInventory.clear()
        filteredInventory.addAll(filtered)
        adapter.notifyDataSetChanged()
    }
}