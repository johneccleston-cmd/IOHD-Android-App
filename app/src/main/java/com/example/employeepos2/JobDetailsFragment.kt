package com.example.employeepos2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

@SuppressLint("SetTextI18n")
class JobDetailsFragment : Fragment(R.layout.fragment_job_details) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            requireContext(),
            "UserPrefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔥 Added the IS_ESTIMATE flag here (defaults to false if not provided)
        val isEstimate   = arguments?.getBoolean("IS_ESTIMATE", false) ?: false
        val javaNum      = arguments?.getString("JOB_NUMBER")       ?: "---"

        val customerName = arguments?.getString("CUSTOMER_NAME")    ?: "Unknown Customer"
        val contactName  = arguments?.getString("CONTACT_NAME")     ?: "No Contact Info"
        val status       = arguments?.getString("JOB_STATUS")       ?: "SCHEDULED"
        val description  = arguments?.getString("JOB_DESCRIPTION")  ?: ""
        val location     = arguments?.getString("JOB_LOCATION")     ?: ""
        val techs        = arguments?.getString("ASSIGNED_TECHS")   ?: ""
        val jobTotal     = arguments?.getString("JOB_TOTAL")        ?: "0.00"
        val poNumber     = arguments?.getString("PO_NUMBER")        ?: ""

        val notes = arguments?.getString("JOB_NOTES")
        val currentTechName = prefs.getString("NAME", "Unknown Tech") ?: "Unknown Tech"

        // 🔥 Dynamically switch the text based on the flag
        val tvJobNumber = view.findViewById<TextView>(R.id.tvDetailsJobNumber)
        if (isEstimate) {
            tvJobNumber.text = "Estimate #$javaNum"
        } else {
            tvJobNumber.text = "Job #$javaNum"
        }

        view.findViewById<TextView>(R.id.tvDetailsCustomer).text   = customerName
        view.findViewById<TextView>(R.id.tvDetailsContact).text    = contactName
        view.findViewById<TextView>(R.id.tvDetailsDescription).text = description
        view.findViewById<TextView>(R.id.tvDetailsTechs).text      = techs

        val poSection  = view.findViewById<LinearLayout>(R.id.llDetailsPoSection)
        val tvPoNumber = view.findViewById<TextView>(R.id.tvDetailsPONumber)

        if (poSection != null && tvPoNumber != null) {
            if (poNumber.isNotBlank()) {
                poSection.visibility = View.VISIBLE
                tvPoNumber.text = poNumber
            } else {
                poSection.visibility = View.GONE
            }
        }

        val notesCard = view.findViewById<MaterialCardView>(R.id.cardJobNotes)
        val tvNotes = view.findViewById<TextView>(R.id.tvJobNotes)

        if (!notes.isNullOrBlank()) {
            notesCard.visibility = View.VISIBLE
            tvNotes.text = notes
        } else {
            notesCard.visibility = View.GONE
        }

        val tvStatusBadge = view.findViewById<TextView>(R.id.tvDetailsStatusBadge)
        tvStatusBadge.text = status.uppercase()
        val colorHex = when (status.uppercase()) {
            "SCHEDULED", "SITE CHECK", "CALLBACK", "WARRANTY WORK" -> "#009E18"
            "APPOINTMENT"                                           -> "#00B51B"
            "DELAYED", "NEED TO WARRANTY", "PARTIALLY COMPLETE"    -> "#FFAE00"
            "COMPLETE"                                              -> "#9C9C9C"
            "MANAGE PROJECT"                                        -> "#0051FF"
            "NEED TO ORDER"                                         -> "#00B3FF"
            "NEED DEPOSIT"                                          -> "#A80000"
            else                                                    -> "#9C9C9C"
        }
        tvStatusBadge.backgroundTintList = ColorStateList.valueOf(colorHex.toColorInt())

        val tvLocation = view.findViewById<TextView>(R.id.tvDetailsLocation)
        tvLocation.text = location
        if (location.isNotEmpty()) {
            tvLocation.setOnClickListener {
                val mapUri = "geo:0,0?q=${android.net.Uri.encode(location)}".toUri()
                try { startActivity(Intent(Intent.ACTION_VIEW, mapUri)) }
                catch (e: Exception) { Toast.makeText(requireContext(), "No maps app found", Toast.LENGTH_SHORT).show() }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnStartSiteCheck).setOnClickListener {
            val intent = Intent(requireContext(), SiteCheckMenuActivity::class.java).apply {
                putExtra("SF_JOB_NUM", javaNum)
                putExtra("JOB_NUMBER", javaNum)
                putExtra("SF_CUSTOMER_NAME", customerName)
                putExtra("TECH_NAME", currentTechName)
                putExtra("LOCATION", location)
            }
            startActivity(intent)
        }

        view.findViewById<MaterialButton>(R.id.btnOpenServiceFusion).setOnClickListener {
            val pm = requireContext().packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.servicefusion.react")
                ?: pm.getLaunchIntentForPackage("com.servicefusion.workerapp")
                ?: pm.getLaunchIntentForPackage("com.servicefusion.fieldworker")
            if (launchIntent != null) startActivity(launchIntent)
            else Toast.makeText(requireContext(), "Service Fusion app not found.", Toast.LENGTH_SHORT).show()
        }

        val btnPayment = view.findViewById<MaterialButton>(R.id.btnCollectPayment)
        val canCollect = prefs.getBoolean("CAN_COLLECT", false)

        // Only show payment button if it's a job (estimates usually require signatures, not immediate checkout)
        // If you DO want to collect payments on estimates, just remove the `&& !isEstimate` part!
        btnPayment.visibility = if (canCollect && !isEstimate) View.VISIBLE else View.GONE

        if (canCollect) {
            btnPayment.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("PREFILL_JOB_NUM",       javaNum)
                    putString("PREFILL_CUSTOMER_NAME", customerName)
                    putString("SF_TOTAL",              jobTotal)
                }
                val targetFragment = JobsFragmentV2().apply { arguments = bundle }
                (requireActivity() as MainActivity).loadFragment(targetFragment, true)
            }
        }
    }
}