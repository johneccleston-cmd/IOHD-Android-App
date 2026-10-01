package com.example.employeepos2

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class InventoryAdapterV2(
    private val onItemChanged: () -> Unit
) : ListAdapter<InventoryJob, InventoryAdapterV2.JobViewHolder>(InventoryDiffCallback()) {

    class JobViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvJobHeader)
        val tvCity: TextView = view.findViewById(R.id.tvJobCity)
        val tvPONumber: TextView = view.findViewById(R.id.tvJobPONumber)
        val tvDescription: TextView = view.findViewById(R.id.tvJobDescription)
        val tvNotes: TextView = view.findViewById(R.id.tvJobNotes)
        val llNotesSection: LinearLayout = view.findViewById(R.id.llNotesSection)
        val container: LinearLayout = view.findViewById(R.id.llMaterialsList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        return JobViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_inventory_job, parent, false)
        )
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = getItem(position)

        val containerParams = holder.container.layoutParams
        if (containerParams != null) {
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            holder.container.layoutParams = containerParams
        }

        holder.tvHeader.text = "Job #${job.jobNumber} - ${job.customer}"

        holder.tvCity.visibility = View.VISIBLE
        holder.tvCity.text = job.city

        if (job.poNumber.isNotBlank()) {
            holder.tvPONumber.visibility = View.VISIBLE
            holder.tvPONumber.text = "PO: ${job.poNumber}"
        } else {
            holder.tvPONumber.visibility = View.GONE
        }

        holder.tvDescription.text = job.description

        if (!job.notes.isNullOrBlank()) {
            holder.llNotesSection.visibility = View.VISIBLE
            holder.tvNotes.text = job.notes
        } else {
            holder.llNotesSection.visibility = View.GONE
        }

        val currentChildCount = holder.container.childCount
        val itemsNeeded = job.items.size

        if (currentChildCount > itemsNeeded) {
            holder.container.removeViews(itemsNeeded, currentChildCount - itemsNeeded)
        }

        job.items.forEachIndexed { index, part ->
            val partView = if (index < currentChildCount) {
                holder.container.getChildAt(index)
            } else {
                val v = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_inventory_part, holder.container, false)
                holder.container.addView(v)
                v
            }

            val rowParams = partView.layoutParams
            if (rowParams != null) {
                rowParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                partView.layoutParams = rowParams
            }

            bindPartView(partView, part)
        }
    }

    private fun bindPartView(view: View, part: InventoryPart) {
        val cb          = view.findViewById<CheckBox>(R.id.cbPicked)
        val tvName      = view.findViewById<TextView>(R.id.tvPartName)
        val etQty       = view.findViewById<EditText>(R.id.etPartQty)
        val btnMinus    = view.findViewById<View>(R.id.btnMinusPartQty)
        val btnPlus     = view.findViewById<View>(R.id.btnPlusPartQty)
        val tvStock     = view.findViewById<TextView>(R.id.tvStockLevel)
        val tvPartDesc  = view.findViewById<TextView>(R.id.tvPartDescription)
        val context     = view.context

        tvName.text = part.name

        if (!part.description.isNullOrBlank()) {
            tvPartDesc.visibility = View.VISIBLE
            tvPartDesc.text = part.description
        } else {
            tvPartDesc.visibility = View.GONE
        }

        if (part.qtyInStock <= 0) {
            tvStock.text = "Out of Stock"
            // Defaulting to a safe red color if R.color.stock_out doesn't exist
            tvStock.setTextColor(android.graphics.Color.parseColor("#CC0007"))
        } else {
            tvStock.text = "In Stock: ${part.qtyInStock}"
            // Defaulting to a safe green color if R.color.stock_available doesn't exist
            tvStock.setTextColor(android.graphics.Color.parseColor("#1A7A4A"))
        }

        cb.setOnCheckedChangeListener(null)
        cb.isChecked = part.isPicked
        cb.setOnCheckedChangeListener { _, isChecked ->
            part.isPicked = isChecked
            onItemChanged()
        }

        val oldWatcher = etQty.tag as? TextWatcher
        if (oldWatcher != null) {
            etQty.removeTextChangedListener(oldWatcher)
        }

        etQty.setText(String.format(Locale.US, "%d", part.qtyNeeded))

        btnPlus.setOnClickListener {
            part.qtyNeeded++
            etQty.setText(String.format(Locale.US, "%d", part.qtyNeeded))
            onItemChanged()
        }

        btnMinus.setOnClickListener {
            if (part.qtyNeeded > 1) {
                part.qtyNeeded--
                etQty.setText(String.format(Locale.US, "%d", part.qtyNeeded))
                onItemChanged()
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val parsedInt = s.toString().toIntOrNull() ?: 1
                if (parsedInt >= 1) {
                    part.qtyNeeded = parsedInt
                    onItemChanged()
                }
            }
        }

        etQty.addTextChangedListener(textWatcher)
        etQty.tag = textWatcher
    }

    class InventoryDiffCallback : DiffUtil.ItemCallback<InventoryJob>() {
        override fun areItemsTheSame(oldItem: InventoryJob, newItem: InventoryJob): Boolean {
            return oldItem.jobNumber == newItem.jobNumber
        }

        override fun areContentsTheSame(oldItem: InventoryJob, newItem: InventoryJob): Boolean {
            return oldItem == newItem
        }
    }
}