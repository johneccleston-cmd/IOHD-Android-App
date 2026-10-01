package com.example.employeepos2

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class PaymentFragment : Fragment(R.layout.activity_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnServiceFusion = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnServiceFusion)
        val btnNewCharge = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnNewCharge)

        // 1. Service Fusion Flow - Swaps to the Lookup Fragment
        btnServiceFusion?.setOnClickListener {
            // We call the loadFragment function sitting in our MainActivity
            (requireActivity() as MainActivity).loadFragment(LookupFragmentV2())
        }

        // 2. Manual Charge Flow
        btnNewCharge?.setOnClickListener {
            // This tells MainActivity to swap the middle of the screen to the Manual Charge UI
            (requireActivity() as MainActivity).loadFragment(ManualChargeFragment())
        }
    }
}