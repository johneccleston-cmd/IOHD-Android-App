package com.example.employeepos2

import android.content.res.ColorStateList
import android.graphics.Color
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
        val tvJobNumber: TextView = view.findViewById(R.id.tvJobNumber)
        val tvCustomerName: TextView = view.findViewById(R.id.tvCustomerName)
        val tvStatusPill: TextView = view.findViewById(R.id.tvStatusPill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = getItem(position)

        // 🔥 Dynamically switch the text on the list screen!
        if (job.isEstimate) {
            holder.tvJobNumber.text = "Estimate #${job.number}"
        } else {
            holder.tvJobNumber.text = "Job #${job.number}"
        }

        // 🔥 Fixed the data binding (mapped Customer to Customer)
        holder.tvCustomerName.text = job.customer

        // Note: If you want to show the location on the list screen,
        // you will need to add a tvLocation to your item_job.xml!

        holder.tvStatusPill.text = job.status

        val colorHex = when (job.status.uppercase()) {
            "SCHEDULED", "SITE CHECK", "CALLBACK", "WARRANTY WORK" -> "#009E18"
            "APPOINTMENT" -> "#00B51B"
            "DELAYED", "NEED TO WARRANTY", "PARTIALLY COMPLETE" -> "#FFAE00"
            "COMPLETE" -> "#9C9C9C"
            "MANAGE PROJECT" -> "#0051FF"
            "NEED TO ORDER" -> "#00B3FF"
            "NEED DEPOSIT" -> "#A80000"
            else -> "#9C9C9C"
        }

        holder.tvStatusPill.backgroundTintList = ColorStateList.valueOf(colorHex.toColorInt())

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