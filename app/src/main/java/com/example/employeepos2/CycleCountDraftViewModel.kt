package com.example.employeepos2

import androidx.lifecycle.ViewModel

/**
 * Holds counts entered but not yet submitted, so they survive screen rotation.
 * Scoped to the activity; drafts are per-device work in progress (submitted counts live on the backend).
 */
class CycleCountDraftViewModel : ViewModel() {
    // zone -> item name -> (draftQty, countedQty)
    private val drafts = mutableMapOf<String, MutableMap<String, Pair<Int?, Int?>>>()

    fun save(zone: String, items: List<ZoneCountItem>) {
        val zoneDrafts = drafts.getOrPut(zone) { mutableMapOf() }
        zoneDrafts.clear()
        items.forEach { if (it.draftQty != null || it.countedQty != null) zoneDrafts[it.name] = it.draftQty to it.countedQty }
    }

    fun restore(zone: String, items: List<ZoneCountItem>) {
        val zoneDrafts = drafts[zone] ?: return
        items.forEach { item ->
            zoneDrafts[item.name]?.let { (draft, counted) ->
                item.draftQty = draft
                item.countedQty = counted
            }
        }
    }

    fun clear(zone: String, names: Collection<String>) {
        drafts[zone]?.keys?.removeAll(names.toSet())
    }
}
