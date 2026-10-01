package com.example.employeepos2 // CHANGE TO YOUR PACKAGE

import android.animation.ValueAnimator
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

data class ZoneCountItem(
    val name: String,
    val category: String,
    val systemQty: Int,
    val partNumber: String = "",
    var countedQty: Int? = null,
    var draftQty: Int? = null
)

class CycleCountAdapter(
    private val items: MutableList<ZoneCountItem>,
    private val onItemCountChanged: (() -> Unit)? = null // 🔥 CALLBACK FOR PROGRESS BAR UPDATES
) : RecyclerView.Adapter<CycleCountAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardItem: MaterialCardView = view.findViewById(R.id.cardItem)
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvSystemQty: TextView = view.findViewById(R.id.tvSystemQty)
        val tvVariance: TextView = view.findViewById(R.id.tvVariance)

        val etActualCount: TextInputEditText = view.findViewById(R.id.etActualCount)
        val btnMinus: MaterialButton = view.findViewById(R.id.btnMinus)
        val btnPlus: MaterialButton = view.findViewById(R.id.btnPlus)
        val btnConfirm: MaterialButton = view.findViewById(R.id.btnConfirm)

        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cycle_count, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvItemName.text = item.name
        holder.tvCategory.text = "Category: ${item.category}"
        holder.tvSystemQty.text = "QTY in Stock: ${item.systemQty}"

        fun refreshUI(updateText: Boolean = true) {
            holder.textWatcher?.let { holder.etActualCount.removeTextChangedListener(it) }

            val displayQty = item.draftQty ?: item.systemQty

            if (updateText) {
                holder.etActualCount.setText(displayQty.toString())
                holder.etActualCount.setSelection(holder.etActualCount.text?.length ?: 0)
            }

            if (item.countedQty != null) {
                // CONFIRMED STATE
                holder.etActualCount.setTextColor(Color.BLACK)
                holder.cardItem.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                holder.btnConfirm.visibility = View.GONE

                // CALCULATE & SHOW VARIANCE BADGE
                val diff = item.countedQty!! - item.systemQty
                if (diff != 0) {
                    holder.tvVariance.visibility = View.VISIBLE
                    holder.tvVariance.text = if (diff > 0) "Δ +$diff" else "Δ $diff"
                    holder.tvVariance.setBackgroundColor(if (diff < 0) Color.parseColor("#FEE2E2") else Color.parseColor("#FEF3C7"))
                    holder.tvVariance.setTextColor(if (diff < 0) Color.parseColor("#CC0007") else Color.parseColor("#D97706"))
                } else {
                    holder.tvVariance.visibility = View.GONE
                }
            } else {
                // DRAFT / UNCONFIRMED STATE
                holder.etActualCount.setTextColor(Color.parseColor("#999999"))
                holder.cardItem.setCardBackgroundColor(Color.WHITE)
                holder.btnConfirm.visibility = View.VISIBLE
                holder.tvVariance.visibility = View.GONE
            }

            holder.textWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val input = s.toString()
                    item.draftQty = input.toIntOrNull()

                    if (item.countedQty != null) {
                        item.countedQty = null
                        onItemCountChanged?.invoke() // 🔥 TRIGGER PROGRESS UPDATE
                        refreshUI(updateText = false)
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            holder.etActualCount.addTextChangedListener(holder.textWatcher)
        }

        refreshUI()

        holder.btnMinus.setOnClickListener {
            val current = item.draftQty ?: item.systemQty
            if (current > 0) {
                item.draftQty = current - 1
                if (item.countedQty != null) {
                    item.countedQty = null
                    onItemCountChanged?.invoke() // 🔥 TRIGGER PROGRESS UPDATE
                }
                refreshUI()
            }
        }

        holder.btnPlus.setOnClickListener {
            val current = item.draftQty ?: item.systemQty
            item.draftQty = current + 1
            if (item.countedQty != null) {
                item.countedQty = null
                onItemCountChanged?.invoke() // 🔥 TRIGGER PROGRESS UPDATE
            }
            refreshUI()
        }

        holder.btnConfirm.setOnClickListener {
            val qtyToSave = item.draftQty ?: item.systemQty
            item.draftQty = qtyToSave
            item.countedQty = qtyToSave

            animateColorChange(holder.cardItem, Color.WHITE, Color.parseColor("#C8E6C9"))
            refreshUI()
            onItemCountChanged?.invoke() // 🔥 TRIGGER PROGRESS UPDATE ON CONFIRM
        }

        holder.etActualCount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.etActualCount.selectAll()
            }
        }
    }

    override fun getItemCount() = items.size

    fun getCountedItems(): List<ZoneCountItem> {
        return items
    }

    fun getItemAt(position: Int): ZoneCountItem {
        return items[position]
    }

    fun removeItemAt(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            onItemCountChanged?.invoke() // 🔥 TRIGGER PROGRESS UPDATE ON REMOVE
        }
    }

    private fun animateColorChange(card: MaterialCardView, startColor: Int, endColor: Int) {
        val animator = ValueAnimator.ofArgb(startColor, endColor)
        animator.duration = 300
        animator.addUpdateListener { animation ->
            card.setCardBackgroundColor(animation.animatedValue as Int)
        }
        animator.start()
    }
}