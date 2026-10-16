package com.example.employeepos2 // CHANGE TO YOUR PACKAGE

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CycleCountMenuFragment : Fragment(R.layout.fragment_cycle_count_menu) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupZoneCard(view.findViewById(R.id.cardZone1), "Zone 1")
        setupZoneCard(view.findViewById(R.id.cardZone2), "Zone 2")
        setupZoneCard(view.findViewById(R.id.cardZone3), "Zone 3")
        setupZoneCard(view.findViewById(R.id.cardZone4), "Zone 4")
    }

    override fun onResume() {
        super.onResume()
        updateZoneStatuses()
        highlightActiveWednesdaySchedule()
    }

    private fun setupZoneCard(card: View?, zoneName: String) {
        card?.setOnClickListener {
            val fragment = CycleCountListFragment().apply {
                arguments = Bundle().apply { putString("ZONE_NAME", zoneName) }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun highlightActiveWednesdaySchedule() {
        val activeZone = getCurrentActiveWednesdayZone()

        for (i in 1..4) {
            val llId = resources.getIdentifier("llSchedule$i", "id", requireContext().packageName)
            val titleId = resources.getIdentifier("tvScheduleTitle$i", "id", requireContext().packageName)
            val subId = resources.getIdentifier("tvScheduleSub$i", "id", requireContext().packageName)

            val layout = view?.findViewById<LinearLayout>(llId)
            val tvTitle = view?.findViewById<TextView>(titleId)
            val tvSub = view?.findViewById<TextView>(subId)

            if (i == activeZone) {
                val activeBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC0007"))
                    cornerRadius = 36f
                }
                layout?.background = activeBg
                tvTitle?.setTextColor(Color.WHITE)
                tvSub?.setTextColor(Color.WHITE)
            } else {
                layout?.background = null
                tvTitle?.setTextColor(Color.parseColor("#1A1C1E"))
                tvSub?.setTextColor(Color.parseColor("#6B7280"))
            }
        }
    }

    // 🔥 HIGHLIGHTS THE ZONE FOR THE WEDNESDAY OF THE CURRENT (MON-SUN) WEEK
    private fun getCurrentActiveWednesdayZone(): Int {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val daysToWednesday = when (dayOfWeek) {
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 0
            Calendar.THURSDAY -> -1
            Calendar.FRIDAY -> -2
            Calendar.SATURDAY -> -3
            Calendar.SUNDAY -> -4
            else -> 0
        }

        val targetWednesday = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, daysToWednesday)
        }

        val wednesdayDayOfMonth = targetWednesday.get(Calendar.DAY_OF_MONTH)
        val zoneNumber = ((wednesdayDayOfMonth - 1) / 7) + 1

        return zoneNumber.coerceAtMost(4)
    }

    // Zone status comes from the backend so every device shows the same progress
    private fun updateZoneStatuses() {
        viewLifecycleOwner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { CycleCountApi.fetchStatus() }
            if (!isAdded) return@launch
            if (status == null) {
                Toast.makeText(requireContext(), "Couldn't load shared cycle count status", Toast.LENGTH_SHORT).show()
                return@launch
            }

            for (zoneNum in 1..4) {
                val zone = CycleCountApi.zoneObject(status, "Zone $zoneNum")
                val date = zone?.optString("lastDate", "")?.takeIf { it.isNotEmpty() && it != "null" }
                val isComplete = zone?.optString("status") == "Complete"
                val counted = zone?.optJSONArray("countedItems")?.length() ?: 0

                val tvDateId = resources.getIdentifier("tvDateZone$zoneNum", "id", requireContext().packageName)
                val tvStatusId = resources.getIdentifier("tvStatusZone$zoneNum", "id", requireContext().packageName)
                val tvDate = view?.findViewById<TextView>(tvDateId)
                val tvStatus = view?.findViewById<TextView>(tvStatusId)

                if (date != null) {
                    tvDate?.text = date
                    tvDate?.setTextColor(Color.parseColor("#1A1C1E"))
                } else {
                    tvDate?.text = "Never"
                    tvDate?.setTextColor(Color.parseColor("#CC0007"))
                }

                if (isComplete) {
                    tvStatus?.text = "Complete"
                    tvStatus?.setTextColor(Color.parseColor("#15803D")) // Green
                } else {
                    tvStatus?.text = if (counted > 0) "Incomplete ($counted counted)" else "Incomplete"
                    tvStatus?.setTextColor(Color.parseColor("#CC0007")) // Red
                }
                tvStatus?.visibility = View.VISIBLE
            }
        }
    }
}
