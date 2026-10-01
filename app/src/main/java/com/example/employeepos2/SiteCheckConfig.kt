package com.example.employeepos2.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Immutable configuration derived from the launching Intent.
 * Passed as a single Parcelable rather than reading individual extras
 * throughout the codebase, so all intent contract knowledge lives here.
 */
@Parcelize
data class SiteCheckConfig(
    val jobNum: String,
    val techName: String,
    val customerName: String,   // 🔥 ADDED
    val location: String,       // 🔥 ADDED
    val city: String,           // 🔥 ADDED
    val present: String,
    val lift: String,
    val replacing: String,
    val concreteProblem: String,
    val checkType: CheckType,
) : Parcelable {

    companion object {
        const val EXTRA_KEY = "SITE_CHECK_CONFIG"

        /** Build from raw intent extras — falls back to safe defaults. */
        fun fromExtras(
            jobNum: String?,
            techName: String?,
            customerName: String?,   // 🔥 ADDED
            location: String?,       // 🔥 ADDED
            city: String?,           // 🔥 ADDED
            present: String?,
            lift: String?,
            replacing: String?,
            concreteProblem: String?,
            checkTypeRaw: String?,
        ) = SiteCheckConfig(
            jobNum          = jobNum          ?: "",
            techName        = techName        ?: "",
            customerName    = customerName    ?: "N/A",  // 🔥 ADDED
            location        = location        ?: "N/A",  // 🔥 ADDED
            city            = city            ?: "N/A",  // 🔥 ADDED
            present         = present         ?: "N/A",
            lift            = lift            ?: "N/A",
            replacing       = replacing       ?: "N/A",
            concreteProblem = concreteProblem ?: "N/A",
            checkType       = CheckType.from(checkTypeRaw),
        )

    }

    /** Whether the operator section should be shown. */
    val showOperatorSection: Boolean
        get() = checkType == CheckType.SERVICE
                || replacing == "Operator Only"
                || replacing == "Door & Operator"

    /** Whether the component inspection (status check) section should be shown. */
    val showInspectionSection: Boolean
        get() = checkType == CheckType.SERVICE

    /** Whether the concrete section should be shown. */
    val showConcreteSection: Boolean
        get() = checkType != CheckType.SERVICE
                && concreteProblem != "No"
                && !(replacing == "Door Only" && concreteProblem != "Yes")

    /** Whether the existing-door info block should be shown. */
    val showExistingDoorInfo: Boolean
        get() = replacing != "Operator Only"
                && (checkType == CheckType.REPLACEMENT || checkType == CheckType.SERVICE)

    /** Whether the door-tag upload instruction is shown. */
    val showDoorTagInstruction: Boolean
        get() = checkType == CheckType.REPLACEMENT

    /** Whether the door notes / photo section is shown. */
    val showDoorNotesAndPhotos: Boolean
        get() = replacing != "Operator Only"

    /** Human-readable opening photo instruction based on check type. */
    val photoInstruction: String
        get() = if (checkType == CheckType.REPLACEMENT || checkType == CheckType.SERVICE)
            "* Upload clear photos of door from the inside & outside"
        else
            "* Upload clear photos of opening from the inside"
}

enum class CheckType {
    NEW_INSTALL, REPLACEMENT, SERVICE, UNKNOWN;

    companion object {
        fun from(raw: String?): CheckType = when (raw) {
            "REPLACEMENT" -> REPLACEMENT
            "SERVICE"     -> SERVICE
            "NEW_INSTALL" -> NEW_INSTALL
            else          -> UNKNOWN
        }
    }
}