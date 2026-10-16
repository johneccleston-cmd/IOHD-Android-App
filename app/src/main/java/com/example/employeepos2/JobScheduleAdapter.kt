package com.example.employeepos2

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class ScheduledJob(
    val internalSfId: Int,
    val number: String,
    val customer: String,
    val contact: String,
    val status: String,
    val description: String,
    val location: String,
    val techs: String,
    val total: String,
    val notes: String,
    val poNumber: String,
    val isEstimate: Boolean // 🔥 ADDED: This tells us if it's an estimate or a job!
)

class JobScheduleAdapter(
    private val onItemClick: (ScheduledJob) -> Unit
) : ListAdapter<ScheduledJob, JobScheduleAdapter.JobViewHolder>(JobDiffCallback()) {

    class JobViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJobType: TextView = view.findViewById(R.id.tvJobType)
        val tvJobNumber: TextView = view.findViewById(R.id.tvJobNumber)
        val tvCustomerName: TextView = view.findViewById(R.id.tvCustomerName)
        val tvStatusPill: TextView = view.findViewById(R.id.tvStatusPill)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val rowLocation: View = view.findViewById(R.id.rowLocation)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvTechs: TextView = view.findViewById(R.id.tvTechs)
        val ivTechs: View = view.findViewById(R.id.ivTechs)
        val btnDirections: View = view.findViewById(R.id.btnDirections)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = getItem(position)

        // Estimates are blue, jobs red (brand).
        holder.tvJobType.text = if (job.isEstimate) "Estimate" else "Job"
        holder.tvJobType.setTextColor((if (job.isEstimate) "#1D4ED8" else "#CC0007").toColorInt())
        holder.tvJobNumber.text = "#${job.number}"

        holder.tvCustomerName.text = job.customer

        val hasAddress = StatusStyle.hasAddress(job.location)
        holder.rowLocation.visibility = if (hasAddress) View.VISIBLE else View.GONE
        holder.tvLocation.text = job.location.trim()
        holder.btnDirections.visibility = if (hasAddress) View.VISIBLE else View.GONE
        holder.btnDirections.setOnClickListener { StatusStyle.openMaps(it.context, job.location.trim()) }

        val desc = job.description.trim()
        val hasDesc = desc.isNotEmpty() && !desc.equals("No description provided.", ignoreCase = true)
        holder.tvDescription.visibility = if (hasDesc) View.VISIBLE else View.GONE
        holder.tvDescription.text = desc

        val techs = job.techs.trim()
        val hasTechs = techs.isNotEmpty() && !techs.equals("Unassigned", ignoreCase = true)
        holder.tvTechs.text = if (hasTechs) techs else "Unassigned"
        holder.ivTechs.alpha = if (hasTechs) 1f else 0.5f

        holder.tvStatusPill.text = job.status
        StatusStyle.applyPill(holder.tvStatusPill, job.status)

        holder.itemView.setOnClickListener { onItemClick(job) }
    }

    class JobDiffCallback : DiffUtil.ItemCallback<ScheduledJob>() {
        override fun areItemsTheSame(oldItem: ScheduledJob, newItem: ScheduledJob): Boolean {
            return oldItem.internalSfId == newItem.internalSfId
        }

        override fun areContentsTheSame(oldItem: ScheduledJob, newItem: ScheduledJob): Boolean {
            return oldItem == newItem
        }
    }
}