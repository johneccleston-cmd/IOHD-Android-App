package com.example.employeepos2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson

class SiteCheckMenuActivity : BaseActivity() {

    private var jobNumber: String = ""
    private var customerName: String = ""
    private var techName: String = ""
    private var location: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_check_menu)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        jobNumber = intent.getStringExtra("SF_JOB_NUM") ?: intent.getStringExtra("JOB_NUMBER") ?: ""
        customerName = intent.getStringExtra("SF_CUSTOMER_NAME") ?: ""
        techName = intent.getStringExtra("TECH_NAME") ?: ""
        location = intent.getStringExtra("LOCATION") ?: ""

        findViewById<FrameLayout>(R.id.btnMenuBack)?.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.cardNewConstruction)?.setOnClickListener {
            val intent = Intent(this, SiteCheckActivity::class.java).apply {
                putExtra("SF_JOB_NUM", jobNumber)
                putExtra("SF_CUSTOMER_NAME", customerName)
                putExtra("TECH_NAME", techName)
                putExtra("CHECK_TYPE", "NEW_INSTALL")
                putExtra("LOCATION", location)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.cardReplacement)?.setOnClickListener {
            val intent = Intent(this, ReplacementQuestionsActivity::class.java).apply {
                putExtra("SF_JOB_NUM", jobNumber)
                putExtra("SF_CUSTOMER_NAME", customerName)
                putExtra("TECH_NAME", techName)
                putExtra("CHECK_TYPE", "REPLACEMENT")
                putExtra("LOCATION", location)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.cardService)?.setOnClickListener {
            val intent = Intent(this, SiteCheckActivity::class.java).apply {
                putExtra("SF_JOB_NUM", jobNumber)
                putExtra("SF_CUSTOMER_NAME", customerName)
                putExtra("TECH_NAME", techName)
                putExtra("CHECK_TYPE", "SERVICE")
                putExtra("LOCATION", location)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        val llDraftsSection = findViewById<LinearLayout>(R.id.llDraftsSection)
        val llDraftsContainer = findViewById<LinearLayout>(R.id.llDraftsContainer)

        // Safely clear views
        llDraftsContainer?.removeAllViews()

        val prefs = getSharedPreferences("SiteCheckDrafts", Context.MODE_PRIVATE)
        val draftKeys = prefs.all.keys.filter { it.startsWith("draft_") }

        if (draftKeys.isNotEmpty()) {
            llDraftsSection?.visibility = View.VISIBLE

            // 🔥 Add a counter to keep track of the draft number dynamically
            var draftCounter = 1

            draftKeys.forEach { key ->
                val jsonString = prefs.getString(key, null) ?: return@forEach

                try {
                    val saveState = Gson().fromJson(jsonString, DraftSaveState::class.java)

                    // Only show drafts belonging to THIS specific job
                    if (saveState.jobNum != jobNumber) return@forEach

                    val card = layoutInflater.inflate(R.layout.item_draft_card, llDraftsContainer, false)

                    // Format the raw check type into a human-readable title
                    val displayType = when (saveState.checkType) {
                        "NEW_INSTALL" -> "New Construction"
                        "REPLACEMENT" -> "Replacement"
                        "SERVICE"       -> "Service"
                        else          -> saveState.checkType
                    }

                    // Update the UI card text with the dynamic number!
                    card.findViewById<TextView>(R.id.tvDraftJob).text = "Saved Draft #$draftCounter"
                    card.findViewById<TextView>(R.id.tvDraftType).text = displayType

                    draftCounter++ // Increase the count for the next valid draft

                    // When they click the card, send the specific Draft ID!
                    card.setOnClickListener {
                        val intent = Intent(this, SiteCheckActivity::class.java).apply {
                            putExtra("RESUME_DRAFT_ID", saveState.draftId)
                        }
                        startActivity(intent)
                    }

                    // 🔥 NEW CUSTOM DELETE POP-UP
                    card.findViewById<View>(R.id.btnDeleteDraft).setOnClickListener {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_draft, null)
                        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDelete)
                        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDelete)

                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()

                        // Make the default background invisible so the rounded corners work
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                        btnCancel.setOnClickListener {
                            dialog.dismiss()
                        }

                        btnConfirm.setOnClickListener {
                            dialog.dismiss()
                            prefs.edit().remove(key).apply()
                            onResume() // Redraw the list instantly!
                        }

                        dialog.show()
                    }

                    llDraftsContainer?.addView(card)

                } catch (e: Exception) {
                    // This catches corrupted test drafts and silently deletes them!
                    prefs.edit().remove(key).apply()
                }
            }

            // If the drafts belonging to this job were 0, hide the section entirely
            if (llDraftsContainer?.childCount == 0) {
                llDraftsSection?.visibility = View.GONE
            }

        } else {
            llDraftsSection?.visibility = View.GONE
        }
    }
}