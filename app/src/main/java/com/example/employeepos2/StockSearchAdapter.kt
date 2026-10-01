package com.example.employeepos2

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// THE BLUEPRINT
data class StockItem(val name: String, val category: String, val qty: Int)

class StockSearchAdapter(private var items: List<StockItem>) :
    RecyclerView.Adapter<StockSearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Pointing to our new Custom IDs
        val tvName: TextView = view.findViewById(R.id.tvPartName)
        val tvQty: TextView = view.findViewById(R.id.tvPartQty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 🔥 Now inflating OUR custom layout instead of the system one
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_part, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 1. Part Name - Hardcoded to Black
        holder.tvName.text = item.name
        holder.tvName.setTextColor(Color.BLACK)

        // 2. Stock Logic - "Appropriate Colors"
        holder.tvQty.text = "In Stock: ${item.qty}"

        when {
            item.qty <= 0 -> {
                holder.tvQty.text = "OUT OF STOCK"
                holder.tvQty.setTextColor(Color.parseColor("#D32F2F")) // Integrity Red
            }
            item.qty <= 5 -> {
                holder.tvQty.setTextColor(Color.parseColor("#FF8F00")) // Warning Orange
            }
            else -> {
                holder.tvQty.setTextColor(Color.parseColor("#2E7D32")) // Success Green
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<StockItem>) {
        items = newList
        notifyDataSetChanged()
    }
}