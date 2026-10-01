package com.example.employeepos2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(private val jobs: List<String>) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJobNumber: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Using a standard Android simple list item for speed
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvJobNumber.text = "Job #${jobs[position]}"
        holder.tvJobNumber.setTextColor(android.graphics.Color.BLACK)
        holder.tvJobNumber.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    override fun getItemCount() = jobs.size
}