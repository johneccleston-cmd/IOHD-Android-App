package com.example.employeepos2

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

// ─── Data Model ────────────────────────────────────────────────────────────────

data class InventoryItem(
    val name: String,
    val category: String,
    val qty: Int
) {
    val stockStatus: StockStatus get() = when {
        qty <= 0    -> StockStatus.OUT
        qty <= 5    -> StockStatus.LOW
        else        -> StockStatus.OK
    }
}

enum class StockStatus(val labelColor: Int, val label: String) {
    OUT(Color.parseColor("#CC0007"), "Out of stock"),
    LOW(Color.parseColor("#E07800"), "Low stock"),
    OK (Color.parseColor("#1A7A4A"), "In stock")
}

// ─── Fragment ──────────────────────────────────────────────────────────────────

class StockLevelsFragment : Fragment(R.layout.fragment_stock_levels) {

    companion object {
        private const val BASE_URL = "https://integrity-backend-cr02.onrender.com"
        private const val AUTH_TOKEN = "Bearer 3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"
        private const val RED_PRIMARY = "#CC0007"
        private const val CHIP_SELECTED_BG = "#CC0007"
        private const val CHIP_UNSELECTED_BG = "#F2F2F2"
        private const val CHIP_CORNER_DP = 20f
    }

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvStockItems: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView

    private var fullInventoryList = listOf<InventoryItem>()
    private var selectedCategory = "All"
    private var categoryAdapter: CategoryAdapter? = null

    private val client = OkHttpClient()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvCategories = view.findViewById(R.id.rvCategories)
        rvStockItems = view.findViewById(R.id.rvStockItems)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        rvCategories.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        rvStockItems.layoutManager = LinearLayoutManager(requireContext())

