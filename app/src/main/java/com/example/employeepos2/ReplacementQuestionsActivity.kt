package com.example.employeepos2

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class ReplacementQuestionsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replacement_questions)

        val jobNumber = intent.getStringExtra("SF_JOB_NUM") ?: ""
        val customerName = intent.getStringExtra("SF_CUSTOMER_NAME") ?: ""
        val techName = intent.getStringExtra("TECH_NAME") ?: ""

        findViewById<TextView>(R.id.tvLockedJob).text = "JOB: #$jobNumber"
        findViewById<TextView>(R.id.tvLockedCustomer).text = "Customer: $customerName"
        findViewById<TextView>(R.id.tvLockedtech).text = "TECH: $techName"

        // 🔥 Added the new group to get colored
        val groups = listOf(
            findViewById<ChipGroup>(R.id.rgReplacing),
            findViewById<ChipGroup>(R.id.rgConcreteProblem)
        )
        applyChipColors(groups)

        findViewById<MaterialButton>(R.id.btnNextToMeasure).setOnClickListener {

            val replaceGroup = findViewById<ChipGroup>(R.id.rgReplacing)
            val concreteProbGroup = findViewById<ChipGroup>(R.id.rgConcreteProblem)

            if (replaceGroup.checkedChipId == View.NO_ID || concreteProbGroup.checkedChipId == View.NO_ID) {
                Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val replacingAnswer = findViewById<Chip>(replaceGroup.checkedChipId).text.toString()
            val concreteProbAnswer = findViewById<Chip>(concreteProbGroup.checkedChipId).text.toString()

            val nextIntent = Intent(this, SiteCheckActivity::class.java).apply {
                putExtra("SF_JOB_NUM", jobNumber)
                putExtra("SF_CUSTOMER_NAME", customerName)
                putExtra("TECH_NAME", techName)
                // 👇 Make sure you pass the Location and City forward from the previous screen! 👇
                putExtra("LOCATION", intent.getStringExtra("LOCATION"))
                putExtra("CITY", intent.getStringExtra("CITY"))

                putExtra("CHECK_TYPE", "REPLACEMENT")
                putExtra("REPLACING", replacingAnswer)
                putExtra("CONCRETE_PROBLEM", concreteProbAnswer)
            }
            startActivity(nextIntent)
        }
    }

    private fun applyChipColors(groups: List<ChipGroup>) {
        val integrityRed = Color.parseColor("#CC0007")
        val white = Color.WHITE
        val blackText = Color.parseColor("#1A1C1E")

        val bgStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(integrityRed, white)
        )
        val textStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(white, blackText)
        )
        val strokeStateList = ColorStateList.valueOf(integrityRed)

        for (cg in groups) {
            for (i in 0 until cg.childCount) {
                val chip = cg.getChildAt(i) as? Chip ?: continue
                chip.chipBackgroundColor = bgStateList
                chip.setTextColor(textStateList)
                chip.checkedIconTint = ColorStateList.valueOf(white)
                chip.chipStrokeColor = strokeStateList
                chip.chipStrokeWidth = 3f
            }
        }
    }
}