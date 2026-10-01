package com.example.employeepos2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment(R.layout.fragment_scanner) {

    private val baseUrl = "https://integrity-backend-cr02.onrender.com"
    private val client = ApiClient.client

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var tvResult: TextView
    private lateinit var scanControls: View
    private lateinit var spinnerQty: Spinner
    private lateinit var btnDeduct: MaterialButton
    private lateinit var btnAdd: MaterialButton
    private lateinit var focusBox: View
    private lateinit var tvScanHint: TextView

    private var lastScannedCode: String? = null
    private var isScanningPaused = false
    private var isProcessingFrame = false

    private var candidateCode: String? = null
    private var consecutiveHits = 0
    private val REQUIRED_HITS = 3

    // Normalized (0..1) rect of the focus box relative to the PreviewView
    private var focusBoxRect: android.graphics.RectF? = null

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

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView  = view.findViewById(R.id.previewView)
        tvResult     = view.findViewById(R.id.tvScannedCode)
        scanControls = view.findViewById(R.id.scanControls)
        spinnerQty   = view.findViewById(R.id.spinnerQty)
        btnDeduct    = view.findViewById(R.id.btnDeduct)
        btnAdd       = view.findViewById(R.id.btnAdd)
        focusBox     = view.findViewById(R.id.focusBox)
        tvScanHint   = view.findViewById(R.id.tvScanHint)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Uses your custom white-bg/black-text blueprint
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, (1..50).toList())
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerQty.adapter = adapter

        btnDeduct.setOnClickListener { sendScanRequest("deduct", spinnerQty.selectedItem.toString()) }
        btnAdd.setOnClickListener    { sendScanRequest("add",    spinnerQty.selectedItem.toString()) }
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { resetScanState() }

        // Cache the focus box position after layout is complete
        focusBox.post { cacheFocusBoxRect() }

        checkCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ─── Focus Box Mapping ───────────────────────────────────────────────────

    private fun cacheFocusBoxRect() {
        val previewLoc = IntArray(2)
        previewView.getLocationOnScreen(previewLoc)

        val boxLoc = IntArray(2)
        focusBox.getLocationOnScreen(boxLoc)

        // Position of focusBox relative to previewView (not the whole screen)
        val relLeft   = (boxLoc[0] - previewLoc[0]).toFloat()
        val relTop    = (boxLoc[1] - previewLoc[1]).toFloat()
        val relRight  = relLeft + focusBox.width.toFloat()
        val relBottom = relTop  + focusBox.height.toFloat()

        val previewW  = previewView.width.toFloat()
        val previewH  = previewView.height.toFloat()

        focusBoxRect = android.graphics.RectF(
            relLeft   / previewW,
            relTop    / previewH,
            relRight  / previewW,
            relBottom / previewH
        )

        Log.d("Scanner", "focusBoxRect (normalized): $focusBoxRect")
        Log.d("Scanner", "previewView size: ${previewW}x${previewH}")
        Log.d("Scanner", "focusBox px size: ${focusBox.width}x${focusBox.height}")
    }

    // ─── Scan State ──────────────────────────────────────────────────────────

    private fun onBarcodeLocked(code: String) {
        // 🔥 Check if launched in lookup mode
        val isLookupMode = arguments?.getBoolean("IS_LOOKUP_MODE", false) ?: false
        if (isLookupMode) {
            parentFragmentManager.setFragmentResult(
                "REQUEST_KEY_SCAN",
                Bundle().apply { putString("SCANNED_BARCODE", code) }
            )
            parentFragmentManager.popBackStack()
            return
        }

        isScanningPaused = true
        lastScannedCode  = code
        tvResult.text    = "SKU: $code"
        setFocusBoxColor(locked = true)
        scanControls.visibility = View.VISIBLE
        tvScanHint.text  = "Barcode locked — select action below"
    }

    private fun resetScanState() {
        lastScannedCode  = null
        candidateCode    = null
        consecutiveHits  = 0
        isScanningPaused = false
        scanControls.visibility = View.GONE
        spinnerQty.setSelection(0)
        setFocusBoxColor(locked = false)
        tvScanHint.text  = "Align barcode inside the box"
    }

    private fun setFocusBoxColor(locked: Boolean) {
        focusBox.backgroundTintList =
            if (locked) android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            else        android.content.res.ColorStateList.valueOf(Color.WHITE)
    }

    // ─── Network ─────────────────────────────────────────────────────────────

    private fun sendScanRequest(action: String, qty: String) {
        val code     = lastScannedCode ?: return
        val techName = prefs.getString("NAME", "Unknown Tech") ?: "Unknown Tech"

        val payload = JSONObject().apply {
            put("techName", techName)
            put("barcode",  code)
            put("qtyUsed",  qty.toInt())
            put("action",   action)
        }

        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/scan_checkout")
            .post(body)
            .build()

        btnDeduct.isEnabled = false
        btnAdd.isEnabled    = false

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val respBody = response.body?.string()
                activity?.runOnUiThread {
                    btnDeduct.isEnabled = true
                    btnAdd.isEnabled    = true
                    if (response.isSuccessful) {
                        val label = if (action == "add") "Added" else "Deducted"
                        Toast.makeText(context, "✓ $label $qty unit(s) of SKU $code", Toast.LENGTH_SHORT).show()
                        resetScanState()
                    } else {
                        val error = try {
                            JSONObject(respBody ?: "{}").optString("error", "Request failed")
                        } catch (e: Exception) { "Part not found in inventory" }
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    btnDeduct.isEnabled = true
                    btnAdd.isEnabled    = true
                    Toast.makeText(context, "Network error — check connection", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // ─── Camera Setup ────────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(context, "Camera permission required", Toast.LENGTH_LONG).show()
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else launcher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor) { proxy -> processImageProxy(proxy) }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
            } catch (e: Exception) {
                Log.e("Scanner", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ─── Frame Analysis ──────────────────────────────────────────────────────

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessingFrame || isScanningPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        isProcessingFrame = true

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image   = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->

                // Log every detection to help diagnose coordinate issues
                if (barcodes.isNotEmpty()) {
                    Log.d("Scanner", "rotation=$rotationDegrees image=${image.width}x${image.height}")
                    barcodes.forEach { Log.d("Scanner", "  found '${it.rawValue}' box=${it.boundingBox}") }
                }

                val normRect = focusBoxRect

                // If layout hasn't finished yet, accept anything so scanning isn't broken
                if (normRect == null) {
                    activity?.runOnUiThread { handleDetected(barcodes.firstOrNull()?.rawValue) }
                    return@addOnSuccessListener
                }

                val camW = image.width.toFloat()
                val camH = image.height.toFloat()

                // Map normalized screen rect → camera pixel rect, accounting for rotation
                val acceptRect = when (rotationDegrees) {
                    90, 270 -> android.graphics.RectF(
                        normRect.left   * camH,
                        normRect.top    * camW,
                        normRect.right  * camH,
                        normRect.bottom * camW
                    )
                    else -> android.graphics.RectF(
                        normRect.left   * camW,
                        normRect.top    * camH,
                        normRect.right  * camW,
                        normRect.bottom * camH
                    )
                }

                Log.d("Scanner", "acceptRect=$acceptRect")

                val detected = barcodes.firstOrNull { barcode ->
                    val box = barcode.boundingBox ?: return@firstOrNull false
                    val bx = box.exactCenterX()
                    val by = box.exactCenterY()
                    Log.d("Scanner", "  '${barcode.rawValue}' center=($bx,$by) inside=${acceptRect.contains(bx, by)}")
                    acceptRect.contains(bx, by)
                }?.rawValue

                activity?.runOnUiThread { handleDetected(detected) }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessingFrame = false
            }
    }

    private fun handleDetected(detected: String?) {
        if (detected != null) {
            if (detected == candidateCode) {
                consecutiveHits++
                if (consecutiveHits >= REQUIRED_HITS) onBarcodeLocked(detected)
            } else {
                candidateCode   = detected
                consecutiveHits = 1
            }
        } else {
            candidateCode   = null
            consecutiveHits = 0
        }
    }
}