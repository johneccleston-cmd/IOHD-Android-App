package com.example.employeepos2 // CHANGE TO YOUR PACKAGE

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class InventoryFragmentV2 : Fragment(R.layout.fragment_inventory) {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var adapter: InventoryAdapterV2

    private lateinit var rv: RecyclerView
    private lateinit var btnCheckout: MaterialButton
    private lateinit var loader: ProgressBar
    private lateinit var tvEmptyState: TextView

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

        // 🔥 DYNAMICALLY ADJUST CHECKOUT BUTTON MARGIN BASED ON PROFILE / BOTTOM NAV BAR
        view.post {
            if (!isAdded) return@post
            val checkoutBtn = view.findViewById<MaterialButton>(R.id.btnCheckout) ?: return@post

            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val rootBottomOnScreen = location[1] + view.height
            val screenHeight = resources.displayMetrics.heightPixels

            val insets = ViewCompat.getRootWindowInsets(view)
            val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0

            val params = checkoutBtn.layoutParams as ViewGroup.MarginLayoutParams
            val baseMargin = (16 * resources.displayMetrics.density).toInt()

            // Only add system bar inset if the root view extends to the very bottom edge of the device screen
            if (rootBottomOnScreen >= (screenHeight - 30)) {
                params.bottomMargin = bottomInset + baseMargin
            } else {
                params.bottomMargin = baseMargin
            }
            checkoutBtn.layoutParams = params
        }

        rv = view.findViewById(R.id.rvInventoryJobs)
        btnCheckout = view.findViewById(R.id.btnCheckout)
        loader = view.findViewById(R.id.inventoryLoader)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        adapter = InventoryAdapterV2 {
            // Optional: add toggle logic for checkout button if needed
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 1. Figure out whose list we are loading, AND who is holding the tablet
        val myName = encryptedPrefs.getString("NAME", "Team Member") ?: "Team Member"
        val targetTechName = arguments?.getString("TARGET_TECH_NAME") ?: myName

        // 2. Hook up Header and Back Button
        val btnBack = view.findViewById<View>(R.id.btnBack)
        val tvHeaderTitle = view.findViewById<TextView>(R.id.tvHeaderTitle)

        // 3. Set the dynamic text (e.g., "DANIEL BATY'S LIST")
        if (targetTechName != myName) {
            tvHeaderTitle.text = "${targetTechName.uppercase()}'S LIST"
        } else {
            tvHeaderTitle.text = "MY PICK LIST"
        }

        // 4. Handle Back Button click
        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // 5. Fetch the inventory using target tech name
        refreshInventory(targetTechName)

        // 6. Hook up the checkout button
        btnCheckout.setOnClickListener {
            runCheckout(targetTechName, myName)
        }
    }

    private fun refreshInventory(techName: String) {
        lifecycleScope.launch {
            loader.visibility = View.VISIBLE
            fetchJobMaterials(techName)
        }
    }

    private suspend fun fetchJobMaterials(techName: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/get_job_materials?techName=${Uri.encode(techName)}")
            .addHeader("Authorization", "Bearer $apiToken")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "[]"
                Log.d("INVENTORY_TEST", "Raw Data from Server: $body")
                val jsonArray = JSONArray(body)
                val freshJobs = mutableListOf<InventoryJob>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    val city = obj.optString("city", "Unknown City")
                    val poNumber = obj.optString("poNumber", "")
                    val description = obj.optString("description", "No description provided.")
                    val notes = obj.optString("notes", "")

                    val itemsArr = obj.getJSONArray("items")
                    val parts = mutableListOf<InventoryPart>()
                    for (j in 0 until itemsArr.length()) {
                        val p = itemsArr.getJSONObject(j)

                        parts.add(
                            InventoryPart(
                                name = p.getString("name"),
                                qtyNeeded = p.getInt("qtyNeeded"),
                                qtyInStock = p.optInt("qtyInStock", 0),
                                description = p.optString("description", "")
                            )
                        )
                    }

                    freshJobs.add(
                        InventoryJob(
                            jobNumber = obj.getString("jobNumber"),
                            customer = obj.getString("customer"),
                            city = city,
                            poNumber = poNumber,
                            description = description,
                            notes = notes,
                            items = parts
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    adapter.submitList(freshJobs)
                    updateUiState(freshJobs.isEmpty())
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Failed to load inventory", Toast.LENGTH_SHORT).show()
            }
        } finally {
            withContext(Dispatchers.Main) { loader.visibility = View.GONE }
        }
    }

    private fun runCheckout(targetTechName: String, loggedInUser: String) {
        val pickedItems = JSONArray()

        adapter.currentList.forEach { job ->
            job.items.filter { it.isPicked }.forEach { part ->
                pickedItems.put(JSONObject().apply {
                    put("name", part.name)
                    put("qtyUsed", part.qtyNeeded)
                    put("jobNumber", job.jobNumber)
                })
            }
        }

        if (pickedItems.length() == 0) {
            Toast.makeText(requireContext(), "Please check at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        val finalNameForSheet = loggedInUser

        lifecycleScope.launch {
            loader.visibility = View.VISIBLE
            val success = processCheckout(finalNameForSheet, pickedItems)
            if (success) {
                Toast.makeText(requireContext(), "Checkout Successful!", Toast.LENGTH_LONG).show()
                fetchJobMaterials(targetTechName)
            } else {
                Toast.makeText(requireContext(), "Checkout Failed", Toast.LENGTH_SHORT).show()
            }
            loader.visibility = View.GONE
        }
    }

    private suspend fun processCheckout(techNameToLog: String, pickedItems: JSONArray): Boolean = withContext(Dispatchers.IO) {

        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())

        val payload = JSONObject().apply {
            put("techName", techNameToLog)
            put("date", currentDate)
            put("items", pickedItems)
        }

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$baseUrl/checkout_inventory")
            .addHeader("Authorization", "Bearer $apiToken")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
        }
    }

    private fun updateUiState(isEmpty: Boolean) {
        if (isEmpty) {
            tvEmptyState.visibility = View.VISIBLE
            rv.visibility = View.GONE
            btnCheckout.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rv.visibility = View.VISIBLE
            btnCheckout.visibility = View.VISIBLE
        }
    }
}