        fetchStockLevels()
    }

    // ─── Network ───────────────────────────────────────────────────────────────

    private fun fetchStockLevels() {
        setLoadingState(true)

        val request = Request.Builder()
            .url("$BASE_URL/get_full_inventory")
            .addHeader("Authorization", AUTH_TOKEN)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    setLoadingState(false)
                    showEmptyState("Couldn't load inventory.\nCheck your connection and try again.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                try {
                    val parsed = parseInventory(body)
                    val categories = buildCategoryList(parsed)

                    activity?.runOnUiThread {
                        fullInventoryList = parsed
                        setLoadingState(false)
                        setupCategorySlider(categories)
                        updateInventoryList("All")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        setLoadingState(false)
                        showEmptyState("Failed to parse inventory data.")
                    }
                }
            }
        })
    }

    private fun parseInventory(json: String): List<InventoryItem> {
        val array = JSONArray(json)
        val list = mutableListOf<InventoryItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            // Regex to strip absolutely everything except normal letters and numbers
            val parsedCategory = obj.optString("category", "Uncategorized").replace(Regex("[^A-Za-z0-9 ]"), "").trim()
            val parsedName = obj.optString("name", "Unknown").trim()

            // Skip if it even contains the word Category
            if (parsedCategory.contains("Category", ignoreCase = true) ||
                parsedName.equals("Name", ignoreCase = true)) {
                continue
            }

            list.add(InventoryItem(
                name     = parsedName,
                category = parsedCategory,
                qty      = obj.optInt("qty", 0)
            ))
        }

        return list
    }

    private fun buildCategoryList(items: List<InventoryItem>): List<String> {
        val cleanCategories = items.map { it.category }
            .filter { !it.equals("Category", ignoreCase = true) } // 🔥 Deletes the "Category" button
            .filter { !it.equals("Uncategorized", ignoreCase = true) } // (Just in case it's using the fallback)
            .distinct()
            .sorted()

        return mutableListOf("All") + cleanCategories
    }

    // ─── UI Updates ────────────────────────────────────────────────────────────

    private fun setLoadingState(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        rvStockItems.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(message: String) {
        tvEmptyState.text = message
        tvEmptyState.visibility = View.VISIBLE
        rvStockItems.visibility = View.GONE
    }

    private fun hideEmptyState() {
        tvEmptyState.visibility = View.GONE
        rvStockItems.visibility = View.VISIBLE
    }

    private fun setupCategorySlider(categories: List<String>) {
        categoryAdapter = CategoryAdapter(categories, selectedCategory) { clicked ->
            selectedCategory = clicked
            categoryAdapter?.updateSelection(clicked)
            updateInventoryList(clicked)
        }
        rvCategories.adapter = categoryAdapter
    }

    private fun updateInventoryList(category: String) {
        val filtered = if (category == "All") fullInventoryList
        else fullInventoryList.filter { it.category == category }

        if (filtered.isEmpty()) {
            showEmptyState("No items in '$category'")
        } else {
            hideEmptyState()
            rvStockItems.adapter = StockAdapter(filtered)
        }
    }

    // ─── Category Chip Adapter ─────────────────────────────────────────────────

    inner class CategoryAdapter(
        private val categories: List<String>,
        private var selected: String,
        private val onCategoryClick: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        inner class VH(val chip: TextView) : RecyclerView.ViewHolder(chip)

        fun updateSelection(category: String) {
            val old = categories.indexOf(selected)
            val new = categories.indexOf(category)
            selected = category
            if (old >= 0) notifyItemChanged(old)
            if (new >= 0) notifyItemChanged(new)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val chip = TextView(parent.context).apply {
                textSize = 14f
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                gravity = Gravity.CENTER
                val lp = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
                layoutParams = lp
            }
            return VH(chip)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val category = categories[position]
            holder.chip.text = category

            val isSelected = category == selected
            val bgColor = if (isSelected) CHIP_SELECTED_BG else CHIP_UNSELECTED_BG
            val textColor = if (isSelected) Color.WHITE else Color.parseColor("#333333")

            holder.chip.background = roundedBackground(
                Color.parseColor(bgColor), CHIP_CORNER_DP
            )
            holder.chip.setTextColor(textColor)
            holder.chip.setOnClickListener { onCategoryClick(category) }
        }

        override fun getItemCount() = categories.size
    }

    // ─── Stock Item Adapter ────────────────────────────────────────────────────

    inner class StockAdapter(
        private val items: List<InventoryItem>
    ) : RecyclerView.Adapter<StockAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView     = view.findViewWithTag("name")
            val tvCategory: TextView = view.findViewWithTag("category")
            val tvQtyBadge: TextView = view.findViewWithTag("qty")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val card = buildStockCard(ctx)
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val status = item.stockStatus

            holder.tvName.text = item.name
            holder.tvCategory.text = item.category
            holder.tvQtyBadge.text = if (status == StockStatus.OUT) "Out" else "${item.qty}"
            holder.tvQtyBadge.background = roundedBackground(
                lighten(status.labelColor, 0.88f), 12f
            )
            holder.tvQtyBadge.setTextColor(status.labelColor)

            // Subtle left-border accent for low/out items
            val borderColor = if (status == StockStatus.OK) Color.TRANSPARENT else status.labelColor
            (holder.itemView.background as? GradientDrawable)?.setStroke(dpToPx(3), borderColor)
        }

        override fun getItemCount() = items.size
    }

    // ─── View Builders ─────────────────────────────────────────────────────────

    private fun buildStockCard(ctx: Context): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.WHITE, 12f)
            elevation = dpToPx(1).toFloat()
            val lp = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5))
            layoutParams = lp
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        }

        // Left: name + category stacked
        val textColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvName = TextView(ctx).apply {
            tag = "name"
            textSize = 16f
            setTextColor(Color.parseColor("#111111"))
            // font weight via typeface if needed
        }

        val tvCategory = TextView(ctx).apply {
            tag = "category"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dpToPx(2), 0, 0)
        }

        textColumn.addView(tvName)
        textColumn.addView(tvCategory)

        // Right: qty badge
        val tvQty = TextView(ctx).apply {
            tag = "qty"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            minWidth = dpToPx(48)
        }

        card.addView(textColumn)
        card.addView(tvQty)
        return card
    }

    // ─── Utilities ─────────────────────────────────────────────────────────────

    private fun roundedBackground(color: Int, cornerDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = cornerDp * resources.displayMetrics.density
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /** Blend a color toward white by `factor` (0=original, 1=white). */
    private fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }
}