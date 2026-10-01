package com.example.employeepos2 // CHANGE IF YOUR PACKAGE IS DIFFERENT

import android.os.Bundle
import android.view.View
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryMenuFragment : Fragment(R.layout.fragment_inventory_menu) {

    private var currentUserRole = "INSTALLER"
    private var currentUserName = ""

    private val encryptedPrefs by lazy {
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

        val btnStockSearch = view.findViewById<CardView>(R.id.btnStockSearch)
        val btnPicker      = view.findViewById<CardView>(R.id.btnWarehousePicker)
        val btnScanner     = view.findViewById<CardView>(R.id.btnScanner)
        val btnCycleCount  = view.findViewById<CardView>(R.id.btnCycleCount)

        lifecycleScope.launch {
            currentUserRole = withContext(Dispatchers.IO) {
                encryptedPrefs.getString("ROLE", "INSTALLER") ?: "INSTALLER"
            }

            currentUserName = withContext(Dispatchers.IO) {
                encryptedPrefs.getString("NAME", "") ?: ""
            }

            val role = currentUserRole.uppercase()
            val cleanName = currentUserName.replace(" ", "").lowercase()

            // 1. Hide specific buttons from Office staff
            val isOffice = role.startsWith("OFFICE")
            if (isOffice) {
                btnPicker.visibility  = View.GONE
                btnScanner.visibility = View.GONE
            }

            // 2. 🔥 STRICT CYCLE COUNT GUARD: Only Warehouse & Admins can see the Cycle Count button
            val isWarehouseOrAdmin = cleanName == "warehouse1" || role.contains("WAREHOUSE") || role.contains("ADMIN")
            if (!isWarehouseOrAdmin) {
                btnCycleCount.visibility = View.GONE // Hides it for regular field techs & office
            }
        }

        // --- NAVIGATION LOGIC ---
        btnStockSearch.setOnClickListener {
            (requireActivity() as MainActivity).loadFragment(StockSearchFragment(), true)
        }

        btnPicker.setOnClickListener {
            val cleanName = currentUserName.replace(" ", "").lowercase()
            val role = currentUserRole.uppercase()

            if (cleanName == "warehouse1" || role.contains("WAREHOUSE") || role.contains("ADMIN")) {
                (requireActivity() as MainActivity).loadFragment(WarehouseTechListFragment(), true)
            } else {
                (requireActivity() as MainActivity).loadFragment(InventoryFragmentV2(), true)
            }
        }

        btnScanner.setOnClickListener {
            (requireActivity() as MainActivity).loadFragment(ScannerFragment(), true)
        }

        btnCycleCount.setOnClickListener {
            // (The button is already hidden for non-admins, but we keep this check for security)
            val cleanName = currentUserName.replace(" ", "").lowercase()
            val role = currentUserRole.uppercase()

            if (cleanName == "warehouse1" || role.contains("WAREHOUSE") || role.contains("ADMIN")) {
                (requireActivity() as MainActivity).loadFragment(CycleCountMenuFragment(), true)
            }
        }
    }
}