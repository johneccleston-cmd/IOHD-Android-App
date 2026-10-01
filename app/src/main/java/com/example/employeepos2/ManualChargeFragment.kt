package com.example.employeepos2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.log.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class ManualChargeFragment : Fragment(R.layout.fragment_manual_charge) {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val locationId = "tml_GcilngBn7FB9b0"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var isCharging = false
    private var totalDueAmount: Double = 0.0 // 🔥 FIXED: Elevated to class property for live tracking

    private lateinit var statusText: TextView
    private lateinit var amountInput: EditText
    private lateinit var jobNumInput: EditText
    private lateinit var customerNameInput: EditText
    private lateinit var jobTotalInput: EditText
    private lateinit var jobTotalLayout: TextInputLayout
    private lateinit var categorySpinner: Spinner

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.statusText)
        amountInput = view.findViewById(R.id.amountInput)
        jobNumInput = view.findViewById(R.id.jobNumInput)
        customerNameInput = view.findViewById(R.id.customerNameInput)
        jobTotalInput = view.findViewById(R.id.jobTotalInput)
        jobTotalLayout = view.findViewById(R.id.jobTotalLayout)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        val chargeButton = view.findViewById<MaterialButton>(R.id.chargeButton)

        // Unpack passed bundle values (if navigated directly from a job card)
        val sfJobNum = arguments?.getString("PREFILL_JOB_NUM")
        val sfCustomer = arguments?.getString("PREFILL_CUSTOMER_NAME")
        val sfTotal = arguments?.getString("SF_TOTAL") ?: "0.00"
        totalDueAmount = sfTotal.toDoubleOrNull() ?: 0.0

        if (!sfJobNum.isNullOrEmpty()) {
            jobNumInput.setText(sfJobNum)
            customerNameInput.setText(sfCustomer ?: "Unknown")
            jobTotalInput.setText(sfTotal)
            lock(customerNameInput)
            lock(jobNumInput)
            lock(jobTotalInput)
            amountInput.setText(String.format(Locale.US, "%.2f", totalDueAmount))
        } else {
            // 🔥 FIXED: If typing manually, pull the live data from your backend when focus leaves the box
            jobNumInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val typedJob = jobNumInput.text.toString().trim()
                    if (typedJob.isNotEmpty()) {
                        fetchLiveJobDetails(typedJob)
                    }
                }
            }
        }

        val categories = arrayOf("Final Payment", "Deposit")

        val spinnerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val dropDownView = super.getView(position, convertView, parent) as TextView
                dropDownView.setTextColor(Color.parseColor("#000000"))
                dropDownView.textSize = 15f
                dropDownView.setTypeface(null, android.graphics.Typeface.BOLD)
                return dropDownView
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val dropDownView = super.getDropDownView(position, convertView, parent) as TextView
                dropDownView.setTextColor(Color.parseColor("#000000"))
                dropDownView.textSize = 15f
                dropDownView.setTypeface(null, android.graphics.Typeface.BOLD)
                dropDownView.setPadding(40, 40, 40, 40)
                return dropDownView
            }
        }
        categorySpinner.adapter = spinnerAdapter

        // 🔥 FIXED: Listens uniformly regardless of manual type or pre-fill state
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCalculatedAmounts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (!Terminal.isInitialized()) {
            Terminal.initTerminal(
                requireContext().applicationContext,
                LogLevel.VERBOSE,
                TokenProvider("$baseUrl/connection_token"),
                object : TerminalListener {
                    override fun onUnexpectedReaderDisconnect(reader: Reader) {
                        activity?.runOnUiThread { updateStatus("Reader Disconnected", "#CC0007") }
                    }
                }
            )
        }

        chargeButton.setOnClickListener {
            if (isCharging) return@setOnClickListener

            val amount = amountInput.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isCharging = true
            chargeButton.isEnabled = false

            vibrateShort()
            updateStatus("Preparing Reader...", "#000000")

            if (!hasPermissions()) {
                requestPermissions()
                resetCharge()
                return@setOnClickListener
            }

            val terminal = Terminal.getInstance()
            if (terminal.connectedReader != null) {
                createPaymentIntent()
            } else {
                startDiscovery()
            }
        }
    }

    // 🔥 FIXED: Live background look-up tool via your Render API
    private fun fetchLiveJobDetails(jobNum: String) {
        updateStatus("Searching job #$jobNum...", "#0000CC")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/fetch_sf_job?jobNum=$jobNum")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && bodyString != null) {
                            val json = JSONObject(bodyString)
                            if (json.getString("status") == "success") {
                                val customer = json.optString("customer", "Unknown Customer")
                                val totalStr = json.optString("total", "0.00")

                                totalDueAmount = totalStr.toDoubleOrNull() ?: 0.0

                                customerNameInput.setText(customer)
                                jobTotalInput.setText(totalStr)

                                updateCalculatedAmounts()
                                updateStatus("Job loaded successfully", "#228B22")
                            } else {
                                updateStatus(json.optString("error", "Job not found."), "#CC0007")
                            }
                        } else {
                            updateStatus("Lookup failed", "#CC0007")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Network error during lookup", "#CC0007")
                }
            }
        }
    }

    // 🔥 FIXED: Unified calculation engine for deposits/totals
    private fun updateCalculatedAmounts() {
        val selectedCategory = categorySpinner.selectedItem?.toString() ?: "Final Payment"
        jobTotalLayout.visibility = if (selectedCategory == "Deposit") View.VISIBLE else View.GONE

        amountInput.setText(
            when (selectedCategory) {
                "Final Payment" -> String.format(Locale.US, "%.2f", totalDueAmount)
                "Deposit" -> String.format(Locale.US, "%.2f", totalDueAmount * 0.5)
                else -> amountInput.text.toString()
            }
        )
    }

    private fun startDiscovery() {
        val config = DiscoveryConfiguration.LocalMobileDiscoveryConfiguration(isSimulated = false)

        Terminal.getInstance().discoverReaders(
            config,
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    if (readers.isNotEmpty()) connectReader(readers.first())
                    else updateStatus("No Reader Found", "#CC0007")
                }
            },
            object : com.stripe.stripeterminal.external.callable.Callback {
                override fun onSuccess() {}
                override fun onFailure(e: TerminalException) {
                    updateStatus("Discovery Failed", "#CC0007")
                    resetCharge()
                }
            }
        )
    }

    private fun connectReader(reader: Reader) {
        val config = ConnectionConfiguration.LocalMobileConnectionConfiguration(locationId)
        Terminal.getInstance().connectLocalMobileReader(reader, config, object : ReaderCallback {
            override fun onSuccess(reader: Reader) { createPaymentIntent() }
            override fun onFailure(e: TerminalException) {
                updateStatus("Connect failed", "#CC0007")
                resetCharge()
            }
        })
    }

    private fun createPaymentIntent() {
        val amountInCents = (amountInput.text.toString().toDouble() * 100).toLong()
        val params = PaymentIntentParameters.Builder()
            .setAmount(amountInCents)
            .setCurrency("usd")
            .setCaptureMethod(CaptureMethod.Automatic)
            .setMetadata(mapOf(
                "job_number" to jobNumInput.text.toString(),
                "customer_name" to customerNameInput.text.toString(),
                "category" to categorySpinner.selectedItem.toString()
            ))
            .build()

        Terminal.getInstance().createPaymentIntent(params, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) { collectPayment(paymentIntent) }
            override fun onFailure(e: TerminalException) {
                updateStatus("Intent failed", "#CC0007")
                resetCharge()
            }
        })
    }

    private fun collectPayment(paymentIntent: PaymentIntent) {
        updateStatus("Tap card / hold device", "#000000")
        Terminal.getInstance().collectPaymentMethod(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) { confirmPayment(paymentIntent) }
            override fun onFailure(e: TerminalException) {
                updateStatus("Payment cancelled", "#CC0007")
                resetCharge()
            }
        })
    }

    private fun confirmPayment(paymentIntent: PaymentIntent) {
        Terminal.getInstance().confirmPaymentIntent(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) { sendToBackend(paymentIntent.id ?: return) }
            override fun onFailure(e: TerminalException) {
                updateStatus("Confirm failed", "#CC0007")
                resetCharge()
            }
        })
    }

    private fun sendToBackend(stripeId: String) {
        val json = JSONObject().apply {
            put("jobNum", jobNumInput.text.toString())
            put("amount", amountInput.text.toString())
            put("stripeId", stripeId)
            put("customer", customerNameInput.text.toString())
            put("category", categorySpinner.selectedItem.toString())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/record_sale")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { res ->
                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful) {
                            updateStatus("✓ PAYMENT SUCCESS", "#228B22")
                            vibrateSuccess()
                            clearFields()
                        } else {
                            updateStatus("Server log failed", "#CC0007")
                        }
                        resetCharge()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Network error", "#CC0007")
                    resetCharge()
                }
            }
        }
    }

    private fun resetCharge() {
        isCharging = false
        activity?.runOnUiThread {
            view?.findViewById<MaterialButton>(R.id.chargeButton)?.isEnabled = true
        }
    }

    private fun clearFields() {
        amountInput.text.clear()
        if (customerNameInput.isEnabled) {
            jobNumInput.text.clear()
            customerNameInput.text.clear()
            jobTotalInput.text.clear()
        }
    }

    private fun lock(editText: EditText) {
        editText.isEnabled = false
        editText.alpha = 0.5f
    }

    private fun hasPermissions(): Boolean {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return perms.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        ActivityCompat.requestPermissions(requireActivity(), perms.toTypedArray(), 1)
    }

    private fun updateStatus(text: String, color: String) {
        activity?.runOnUiThread {
            statusText.text = text
            statusText.setTextColor(color.toColorInt())
        }
    }

    private fun vibrateShort() {
        val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateSuccess() {
        val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 120), intArrayOf(0, 255, 0, 255), -1))
    }
}