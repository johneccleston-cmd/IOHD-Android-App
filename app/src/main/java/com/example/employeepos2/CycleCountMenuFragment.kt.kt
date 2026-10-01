package com.example.employeepos2 // CHANGE TO YOUR PACKAGE

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

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
        checkAndApplyMonthlyResets()
        updateZoneStatuses()
        highlightActiveFridaySchedule()
    }

    private fun setupZoneCard(card: MaterialCardView?, zoneName: String) {
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

    private fun checkAndApplyMonthlyResets() {
        val prefs = requireActivity().getSharedPreferences("CycleCountPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val today = Calendar.getInstance()

        for (zoneNum in 1..4) {
            val zoneName = "Zone $zoneNum"
            val targetFriday = getNthFridayOfCurrentMonth(zoneNum)
            val lastResetMillis = prefs.getLong("LAST_RESET_FRIDAY_$zoneName", 0L)

            if (today.timeInMillis >= targetFriday.timeInMillis && lastResetMillis < targetFriday.timeInMillis) {
                editor.putString("LAST_STATUS_$zoneName", "Incomplete")
                editor.remove("CountedItems_$zoneName")
                editor.putLong("LAST_RESET_FRIDAY_$zoneName", targetFriday.timeInMillis)
            }
        }
        editor.apply()
    }

    private fun getNthFridayOfCurrentMonth(n: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)

        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        cal.add(Calendar.DAY_OF_MONTH, (n - 1) * 7)

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal
    }

    private fun highlightActiveFridaySchedule() {
        val activeZone = getCurrentActiveFridayZone()

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
                    cornerRadius = 24f
                }
                layout?.background = activeBg
                tvTitle?.setTextColor(Color.WHITE)
                tvSub?.setTextColor(Color.WHITE)
            } else {
                layout?.background = null
                tvTitle?.setTextColor(Color.parseColor("#111111"))
                tvSub?.setTextColor(Color.parseColor("#777777"))
            }
        }
    }

    // 🔥 HIGHLIGHTS CURRENT FRIDAY STARTING ON THE MONDAY BEFORE
    private fun getCurrentActiveFridayZone(): Int {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val daysToFriday = when (dayOfWeek) {
            Calendar.MONDAY -> 4
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 1
            Calendar.FRIDAY -> 0
            Calendar.SATURDAY -> -1
            Calendar.SUNDAY -> -2
            else -> 0
        }

        val targetFriday = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, daysToFriday)
        }

        val fridayDayOfMonth = targetFriday.get(Calendar.DAY_OF_MONTH)
        val zoneNumber = ((fridayDayOfMonth - 1) / 7) + 1

        return zoneNumber.coerceAtMost(4)
    }

    private fun updateZoneStatuses() {
        val prefs = requireActivity().getSharedPreferences("CycleCountPrefs", Context.MODE_PRIVATE)
        val zones = listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4")

        zones.forEachIndexed { index, zone ->
            val zoneNum = index + 1
            val date = prefs.getString("LAST_DATE_$zone", null)
            val status = prefs.getString("LAST_STATUS_$zone", null)

            val tvDateId = resources.getIdentifier("tvDateZone$zoneNum", "id", requireContext().packageName)
            val tvStatusId = resources.getIdentifier("tvStatusZone$zoneNum", "id", requireContext().packageName)

            val tvDate = view?.findViewById<TextView>(tvDateId)
            val tvStatus = view?.findViewById<TextView>(tvStatusId)

            if (date != null) {
                tvDate?.text = date
                tvDate?.setTextColor(Color.parseColor("#111111"))
            } else {
                tvDate?.text = "Never"
                tvDate?.setTextColor(Color.parseColor("#CC0007"))
            }

            if (status == "Complete") {
                tvStatus?.text = "Complete"
                tvStatus?.setTextColor(Color.parseColor("#2E7D32")) // Green
                tvStatus?.visibility = View.VISIBLE
            } else {
                tvStatus?.text = "Incomplete"
                tvStatus?.setTextColor(Color.parseColor("#CC0007")) // Red
                tvStatus?.visibility = View.VISIBLE
            }
        }
    }
}