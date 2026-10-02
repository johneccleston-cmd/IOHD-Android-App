package com.example.employeepos2

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🔥 Data class to hold our background-loaded profile data
data class ProfileData(
    val name: String,
    val role: String,
    val year: String,
    val allowance: String,
    val badges: Map<String, Boolean>
)

class ProfileFragmentV2 : Fragment() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvName: TextView
    private lateinit var tvJoinYear: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var btnMyDocuments: MaterialButton
    private lateinit var ivAdminMenu: ImageView
    private lateinit var btnLogout: MaterialButton
    private lateinit var imgUserPic: ShapeableImageView
    private lateinit var tvToolAllowance: TextView
    private lateinit var pbToolAllowance: LiquidProgressBar
    private lateinit var btnSideMasterSchedule: MaterialButton
    private lateinit var btnSideTeamCommissions: MaterialButton
    private lateinit var btnSideTeamToolAllowance: MaterialButton

    // Cache the name so the My Documents button doesn't freeze the UI on click
    private var currentTechName = "Unknown"

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_profile_v2, container, false)

        drawerLayout = rootView.findViewById(R.id.drawerLayout)
        ivAdminMenu = rootView.findViewById(R.id.ivAdminMenu)
        tvName = rootView.findViewById(R.id.tvProfileName)
        tvJoinYear = rootView.findViewById(R.id.join_year_display)
        tvRole = rootView.findViewById(R.id.tvProfileRole)
        tvAvatarInitials = rootView.findViewById(R.id.tvAvatarInitials)
        btnMyDocuments = rootView.findViewById(R.id.btnMyDocuments)
        btnLogout = rootView.findViewById(R.id.btnLogout)
        imgUserPic = rootView.findViewById(R.id.imgUserProfilePic)
        tvToolAllowance = rootView.findViewById(R.id.tvToolAllowance)
        pbToolAllowance = rootView.findViewById(R.id.pbToolAllowance)
        btnSideMasterSchedule = rootView.findViewById(R.id.btnSideMasterSchedule)
        btnSideTeamCommissions = rootView.findViewById(R.id.btnSideTeamCommissions)
        btnSideTeamToolAllowance = rootView.findViewById(R.id.btnSideTeamToolAllowance)

        ivAdminMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }

        btnSideMasterSchedule.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(requireContext(), MasterScheduleActivity::class.java))
        }

        btnSideTeamCommissions.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            (requireActivity() as MainActivity).loadFragment(TeamCommissionFragment(), true)
        }

        btnSideTeamToolAllowance.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            (requireActivity() as MainActivity).loadFragment(TeamToolAllowanceFragment(), true)
        }

        btnMyDocuments.setOnClickListener {
            val intent = Intent(requireContext(), MyDocumentsActivity::class.java).apply {
                putExtra("TECH_NAME", currentTechName)
            }
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            prefs.edit().apply {
                remove("NAME")
                remove("ROLE")
                remove("SAVED_PIN")
                remove("JOIN_YEAR")
                remove("TOOL_ALLOWANCE")
                remove("LAST_FCM_TOKEN")
                remove("HAS_5STAR")
                remove("HAS_NO_RECALLS")
                remove("IS_LEAD")
                remove("Y1")
                remove("Y2")
                remove("Y3")
                remove("Y4")
                remove("Y5")
            }.apply()

            val intent = Intent(requireActivity(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }

        clearProfile()
        loadProfile(rootView)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            clearProfile()
            loadProfile(it)
        }
    }

    private fun clearProfile() {
        tvName.text = ""
        tvRole.text = ""
        tvJoinYear.text = ""
        tvToolAllowance.text = ""
        tvAvatarInitials.text = ""
        imgUserPic.visibility = View.GONE
        tvAvatarInitials.visibility = View.GONE
    }

    private fun loadProfile(rootView: View) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val n = prefs.getString("NAME", "Team Member") ?: "Team Member"
                val r = prefs.getString("ROLE", "INSTALLER") ?: "INSTALLER"
                val y = prefs.getString("JOIN_YEAR", "2025") ?: "2025"
                val a = prefs.getString("TOOL_ALLOWANCE", "999") ?: "999"

                val b = mapOf(
                    "HAS_5STAR" to prefs.getBoolean("HAS_5STAR", false),
                    "HAS_NO_RECALLS" to prefs.getBoolean("HAS_NO_RECALLS", false),
                    "IS_LEAD" to prefs.getBoolean("IS_LEAD", false),
                    "Y1" to prefs.getBoolean("Y1", false),
                    "Y2" to prefs.getBoolean("Y2", false),
                    "Y3" to prefs.getBoolean("Y3", false),
                    "Y4" to prefs.getBoolean("Y4", false),
                    "Y5" to prefs.getBoolean("Y5", false)
                )
                ProfileData(n, r, y, a, b)
            }

            currentTechName = data.name
            tvName.text = data.name
            tvJoinYear.text = "Team member since ${data.year}"

            val allowanceLeft = data.allowance.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 1000
            tvToolAllowance.text = "$${allowanceLeft} Remaining"
            val targetProgress = (allowanceLeft.toFloat() / 1000f).coerceIn(0f, 1f)

            android.animation.ObjectAnimator.ofFloat(pbToolAllowance, "progress", 0f, targetProgress).apply {
                duration = 1500
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }

            val roleUpper = data.role.uppercase()
            val isAdmin = roleUpper == "OWNER" || roleUpper == "ADMIN" || roleUpper == "OFFICE MANAGER"
            val isOfficeStaff = isAdmin || roleUpper == "OFFICE"

            rootView.findViewById<View>(R.id.cardToolAllowance)?.visibility = if (isOfficeStaff) View.GONE else View.VISIBLE
            ivAdminMenu.visibility = if (isAdmin) View.VISIBLE else View.GONE
            drawerLayout.setDrawerLockMode(if (isAdmin) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

            // 🔥 HIDE THE TEAM COMMISSIONS BUTTON IF THE USER IS "OFFICE"
            if (data.role.uppercase() == "OFFICE") {
                btnSideTeamCommissions.visibility = View.GONE
            } else {
                btnSideTeamCommissions.visibility = View.VISIBLE
            }

            tvRole.setTextColor(Color.parseColor("#CC0007"))
            if (data.name.equals("Ryan Schwartz", ignoreCase = true)) {
                tvRole.text = "OWNER"
                tvJoinYear.text = "Owner Since 2018"
            } else {
                tvRole.text = data.role.uppercase()
            }

            setupAvatar(data.name)
            setupBadges(rootView, data.name, data.badges)
        }
    }

    private fun setupAvatar(name: String) {
        val fileName = name.lowercase().trim().replace(" ", "_")
        val resId = resources.getIdentifier(fileName, "drawable", requireContext().packageName)
        if (resId != 0) {
            imgUserPic.setImageResource(resId)
            imgUserPic.visibility = View.VISIBLE
            tvAvatarInitials.visibility = View.GONE
        } else {
            val initials = name.split(" ").filter { it.isNotBlank() }.map { it[0].uppercase() }.joinToString("").take(2)
            tvAvatarInitials.text = initials
            tvAvatarInitials.visibility = View.VISIBLE
            imgUserPic.visibility = View.GONE
        }
    }

    private fun setupBadges(rootView: View, name: String, badgeStatus: Map<String, Boolean>) {
        data class BadgeInfo(val prefKey: String, val title: String, val description: String)
        val badgeMap = mapOf(
            R.id.badge5Star to BadgeInfo("HAS_5STAR", "5-Star Elite", "The customer's favorite. You don't just finish the job; you leave a lasting impression of excellence."),
            R.id.badgeNoRecalls to BadgeInfo("HAS_NO_RECALLS", "Precision Pro", "Right the first time, every time. This badge is reserved for those with a perfect record of zero callbacks."),
            R.id.badgeLeadTech to BadgeInfo("IS_LEAD", "Master Lead Tech", "A cornerstone of the Integrity crew. Recognized for top-tier technical expertise and leadership in the field."),
            R.id.badge1Year to BadgeInfo("Y1", "Freshman Ace", "One year in the books! You've officially cemented your place as a vital part of the team."),
            R.id.badge2Year to BadgeInfo("Y2", "Reliable Force", "Two years of consistent excellence. You've become a pro that the team can always count on."),
            R.id.badge3Year to BadgeInfo("Y3", "The Standard", "Three years of service. At this point, you don't just follow the standard—you are the standard."),
            R.id.badge4Year to BadgeInfo("Y4", "Core Pillar", "Four years of dedication. You are a fundamental part of the foundation that keeps Integrity strong."),
            R.id.badge5Year to BadgeInfo("Y5", "Legacy Builder", "Five years of legendary service. A true veteran, a master of the craft, and a mentor to all.")
        )

        for ((id, info) in badgeMap) {
            val badgeView = rootView.findViewById<ImageView>(id) ?: continue
            val earned = badgeStatus[info.prefKey] ?: false
            val hasBadge = name.equals("Ryan Schwartz", ignoreCase = true) || earned

            (badgeView.parent as View).alpha = if (hasBadge) 1.0f else 0.15f
            badgeView.isClickable = true
            badgeView.setOnClickListener {
                showBadgeDialog(badgeView, info.title, info.description, hasBadge)
            }
        }
    }

    private fun showBadgeDialog(badgeView: ImageView, title: String, desc: String, hasBadge: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_badge, null)
        val iconView = dialogView.findViewById<ImageView>(R.id.dialogBadgeIcon)
        val statusView = dialogView.findViewById<TextView>(R.id.dialogBadgeStatus)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogBadgeTitle)
        val descView = dialogView.findViewById<TextView>(R.id.dialogBadgeDesc)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnDialogClose)

        iconView.setImageDrawable(badgeView.drawable)
        titleView.text = title
        descView.text = desc

        if (hasBadge) {
            statusView.text = "🌟 EARNED"
            statusView.setTextColor(Color.parseColor("#009E18"))
            btnClose.text = "Awesome"
        } else {
            statusView.text = "🔒 LOCKED"
            statusView.setTextColor(Color.parseColor("#9C9C9C"))
            btnClose.text = "Got it"
            btnClose.setBackgroundColor(Color.parseColor("#757575"))
        }

        val badgeDialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        badgeDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnClose.setOnClickListener { badgeDialog.dismiss() }
        badgeDialog.show()

        iconView.rotation = 0f
        iconView.scaleX = 0f
        iconView.scaleY = 0f
        iconView.animate().rotation(360f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(android.view.animation.OvershootInterpolator()).start()
    }
}