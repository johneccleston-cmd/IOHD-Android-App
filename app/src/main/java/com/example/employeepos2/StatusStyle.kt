package com.example.employeepos2

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

/** Shared look for job status colours and the "open in maps" action (home schedule card + job details). */
object StatusStyle {

    @ColorInt
    fun accent(status: String): Int = when (status.trim().uppercase()) {
        "SCHEDULED", "SITE CHECK", "CALLBACK", "WARRANTY WORK" -> "#009E18"
        "APPOINTMENT" -> "#00B51B"
        "DELAYED", "NEED TO WARRANTY", "PARTIALLY COMPLETE" -> "#FFAE00"
        "COMPLETE" -> "#9C9C9C"
        "MANAGE PROJECT", "ESTIMATE REQUESTED", "ESTIMATE PROVIDED" -> "#0051FF"
        "NEED TO ORDER" -> "#00B3FF"
        "NEED DEPOSIT" -> "#A80000"
        "GOOGLE REVIEW SENT" -> "#7C3AED"
        else -> "#9C9C9C"
    }.toColorInt()

    /** White text on the dark status colours, near-black on the light ones (amber, light blue, grey). */
    @ColorInt
    fun textOn(@ColorInt bg: Int): Int =
        if (androidx.core.graphics.ColorUtils.calculateLuminance(bg) > 0.38) "#1A1C1E".toColorInt() else Color.WHITE

    /** Applies the solid status pill look to a TextView that uses bg_status_badge. */
    fun applyPill(view: android.widget.TextView, status: String) {
        val accent = accent(status)
        view.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        view.setTextColor(textOn(accent))
    }

    @ColorInt
    fun tint(@ColorInt color: Int, alpha: Int = 34): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** True when the text is a real address (the backend sends "No Address Provided" when it has none). */
    fun hasAddress(location: String): Boolean {
        val l = location.trim()
        return l.isNotEmpty() && !l.equals("No Address Provided", ignoreCase = true)
    }

    fun openMaps(context: Context, location: String) {
        val uri = "geo:0,0?q=${android.net.Uri.encode(location)}".toUri()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(context, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }
}
