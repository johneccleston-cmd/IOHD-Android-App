package com.example.employeepos2

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

class JobsFragmentV2 : Fragment() {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val locationId = "tml_GcilngBn7FB9b0"
    private val apiToken = "3cf46a8e52e497f9a0bd4fdf7aab227cb2291bcf084bfa9ff60bc88d58090cf7"

    private lateinit var statusText: TextView
    private lateinit var amountInput: EditText
    private lateinit var jobNumInput: EditText
    private lateinit var customerNameInput: EditText
    private lateinit var jobTotalInput: EditText
    private lateinit var jobTotalLayout: TextInputLayout
    private lateinit var categorySpinner: Spinner

    private var totalDueAmount: Double = 0.0
    private var isConnecting = false
    private var isCharging = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_jobs_v2, container, false)

        statusText = rootView.findViewById(R.id.statusText)
        amountInput = rootView.findViewById(R.id.amountInput)
        jobNumInput = rootView.findViewById(R.id.jobNumInput)
        customerNameInput = rootView.findViewById(R.id.customerNameInput)
        jobTotalInput = rootView.findViewById(R.id.jobTotalInput)
        jobTotalLayout = rootView.findViewById(R.id.jobTotalLayout)
        categorySpinner = rootView.findViewById(R.id.categorySpinner)
        val chargeButton = rootView.findViewById<MaterialButton>(R.id.chargeButton)

        val categories = arrayOf("Final Payment", "Deposit")

        val spinnerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.parseColor("#000000"))
                view.textSize = 15f
                view.setTypeface(null, android.graphics.Typeface.BOLD)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.parseColor("#000000"))
                view.textSize = 15f
                view.setTypeface(null, android.graphics.Typeface.BOLD)
                view.setPadding(40, 40, 40, 40)
                return view
            }
        }

        categorySpinner.adapter = spinnerAdapter

        // Unpack explicit arguments bundle keys
        val sfJobNum = arguments?.getString("PREFILL_JOB_NUM")
        val sfCustomer = arguments?.getString("PREFILL_CUSTOMER_NAME")
        val sfTotalStr = arguments?.getString("SF_TOTAL") ?: "0.00"


        val cleanTotalStr = sfTotalStr.replace(Regex("[^0-9.]"), "")
        totalDueAmount = cleanTotalStr.toDoubleOrNull() ?: 0.0

        if (!sfJobNum.isNullOrEmpty()) {
            jobNumInput.setText(sfJobNum)
            customerNameInput.setText(sfCustomer ?: "Unknown Customer")
            jobTotalInput.setText(String.format(Locale.US, "%.2f", totalDueAmount))

            lockField(jobNumInput)
            lockField(customerNameInput)
            lockField(jobTotalInput)

            amountInput.setText(String.format(Locale.US, "%.2f", totalDueAmount))
        } else {

            jobNumInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val typedJob = jobNumInput.text.toString().trim()
                    if (typedJob.isNotEmpty()) {
                        fetchLiveJobDetails(typedJob)
                    }
                }
            }
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCalculatedAmounts(categories[position])
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

            val amountStr = amountInput.text.toString()
            if (amountStr.isEmpty() || amountStr.toDoubleOrNull() == null || amountStr.toDouble() <= 0) {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bluetoothAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (bluetoothAdapter?.isEnabled == false) {
                updateStatus("Please enable Bluetooth", "#CC0007")
                return@setOnClickListener
            }

            isCharging = true
            vibrateShort()
            updateStatus("Preparing Reader...", "#000000")

            if (hasRequiredPermissions()) {
                val terminal = Terminal.getInstance()
                if (terminal.connectedReader != null) createPaymentIntent()
                else startDiscovery()
            } else {
                checkAndRequestPermissions()
                isCharging = false
            }
        }

        return rootView
    }


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

                                val cleanTotal = totalStr.replace(Regex("[^0-9.]"), "")
                                totalDueAmount = cleanTotal.toDoubleOrNull() ?: 0.0

                                customerNameInput.setText(customer)
                                jobTotalInput.setText(String.format(Locale.US, "%.2f", totalDueAmount))

                                val selectedCategory = categorySpinner.selectedItem?.toString() ?: "Final Payment"
                                updateCalculatedAmounts(selectedCategory)
                                updateStatus("Job details loaded", "#228B22")
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

    private fun updateCalculatedAmounts(category: String) {
        jobTotalLayout.visibility = if (category == "Deposit") View.VISIBLE else View.GONE
        when (category) {
            "Final Payment" -> amountInput.setText(String.format(Locale.US, "%.2f", totalDueAmount))
            "Deposit" -> amountInput.setText(String.format(Locale.US, "%.2f", totalDueAmount * 0.5))
        }
    }

    private fun startDiscovery() {
        isConnecting = false
        val config = DiscoveryConfiguration.LocalMobileDiscoveryConfiguration(isSimulated = false)
        updateStatus("Discovering reader...", "#0000CC")

        Terminal.getInstance().discoverReaders(
            config,
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    if (readers.isNotEmpty() && !isConnecting) {
                        isConnecting = true
                        connectReader(readers[0])
                    }
                }
            },
            object : com.stripe.stripeterminal.external.callable.Callback {
                override fun onSuccess() {
                    if (!isConnecting) {
                        updateStatus("No reader found. Check NFC/Location.", "#CC0007")
                        isCharging = false // 🔥 FIXED: Unlocks the button if nothing is found
                    }
                }
                override fun onFailure(e: TerminalException) {
                    // 🔥 FIXED: Now it tells you EXACTLY why it failed
                    updateStatus("Discovery Failed: ${e.errorMessage}", "#CC0007")
                    isCharging = false
                }
            }
        )
    }

    private fun connectReader(reader: Reader) {
        val connectConfig = ConnectionConfiguration.LocalMobileConnectionConfiguration(locationId)
        Terminal.getInstance().connectLocalMobileReader(reader, connectConfig, object : ReaderCallback {
            override fun onSuccess(reader: Reader) = createPaymentIntent()
            override fun onFailure(e: TerminalException) {
                isConnecting = false
                isCharging = false
                updateStatus("Connect error: ${e.errorMessage}", "#CC0007")
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
            override fun onSuccess(paymentIntent: PaymentIntent) = collectPayment(paymentIntent)
            override fun onFailure(e: TerminalException) {
                updateStatus("Intent fail", "#CC0007")
                isCharging = false
            }
        })
    }

    private fun collectPayment(paymentIntent: PaymentIntent) {
        updateStatus("Ready: Hold Card to Phone", "#000000")
        Terminal.getInstance().collectPaymentMethod(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) = confirmPayment(paymentIntent)
            override fun onFailure(e: TerminalException) {
                updateStatus("Tap Canceled", "#CC0007")
                isCharging = false
            }
        })
    }

    private fun confirmPayment(paymentIntent: PaymentIntent) {
        Terminal.getInstance().confirmPaymentIntent(paymentIntent, object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) = sendToBackend(paymentIntent.id ?: "no_id")
            override fun onFailure(e: TerminalException) {
                updateStatus("Confirm fail", "#CC0007")
                isCharging = false
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
            put("jobTotal", jobTotalInput.text.toString())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/record_sale")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    // 🔥 FIXED: We capture the exact server response code and message before it disappears
                    val code = response.code
                    val errorBody = response.body?.string() ?: "No message provided by server"

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            updateStatus("✓ PAYMENT SUCCESSFUL", "#228B22")
                            vibrateSuccess()
                            clearInputs()
                        } else {
                            // 🔥 FIXED: It will now print exactly why the server rejected it
                            updateStatus("Server Error $code", "#CC0007")
                            Log.e("PAYMENT_ERROR", "Backend rejected sale. Code: $code, Reason: $errorBody")
                        }
                        isCharging = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Network error: ${e.message}", "#CC0007")
                    isCharging = false
                }
            }
        }
    }

    private fun clearInputs() {
        amountInput.setText("")
        if (jobNumInput.isEnabled) {
            jobNumInput.setText("")
            customerNameInput.setText("")
            jobTotalInput.setText("")
        }
    }

    private fun lockField(editText: EditText) {
        editText.isEnabled = false
        editText.alpha = 0.5f
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NFC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        ActivityCompat.requestPermissions(requireActivity(), permissions.toTypedArray(), 1)
    }

    private fun updateStatus(text: String, colorHex: String) {
        activity?.runOnUiThread {
            statusText.text = text
            statusText.setTextColor(colorHex.toColorInt())
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