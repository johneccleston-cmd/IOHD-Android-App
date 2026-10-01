package com.example.employeepos2 // CHANGE TO YOUR PACKAGE

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CycleCountListFragment : Fragment(R.layout.fragment_cycle_count_list) {

    private lateinit var adapter: CycleCountAdapter
    private var zoneName: String = "Zone"

    private val masterZoneItems = mutableListOf<ZoneCountItem>()
    private val filteredZoneItems = mutableListOf<ZoneCountItem>()
    private var selectedCategory = "ALL"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // DYNAMICALLY ADJUST BUTTON MARGIN BASED ON PROFILE / BOTTOM NAV BAR PRESENCE
        view.post {
            if (!isAdded) return@post
            val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitCount) ?: return@post

            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val rootBottomOnScreen = location[1] + view.height
            val screenHeight = resources.displayMetrics.heightPixels

            val insets = ViewCompat.getRootWindowInsets(view)
            val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0

            val params = btnSubmit.layoutParams as ViewGroup.MarginLayoutParams
            val baseMargin = (16 * resources.displayMetrics.density).toInt()

            if (rootBottomOnScreen >= (screenHeight - 30)) {
                params.bottomMargin = bottomInset + baseMargin
            } else {
                params.bottomMargin = baseMargin
            }
            btnSubmit.layoutParams = params
        }

        zoneName = arguments?.getString("ZONE_NAME") ?: "Zone"
        view.findViewById<TextView>(R.id.tvZoneTitle).text = "$zoneName Count"

        val rv = view.findViewById<RecyclerView>(R.id.rvCountItems)
        rv.layoutManager = LinearLayoutManager(requireContext())

        // 🔥 PASS CALLBACK TO UPDATE PROGRESS BAR REAL-TIME WHEN COUNTS CHANGE
        adapter = CycleCountAdapter(filteredZoneItems) {
            updateProgress()
        }
        rv.adapter = adapter

        // AUTO-HIDE KEYBOARD ON SCROLL
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard()
                }
            }
        })

        setupSwipeToIgnore(rv)

        // Handle back button click
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            hideKeyboard()
            parentFragmentManager.popBackStack()
        }

        view.findViewById<MaterialButton>(R.id.btnSubmitCount).setOnClickListener {
            submitCount()
        }

        fetchInventoryForZone()
    }

    // 🔥 LIVE PROGRESS CALCULATOR
    private fun updateProgress() {
        val total = masterZoneItems.size
        val counted = masterZoneItems.count { it.countedQty != null }
        val percent = if (total > 0) (counted * 100) / total else 0

        val tvProgressPercent = view?.findViewById<TextView>(R.id.tvProgressPercent)
        val progressZoneCount = view?.findViewById<ProgressBar>(R.id.progressZoneCount)

        tvProgressPercent?.text = "$counted / $total ($percent%)"
        progressZoneCount?.max = if (total > 0) total else 1
        progressZoneCount?.progress = counted
    }

    private fun hideKeyboard() {
        val currentFocusedView = activity?.currentFocus ?: view ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
        currentFocusedView.clearFocus()
    }

    private fun setupSwipeToIgnore(recyclerView: RecyclerView) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                hideKeyboard()
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return

                val item = adapter.getItemAt(position)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Mark as Non-Inventory?")
                    .setMessage("Are you sure you want to mark '${item.name}' as a Non-Inventory item?\n\nThis will permanently remove it from all future cycle counts.")
                    .setCancelable(false)
                    .setPositiveButton("YES, HIDE ITEM") { dialog, _ ->
                        markItemAsNonInventory(item, position)
                        dialog.dismiss()
                    }
                    .setNegativeButton("CANCEL") { dialog, _ ->
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint()

                if (dX < 0) { // Swiping Left
                    paint.color = Color.parseColor("#CC0007")
                    val background = RectF(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    )
                    c.drawRect(background, paint)

                    paint.color = Color.BLACK
                    paint.textSize = 46f
                    paint.isFakeBoldText = true
                    paint.textAlign = Paint.Align.RIGHT

                    val textMargin = 40f
                    val textY = itemView.top + (itemView.height / 2f) + (paint.textSize / 3f)
                    c.drawText("NON-INVENTORY", itemView.right - textMargin, textY, paint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun markItemAsNonInventory(item: ZoneCountItem, position: Int) {
        val prefs = requireActivity().getSharedPreferences("CycleCountPrefs", Context.MODE_PRIVATE)
        val nonInventorySet = prefs.getStringSet("NonInventoryItems", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        nonInventorySet.add(item.name)
        prefs.edit().putStringSet("NonInventoryItems", nonInventorySet).apply()

        masterZoneItems.removeAll { it.name == item.name }
        adapter.removeItemAt(position)
        updateProgress() // 🔥 RECALCULATE PROGRESS

        Toast.makeText(requireContext(), "'${item.name}' hidden from cycle counts.", Toast.LENGTH_SHORT).show()
    }

    private fun fetchInventoryForZone() {
        val pb = view?.findViewById<ProgressBar>(R.id.progressBar)
        pb?.visibility = View.VISIBLE

        val request = Request.Builder()
            .url("https://integrity-backend-cr02.onrender.com/get_full_inventory")
            .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    pb?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Connection failed", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"

                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
                        pb?.visibility = View.GONE
                        Toast.makeText(requireContext(), "Server Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    val jsonArray = JSONArray(body)
                    val allItems = mutableListOf<ZoneCountItem>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val cat = obj.optString("category", "").trim()
                        val name = obj.optString("name", "").trim()

                        if (cat.uppercase() == "CATEGORY" || name.uppercase() == "NAME") continue

                        allItems.add(
                            ZoneCountItem(
                                name = name,
                                category = cat,
                                systemQty = obj.optInt("qty", 0)
                            )
                        )
                    }
                    val targetCategories = when (zoneName) {
                        "Zone 1" -> listOf(
                            "door",
                            "strut",
                            "shaft",
                            "operator head",
                            "residential operator head",
                            "operator rail",
                            "operator part",
                            "remote",
                            "keypad",
                            "photo eye"
                        )
                        "Zone 2" -> listOf("weather seal", "bolt", "spring", "door", "punched angle", "miscellaneous")
                        "Zone 3" -> listOf("door", "track", "operator head", "operator rail", "wall station")
                        "Zone 4" -> listOf("door", "bearing", "bracket", "cable", "coupler", "drum", "bearing plate", "hinge", "roller", "sprocket")
                        else -> emptyList()
                    }

                    val prefs = requireActivity().getSharedPreferences("CycleCountPrefs", Context.MODE_PRIVATE)
                    val countedSet = prefs.getStringSet("CountedItems_$zoneName", emptySet()) ?: emptySet()
                    val nonInventorySet = prefs.getStringSet("NonInventoryItems", emptySet()) ?: emptySet()

                    masterZoneItems.clear()

                    val zoneItems = allItems.filter { item ->
                        val catLower = item.category.lowercase()
                        val nameLower = item.name.lowercase()

                        val isRightZone = targetCategories.any { target -> catLower.contains(target) }
                        val isNotCountedYet = !countedSet.contains(item.name)
                        val isNotNonInventory = !nonInventorySet.contains(item.name)

                        // 🔥 Exclude track brackets from Zone 4
                        val isTrackBracket = catLower.contains("track bracket") || nameLower.contains("track bracket")
                        val isExcludedInZone = zoneName == "Zone 4" && isTrackBracket

                        isRightZone && isNotCountedYet && isNotNonInventory && !isExcludedInZone
                    }.sortedBy { it.category }

                    masterZoneItems.addAll(zoneItems)

                    activity?.runOnUiThread {
                        if (isAdded) {
                            pb?.visibility = View.GONE
                            buildCategoryButtons()
                            applyFilters("ALL")
                            updateProgress() // 🔥 INITIALIZE PROGRESS BAR

                            if (masterZoneItems.isEmpty()) {
                                Toast.makeText(requireContext(), "Zone fully counted!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        pb?.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error parsing data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun buildCategoryButtons() {
        val container = view?.findViewById<LinearLayout>(R.id.llCategoryFilters) ?: return
        container.removeAllViews()

        val uniqueCats = masterZoneItems.map { it.category }.distinct().sorted()
        val buttons = mutableListOf("ALL")
        buttons.addAll(uniqueCats)

        buttons.forEach { catName ->
            val btn = MaterialButton(requireContext()).apply {
                text = catName
                isAllCaps = true
                cornerRadius = 50
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 24, 0) }

                if (catName == selectedCategory) {
                    setBackgroundColor(Color.parseColor("#CC0007"))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                } else {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.BLACK)
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#CC0007"))
                    strokeWidth = 3
                }

                setOnClickListener {
                    hideKeyboard()
                    selectedCategory = catName
                    buildCategoryButtons()
                    applyFilters(catName)
                }
            }
            container.addView(btn)
        }
    }

    private fun applyFilters(categoryToFilter: String) {
        filteredZoneItems.clear()
        if (categoryToFilter == "ALL") {
            filteredZoneItems.addAll(masterZoneItems)
        } else {
            filteredZoneItems.addAll(masterZoneItems.filter { it.category == categoryToFilter })
        }
        adapter.notifyDataSetChanged()
    }

    private fun submitCount() {
        hideKeyboard()
        if (!::adapter.isInitialized) return

        val allItems = adapter.getCountedItems()
        val countedItems = allItems.filter { it.countedQty != null }

        if (countedItems.isEmpty()) {
            Toast.makeText(requireContext(), "No counts entered!", Toast.LENGTH_SHORT).show()
            return
        }

        val pb = view?.findViewById<ProgressBar>(R.id.progressBar)
        val btnSubmit = view?.findViewById<MaterialButton>(R.id.btnSubmitCount)

        pb?.visibility = View.VISIBLE
        btnSubmit?.isEnabled = false
        btnSubmit?.text = "UPDATING SHEET..."

        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = requireActivity().getSharedPreferences("CycleCountPrefs", Context.MODE_PRIVATE)
            val countedSet = prefs.getStringSet("CountedItems_$zoneName", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            val itemsArray = JSONArray()
            for (item in countedItems) {
                val obj = JSONObject().apply {
                    put("name", item.name)
                    put("countedQty", item.countedQty)
                }
                itemsArray.put(obj)
            }

            val currentDate = SimpleDateFormat("MM/dd/yy", Locale.US).format(Date())

            // 🔥 ADDED 'date' (Cell I5) AND 'zone' (Cell I9) TO PAYLOAD
            val requestBody = JSONObject().apply {
                put("techName", "Warehouse Team")
                put("date", currentDate)     // Destined for I5
                put("zone", zoneName)        // Destined for I9
                put("items", itemsArray)
            }

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val body = RequestBody.create(jsonMediaType, requestBody.toString())

            val request = Request.Builder()
                .url("https://integrity-backend-cr02.onrender.com/cycle_count_update")
                .addHeader("Authorization", "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7")
                .post(body)
                .build()

            try {
                val response = ApiClient.client.newCall(request).execute()
                if (response.isSuccessful) {
                    for (item in countedItems) {
                        countedSet.add(item.name)
                    }

                    val isComplete = masterZoneItems.size <= countedItems.size
                    val statusText = if (isComplete) "Complete" else "Incomplete"

                    prefs.edit()
                        .putStringSet("CountedItems_$zoneName", countedSet)
                        .putString("LAST_DATE_$zoneName", currentDate)
                        .putString("LAST_STATUS_$zoneName", statusText)
                        .apply()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Successfully updated ${countedItems.size} items!", Toast.LENGTH_LONG).show()
                        fetchInventoryForZone()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Backend Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
                response.close()
            } catch (e: Exception) {
                Log.e("CYCLE_COUNT", "Failed to update", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Network Error", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    pb?.visibility = View.GONE
                    btnSubmit?.isEnabled = true
                    btnSubmit?.text = "SUBMIT ZONE COUNT"
                }
            }
        }
    }
}