package com.example.employeepos2

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.*
import android.text.*
import android.text.method.DigitsKeyListener
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import android.graphics.drawable.GradientDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.example.employeepos2.model.DoorState
import com.example.employeepos2.model.SiteCheckConfig
import com.example.employeepos2.model.PhotoTarget
import com.example.employeepos2.databinding.ActivitySiteCheckBinding
import com.example.employeepos2.viewmodel.SiteCheckViewModel
import com.example.employeepos2.viewmodel.SubmitState

@SuppressLint("SetTextI18n")
class SiteCheckActivity : BaseActivity() {

    companion object {
        private val NOT_OK_LIST   = listOf("Adj", "Rep", "Repl")
        private val REP_REPL_LIST = listOf("Rep", "Repl")
    }

    private lateinit var binding: ActivitySiteCheckBinding
    private lateinit var config: SiteCheckConfig
    private lateinit var viewModel: SiteCheckViewModel

    private val doors = mutableListOf<DoorState>()
    private var currentDoorIndex = 0
    private var activePhotoTarget = ""
    private var tempCameraUri: Uri? = null
    private var isLoadingState = false

    // 🔥 Generates a unique ID for this specific session!
    private var currentDraftId: String = java.util.UUID.randomUUID().toString()

    private data class TextBinding(
        val view: EditText,
        val getter: (DoorState) -> String,
        val setter: (DoorState, String) -> Unit
    )

    private data class SingleChipBinding(
        val group: ChipGroup,
        val getter: (DoorState) -> String,
        val setter: (DoorState, String) -> Unit
    )

    private data class MultiChipBinding(
        val group: ChipGroup,
        val getter: (DoorState) -> List<String>,
        val setter: (DoorState, List<String>) -> Unit
    )

    private data class RepairRow(
        val chipGroup: ChipGroup,
        val detailLayout: View,
        val repReplLayout: View? = null,
        val getter: (DoorState) -> String,
        val setter: (DoorState, String) -> Unit
    )

    private lateinit var textBindings: List<TextBinding>
    private lateinit var singleChipBindings: List<SingleChipBinding>
    private lateinit var multiChipBindings: List<MultiChipBinding>
    private lateinit var repairRows: List<RepairRow>

    private val pickMultiplePhotosLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris.forEach { processAndAddPhoto(it) }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            }
        }

    private fun launchCamera() {
        tempCameraUri = createTempImageUri()
        takePhotoLauncher.launch(tempCameraUri!!)
    }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) processAndAddPhoto(tempCameraUri!!)
        }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySiteCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[SiteCheckViewModel::class.java]

        binding.incConcrete.overlayConcreteRight.drawMode = 1
        binding.incConcrete.overlayConcreteLeft.drawMode  = 2
        binding.incDoor.measurementDrawing.bringToFront()

        buildBindings()
        applyChipColors()
        setupTextFormatting()
        setupClickListeners()
        setupObservers()

        // 🔥 INITIALIZATION LOGIC
        val resumeDraftId = intent.getStringExtra("RESUME_DRAFT_ID")

        if (resumeDraftId != null) {
            // WE ARE RESUMING A SAVED DRAFT!
            currentDraftId = resumeDraftId
            val prefs = getSharedPreferences("SiteCheckDrafts", Context.MODE_PRIVATE)
            val jsonString = prefs.getString("draft_$currentDraftId", null)

            if (jsonString != null) {
                val saveState = Gson().fromJson(jsonString, DraftSaveState::class.java)

                // Rebuild the config and doors from the saved string
                config = Gson().fromJson(saveState.configJson, SiteCheckConfig::class.java)

                val doorsType = object : TypeToken<MutableList<DoorState>>() {}.type
                val savedDoors: MutableList<DoorState> = Gson().fromJson(saveState.doorsJson, doorsType)
                doors.clear()
                doors.addAll(savedDoors)
            } else {
                loadFreshConfig()
            }
        } else {
            // NEW SITE CHECK
            loadFreshConfig()
        }

        setupConditionalVisibility()
        renderTabs()
        loadDoorState(0)
    }

    private fun loadFreshConfig() {
        config = SiteCheckConfig.fromExtras(
            jobNum          = intent.getStringExtra("SF_JOB_NUM"),
            techName        = intent.getStringExtra("TECH_NAME"),
            customerName    = intent.getStringExtra("SF_CUSTOMER_NAME"),
            location        = intent.getStringExtra("LOCATION"),
            city            = intent.getStringExtra("CITY"),
            present         = intent.getStringExtra("PRESENT"),
            lift            = intent.getStringExtra("LIFT"),
            replacing       = intent.getStringExtra("REPLACING"),
            concreteProblem = intent.getStringExtra("CONCRETE_PROBLEM"),
            checkTypeRaw    = intent.getStringExtra("CHECK_TYPE")
        )
        doors.add(DoorState())
    }

    private fun buildBindings() {
        textBindings = buildTextBindings()
        singleChipBindings = buildSingleChipBindings()
        multiChipBindings = buildMultiChipBindings()
        repairRows = buildRepairRows()
    }

    private fun buildTextBindings() = with(binding) {
        buildList {
            with(incDoor) {
                add(TextBinding(etWidth,         { it.width },     { d, v -> d.width     = v }))
                add(TextBinding(etHeight,        { it.height },    { d, v -> d.height    = v }))
                add(TextBinding(etHeadroom,      { it.headroom },  { d, v -> d.headroom  = v }))
                add(TextBinding(etLeftSide,      { it.leftSide },  { d, v -> d.leftSide  = v }))
                add(TextBinding(etRightSide,     { it.rightSide }, { d, v -> d.rightSide = v }))
                add(TextBinding(etBackroom,      { it.backroom },  { d, v -> d.backroom  = v }))
                add(TextBinding(etExistingBrand, { it.exBrand },   { d, v -> d.exBrand   = v }))
                add(TextBinding(etExistingModel, { it.exModel },   { d, v -> d.exModel   = v }))
                add(TextBinding(etExistingColor, { it.exColor },   { d, v -> d.exColor   = v }))
                add(TextBinding(etDoorNotes,     { it.doorNotes }, { d, v -> d.doorNotes = v }))
            }
            with(incConcrete) {
                add(TextBinding(etConcreteNotes,       { it.concreteNotes }, { d, v -> d.concreteNotes = v }))
                add(TextBinding(etConcreteDepthLeft,   { it.cDepthL },       { d, v -> d.cDepthL       = v }))
                add(TextBinding(etConcreteDepthRight,  { it.cDepthR },       { d, v -> d.cDepthR       = v }))
                add(TextBinding(etConcreteWidthLeft,   { it.cWidthL },       { d, v -> d.cWidthL       = v }))
                add(TextBinding(etConcreteWidthRight,  { it.cWidthR },       { d, v -> d.cWidthR       = v }))
                add(TextBinding(etConcreteLengthLeft,  { it.cLengthL },      { d, v -> d.cLengthL      = v }))
                add(TextBinding(etConcreteLengthRight, { it.cLengthR },      { d, v -> d.cLengthR      = v }))
            }
            with(incOperator) {
                add(TextBinding(etOpBrand, { it.opBrand }, { d, v -> d.opBrand = v }))
                add(TextBinding(etOpModel, { it.opModel }, { d, v -> d.opModel = v }))
            }
            with(incInspection) {
                add(TextBinding(etDoorBalanceNotes, { it.doorBalanceNotes }, { d, v -> d.doorBalanceNotes = v }))
                add(TextBinding(etSecNum,           { it.secNum },           { d, v -> d.secNum           = v }))
                add(TextBinding(etSecWidth,         { it.secWidth },         { d, v -> d.secWidth         = v }))
                add(TextBinding(etSecHeight,        { it.secHeight },        { d, v -> d.secHeight        = v }))
                add(TextBinding(etSecGlass,         { it.secGlass },         { d, v -> d.secGlass         = v }))
                add(TextBinding(etSecNotes,         { it.secNotes },         { d, v -> d.secNotes         = v }))
                add(TextBinding(etStrutQty,         { it.strutQty },         { d, v -> d.strutQty         = v }))
                add(TextBinding(etStrutNotes,       { it.strutNotes },       { d, v -> d.strutNotes       = v }))
                add(TextBinding(etHorizontalLength, { it.horizontalLength }, { d, v -> d.horizontalLength = v }))
                add(TextBinding(etVerticalLength,   { it.verticalLength },   { d, v -> d.verticalLength   = v }))
                add(TextBinding(etTrackNotes,       { it.trackNotes },       { d, v -> d.trackNotes       = v }))
                add(TextBinding(etSpringWireSize,   { it.springWireSize },   { d, v -> d.springWireSize   = v }))
                add(TextBinding(etSpringId,         { it.springId },         { d, v -> d.springId         = v }))
                add(TextBinding(etSpringLength,     { it.springLength },     { d, v -> d.springLength     = v }))
                add(TextBinding(etSpringNotes,      { it.springNotes },      { d, v -> d.springNotes      = v }))

                add(TextBinding(etDrumModel,        { it.drumModel },        { d, v -> d.drumModel        = v }))
                add(TextBinding(etDrumFrontDiameter,{ it.drumFrontDiameter },{ d, v -> d.drumFrontDiameter = v }))
                add(TextBinding(etDrumBackDiameter, { it.drumBackDiameter }, { d, v -> d.drumBackDiameter  = v }))
                add(TextBinding(etDrumNotes,        { it.drumNotes },        { d, v -> d.drumNotes        = v }))

                add(TextBinding(etCableDiameter,    { it.cableDiameter },    { d, v -> d.cableDiameter    = v }))
                add(TextBinding(etCableLength,      { it.cableLength },      { d, v -> d.cableLength      = v }))
                add(TextBinding(etCableNotes,       { it.cableNotes },       { d, v -> d.cableNotes       = v }))
                add(TextBinding(etShaftNotes,       { it.shaftNotes },       { d, v -> d.shaftNotes       = v }))
                add(TextBinding(etPlateNotes,       { it.plateNotes },       { d, v -> d.plateNotes       = v }))
                add(TextBinding(etHingeType,        { it.hingeType },        { d, v -> d.hingeType        = v }))
                add(TextBinding(etHingeQty,         { it.hingeQty },         { d, v -> d.hingeQty         = v }))
                add(TextBinding(etHingeNotes,       { it.hingeNotes },       { d, v -> d.hingeNotes       = v }))

                add(TextBinding(etRollerShortQty,   { it.rollerShortQty },   { d, v -> d.rollerShortQty   = v }))
                add(TextBinding(etRollerLongQty,    { it.rollerLongQty },    { d, v -> d.rollerLongQty    = v }))
                add(TextBinding(etRollerNotes,      { it.rollerNotes },      { d, v -> d.rollerNotes      = v }))

                add(TextBinding(etBracketNotes,     { it.bracketNotes },     { d, v -> d.bracketNotes     = v }))

                add(TextBinding(etSealColor,        { it.sealColor },        { d, v -> d.sealColor        = v }))
                add(TextBinding(etSealNotes,        { it.sealNotes },        { d, v -> d.sealNotes        = v }))

                add(TextBinding(etMotorNotes,       { it.motorNotes },       { d, v -> d.motorNotes       = v }))
                add(TextBinding(etLimitsNotes,      { it.limitsNotes },      { d, v -> d.limitsNotes      = v }))
                add(TextBinding(etForceNotes,       { it.forceNotes },       { d, v -> d.forceNotes       = v }))
                add(TextBinding(etSrNotes,          { it.srNotes },          { d, v -> d.srNotes          = v }))
                add(TextBinding(etChainNotes,       { it.chainNotes },       { d, v -> d.chainNotes       = v }))
                add(TextBinding(etWsModel,          { it.wsModel },          { d, v -> d.wsModel          = v }))
                add(TextBinding(etWsNotes,          { it.wsNotes },          { d, v -> d.wsNotes          = v }))
                add(TextBinding(etPeNotes,          { it.peNotes },          { d, v -> d.peNotes          = v }))
                add(TextBinding(etLcNotes,          { it.lcNotes },          { d, v -> d.lcNotes          = v }))
                add(TextBinding(etSeNotes,          { it.seNotes },          { d, v -> d.seNotes          = v }))
            }
        }
    }

    private fun buildSingleChipBindings() = with(binding) {
        buildList {
            with(incDoor) {
                add(SingleChipBinding(rgMountTo,   { it.mountTo },  { d, v -> d.mountTo  = v }))
                add(SingleChipBinding(rgFramed,    { it.framed },   { d, v -> d.framed   = v }))
                add(SingleChipBinding(rgVoltage,   { it.voltage },  { d, v -> d.voltage  = v }))
                add(SingleChipBinding(rgTrackSize, { it.exTrack },  { d, v -> d.exTrack  = v }))
            }
            with(incConcrete) {
                add(SingleChipBinding(rgConcreteReady, { it.concreteReady }, { d, v -> d.concreteReady = v }))
            }
            with(incOperator) {
                add(SingleChipBinding(rgOpFramed,       { it.opFramed },       { d, v -> d.opFramed       = v }))
                add(SingleChipBinding(rgOpMount,        { it.opMount },        { d, v -> d.opMount        = v }))
                add(SingleChipBinding(rgOpVoltage,      { it.opVoltage },      { d, v -> d.opVoltage      = v }))
                add(SingleChipBinding(rgOpPhase,        { it.opPhase },        { d, v -> d.opPhase        = v }))
                add(SingleChipBinding(rgOpHP,           { it.opHP },           { d, v -> d.opHP           = v }))
                add(SingleChipBinding(rgOpWallStation,  { it.opWallStation },  { d, v -> d.opWallStation  = v }))
                add(SingleChipBinding(rgOpWallLocation, { it.opWallLocation }, { d, v -> d.opWallLocation = v }))
                add(SingleChipBinding(rgOpRemotes,      { it.opRemotes },      { d, v -> d.opRemotes      = v }))
                add(SingleChipBinding(rgOpKeypad,       { it.opKeypad },       { d, v -> d.opKeypad       = v }))
            }
            with(incInspection) {
                add(SingleChipBinding(rgSpringType,     { it.springType },    { d, v -> d.springType    = v }))
                add(SingleChipBinding(rgHingeGauge,     { it.hingeGauge },    { d, v -> d.hingeGauge    = v }))
                add(SingleChipBinding(rgRollerMaterial, { it.rollerMaterial },{ d, v -> d.rollerMaterial= v }))

                add(SingleChipBinding(rgSealThickness,  { it.sealThickness }, { d, v -> d.sealThickness = v }))
                add(SingleChipBinding(rgPlateSize,      { it.plateSize },     { d, v -> d.plateSize     = v }))
            }
        }
    }

    private fun buildMultiChipBindings() = buildList {
        with(binding.incOperator) {
            add(MultiChipBinding(rgOpSafety,
                { it.opSafetyList }, { d, v -> d.opSafetyList = v.toMutableList() }))
        }
        with(binding.incInspection) {
            add(MultiChipBinding(rgSpringWind,
                { it.springWindList }, { d, v -> d.springWindList = v.toMutableList() }))
            add(MultiChipBinding(rgPlateLocation,
                { it.plateLocationList }, { d, v -> d.plateLocationList = v.toMutableList() }))
            add(MultiChipBinding(rgSealLocation,
                { it.sealLocationList }, { d, v -> d.sealLocationList = v.toMutableList() }))
            add(MultiChipBinding(rgTrackHorizontal,
                { it.trackHorizontalList }, { d, v -> d.trackHorizontalList = v.toMutableList() }))
            add(MultiChipBinding(rgTrackVertical,
                { it.trackVerticalList }, { d, v -> d.trackVerticalList = v.toMutableList() }))
            add(MultiChipBinding(rgShaftSize,
                { it.shaftSizeList }, { d, v -> d.shaftSizeList = v.toMutableList() }))
            add(MultiChipBinding(rgShaftType,
                { it.shaftTypeList }, { d, v -> d.shaftTypeList = v.toMutableList() }))
            add(MultiChipBinding(rgSealType,
                { it.sealTypeList }, { d, v -> d.sealTypeList = v.toMutableList() }))
            add(MultiChipBinding(rgBracketType,
                { it.bracketType }, { d, v -> d.bracketType = v.toMutableList() }))
        }
    }

    private fun buildRepairRows() = with(binding.incInspection) {
        listOf(
            RepairRow(rgStatDoorBalance,   llDoorBalanceDetails,   null,               { it.statDoorBalance   }, { d, v -> d.statDoorBalance   = v }),
            RepairRow(rgStatSections,      llSectionRepairDetails, llSecRepReplFields, { it.statSections      }, { d, v -> d.statSections      = v }),
            RepairRow(rgStatStruts,        llStrutRepairDetails,   llStrutRepReplFields,{ it.statStruts        }, { d, v -> d.statStruts        = v }),
            RepairRow(rgStatTrack,         llTrackRepairDetails,   llTrackRepReplFields,{ it.statTrack         }, { d, v -> d.statTrack         = v }),
            RepairRow(rgStatSprings,       llSpringRepairDetails,  llSpringRepReplFields,{ it.statSprings      }, { d, v -> d.statSprings       = v }),
            RepairRow(rgStatDrums,         llDrumRepairDetails,    llDrumRepReplFields, { it.statDrums         }, { d, v -> d.statDrums         = v }),
            RepairRow(rgStatCables,        llCableRepairDetails,   llCableRepReplFields,{ it.statCables        }, { d, v -> d.statCables        = v }),
            RepairRow(rgStatBearingsShaft, llShaftRepairDetails,   llShaftRepReplFields,{ it.statBearingsShaft}, { d, v -> d.statBearingsShaft = v }),
            RepairRow(rgStatBearingPlates, llPlateRepairDetails,   llPlateRepReplFields,{ it.statBearingPlates}, { d, v -> d.statBearingPlates = v }),
            RepairRow(rgStatHinges,        llHingeRepairDetails,   llHingeRepReplFields,{ it.statHinges        }, { d, v -> d.statHinges        = v }),
            RepairRow(rgStatRollers,       llRollerRepairDetails,  llRollerRepReplFields,{ it.statRollers      }, { d, v -> d.statRollers       = v }),
            RepairRow(rgStatBrackets,      llBracketRepairDetails, llBracketRepReplFields,{ it.statBrackets    }, { d, v -> d.statBrackets      = v }),
            RepairRow(rgStatWeatherseal,   llSealRepairDetails,    llSealRepReplFields, { it.statWeatherseal  }, { d, v -> d.statWeatherseal   = v }),
            RepairRow(rgStatMotor,         llMotorRepairDetails,   llMotorRepReplFields,{ it.statMotor         }, { d, v -> d.statMotor         = v }),
            RepairRow(rgStatLimits,        llLimitsDetails,        null,               { it.statLimits         }, { d, v -> d.statLimits        = v }),
            RepairRow(rgStatForceSettings, llForceDetails,         null,               { it.statForceSettings }, { d, v -> d.statForceSettings = v }),
            RepairRow(rgStatSafetyReverse, llSrDetails,            null,               { it.statSafetyReverse }, { d, v -> d.statSafetyReverse = v }),
            RepairRow(rgStatChainBelt,     llChainRepairDetails,   llChainRepReplFields,{ it.statChainBelt    }, { d, v -> d.statChainBelt     = v }),
            RepairRow(rgStatWallStation,   llWsRepairDetails,      llWsRepReplFields,   { it.statWallStation  }, { d, v -> d.statWallStation   = v }),
            RepairRow(rgStatPhotoEyes,     llPeRepairDetails,      llPeRepReplFields,   { it.statPhotoEyes    }, { d, v -> d.statPhotoEyes     = v }),
            RepairRow(rgStatLightCurtain,  llLcRepairDetails,      llLcRepReplFields,   { it.statLightCurtain }, { d, v -> d.statLightCurtain  = v }),
            RepairRow(rgStatSensingEdge,   llSeRepairDetails,      llSeRepReplFields,   { it.statSensingEdge  }, { d, v -> d.statSensingEdge   = v }),
        )
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.submitState.collect { state ->
                when (state) {
                    is SubmitState.Idle    -> Unit
                    is SubmitState.Loading -> {
                        binding.siteLoader.visibility = View.VISIBLE
                        binding.btnSubmitAllDoors.isEnabled = false
                    }
                    is SubmitState.Success -> {
                        // 🔥 Wipe ONLY this specific draft on successful submit!
                        getSharedPreferences("SiteCheckDrafts", Context.MODE_PRIVATE)
                            .edit()
                            .remove("draft_$currentDraftId")
                            .apply()

                        binding.siteLoader.visibility = View.GONE
                        Toast.makeText(this@SiteCheckActivity, "Submitted ${doors.size} door(s)", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SiteCheckActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    }
                    is SubmitState.Error -> {
                        binding.siteLoader.visibility = View.GONE
                        binding.btnSubmitAllDoors.isEnabled = true
                        Toast.makeText(this@SiteCheckActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupConditionalVisibility() {
        with(binding.incDoor) {
            tvPhotoInstruction1.text    = config.photoInstruction
            llExistingDoorInfo.visibility  = if (config.showExistingDoorInfo)    View.VISIBLE else View.GONE
            tvDoorTagInstruction.visibility = if (config.showDoorTagInstruction) View.VISIBLE else View.GONE
            llDoorNotesAndPhotos.visibility = if (config.showDoorNotesAndPhotos) View.VISIBLE else View.GONE
        }
        binding.incOperator.root.visibility   = if (config.showOperatorSection)   View.VISIBLE else View.GONE
        binding.incInspection.root.visibility = if (config.showInspectionSection) View.VISIBLE else View.GONE
        binding.incConcrete.root.visibility   = if (config.showConcreteSection)   View.VISIBLE else View.GONE
    }

    private fun setupClickListeners() {
        setupRepairListeners()

        with(binding.incInspection) {
            rgSealType.setOnCheckedStateChangeListener { group, _ ->
                val selections = getMultiChipValues(group)
                llSealThickness.visibility =
                    if (selections.any { it.contains("Bottom Seal") }) View.VISIBLE else View.GONE
            }

            btnAddSection.setOnClickListener { addSectionEntry() }
            btnAddSpring.setOnClickListener  { addSpringEntry()  }
            btnAddHinge.setOnClickListener   { addHingeEntry()   }
        }

        mapOf(
            PhotoTarget.DOOR     to binding.incDoor.btnDoorPhoto,
            PhotoTarget.CONCRETE to binding.incConcrete.btnConcretePhoto,
            PhotoTarget.SECTION  to binding.incInspection.btnSecPhoto,
            PhotoTarget.TRACK    to binding.incInspection.btnTrackPhoto,
            PhotoTarget.SPRING   to binding.incInspection.btnSpringPhoto,
            PhotoTarget.DRUM     to binding.incInspection.btnDrumPhoto,
            PhotoTarget.CABLE    to binding.incInspection.btnCablePhoto,
            PhotoTarget.HINGE    to binding.incInspection.btnHingePhoto,
            PhotoTarget.ROLLER   to binding.incInspection.btnRollerPhoto,
            PhotoTarget.BRACKET  to binding.incInspection.btnBracketPhoto,
            PhotoTarget.SEAL     to binding.incInspection.btnSealPhoto,
            PhotoTarget.MOTOR    to binding.incInspection.btnMotorPhoto,
            PhotoTarget.CHAIN    to binding.incInspection.btnChainPhoto,
            PhotoTarget.WS       to binding.incInspection.btnWsPhoto,
            PhotoTarget.PE       to binding.incInspection.btnPePhoto,
            PhotoTarget.LC       to binding.incInspection.btnLcPhoto,
            PhotoTarget.SE       to binding.incInspection.btnSePhoto,
        ).forEach { (target, button) ->
            button.setOnClickListener {
                activePhotoTarget = target.name
                showPhotoOptionsDialog()
            }
        }

        binding.btnAddNewDoorTab.setOnClickListener  { addNewDoorTab() }
        binding.btnDeleteDoor.setOnClickListener     { deleteCurrentDoor() }

        // 🔥 SAVE DRAFT LOGIC WITH 3-LIMIT CHECK
        binding.btnSaveDraft.setOnClickListener {
            val prefs = getSharedPreferences("SiteCheckDrafts", Context.MODE_PRIVATE)
            val draftKeys = prefs.all.keys.filter { it.startsWith("draft_") }

            // Check if this is a brand new draft, and if they already have 3 drafts
            if (!draftKeys.contains("draft_$currentDraftId") && draftKeys.size >= 3) {
                showDraftLimitErrorDialog()
                return@setOnClickListener
            }

            saveCurrentDoorState()

            val saveState = DraftSaveState(
                draftId = currentDraftId,
                jobNum = config.jobNum, // (If this also gives an error, change it to: intent.getStringExtra("SF_JOB_NUM") ?: "")
                checkType = intent.getStringExtra("CHECK_TYPE") ?: "SITE CHECK", // 🔥 FIXED: Grabs directly from the intent!
                configJson = Gson().toJson(config),
                doorsJson = Gson().toJson(doors),
                timestamp = System.currentTimeMillis()
            )

            prefs.edit().putString("draft_$currentDraftId", Gson().toJson(saveState)).apply()

            Toast.makeText(this, "Draft saved securely!", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnSubmitAllDoors.setOnClickListener {
            showSubmitConfirmationDialog()
        }
    }

    private fun showDraftLimitErrorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_draft_limit, null)
        val btnOkay = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOkay)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOkay.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showSubmitConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_submit, null)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelSubmit)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmSubmit)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            saveCurrentDoorState()
            viewModel.submit(this@SiteCheckActivity, config, doors)
        }

        dialog.show()
    }

    private fun setupRepairListeners() {
        repairRows.forEach { row ->
            row.chipGroup.setOnCheckedStateChangeListener { _, _ ->
                val sel = getSingleChipValue(row.chipGroup)
                row.detailLayout.visibility   = sel.visibleIf(NOT_OK_LIST)
                row.repReplLayout?.visibility = sel.visibleIf(REP_REPL_LIST)
            }
        }
    }

    private fun addSectionEntry() = with(binding.incInspection) {
        val sNum    = etSecNum.text.toString().trim()
        val sWidth  = etSecWidth.text.toString().trim()
        val sHeight = etSecHeight.text.toString().trim()
        val sGlass  = etSecGlass.text.toString().trim()

        if (sNum.isBlank() && sWidth.isBlank() && sHeight.isBlank()) {
            Toast.makeText(this@SiteCheckActivity, "Please enter Section dimensions first.", Toast.LENGTH_SHORT).show()
            return@with
        }

        val wStr = if (sWidth.isNotBlank()) {
            if (sWidth.endsWith("\"") || sWidth.endsWith("'")) "${sWidth}W" else "${sWidth}\"W"
        } else ""

        val hStr = if (sHeight.isNotBlank()) {
            if (sHeight.endsWith("\"") || sHeight.endsWith("'")) "${sHeight}H" else "${sHeight}\"H"
        } else ""

        var entry = "Section $sNum:"

        if (wStr.isNotBlank() && hStr.isNotBlank()) entry += " $wStr x $hStr"
        else if (wStr.isNotBlank()) entry += " $wStr"
        else if (hStr.isNotBlank()) entry += " $hStr"

        if (sGlass.isNotBlank()) entry += " (Glass: $sGlass)"

        appendNotes(etSecNotes, entry)
        listOf(etSecNum, etSecWidth, etSecHeight, etSecGlass).forEach { it.text?.clear() }
        Toast.makeText(this@SiteCheckActivity, "Section added to notes!", Toast.LENGTH_SHORT).show()
    }

    private fun addSpringEntry() = with(binding.incInspection) {
        val sWire = etSpringWireSize.text.toString().trim()
        val sId   = etSpringId.text.toString().trim()
        val sLen  = etSpringLength.text.toString().trim()

        if (sWire.isBlank() && sId.isBlank() && sLen.isBlank()) {
            Toast.makeText(this@SiteCheckActivity, "Please enter Spring details first.", Toast.LENGTH_SHORT).show()
            return
        }

        val sType = getSingleChipValue(rgSpringType)
        val sWind = getMultiChipValues(rgSpringWind).joinToString(", ")

        var entry = "Spring:"
        if (sType != "N/A")       entry += " $sType"
        if (sWire.isNotBlank())   entry += " | Wire: $sWire"
        if (sId.isNotBlank())     entry += " | ID: $sId"
        if (sLen.isNotBlank())    entry += " | Length: $sLen"
        if (sWind.isNotBlank())   entry += " | Wind: $sWind"

        appendNotes(etSpringNotes, entry)
        listOf(etSpringWireSize, etSpringId, etSpringLength).forEach { it.text?.clear() }
        rgSpringType.clearCheck()
        rgSpringWind.clearCheck()
        Toast.makeText(this@SiteCheckActivity, "Spring added to notes!", Toast.LENGTH_SHORT).show()
    }

    private fun addHingeEntry() = with(binding.incInspection) {
        val hType  = etHingeType.text.toString().trim()
        val hQty   = etHingeQty.text.toString().trim()

        if (hType.isBlank() && hQty.isBlank()) {
            Toast.makeText(this@SiteCheckActivity, "Please enter Hinge details first.", Toast.LENGTH_SHORT).show()
            return
        }

        val hGauge = getSingleChipValue(rgHingeGauge)
        var entry  = "Hinge:"
        if (hType.isNotBlank())  entry += " Type #$hType"
        if (hQty.isNotBlank())   entry += " | Qty: $hQty"
        if (hGauge != "N/A")     entry += " | Gauge: $hGauge"

        appendNotes(etHingeNotes, entry)
        listOf(etHingeType, etHingeQty).forEach { it.text?.clear() }
        rgHingeGauge.clearCheck()
        Toast.makeText(this@SiteCheckActivity, "Hinge added to notes!", Toast.LENGTH_SHORT).show()
    }

    private fun appendNotes(et: EditText, line: String) {
        val current = et.text.toString().trim()
        et.setText(if (current.isBlank()) line else "$current\n$line")
    }

    private fun switchDoor(newIndex: Int) {
        if (currentDoorIndex == newIndex) return
        saveCurrentDoorState()
        currentDoorIndex = newIndex
        loadDoorState(newIndex)
        renderTabs()
    }

    private fun addNewDoorTab() {
        saveCurrentDoorState()
        doors.add(DoorState())
        currentDoorIndex = doors.size - 1
        loadDoorState(currentDoorIndex)
        renderTabs()
    }

    private fun deleteCurrentDoor() {
        if (doors.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle("Delete Door ${currentDoorIndex + 1}?")
            .setMessage("This will permanently remove this door's measurements and photos.")
            .setPositiveButton("Delete") { _, _ ->
                doors.removeAt(currentDoorIndex)
                currentDoorIndex = if (currentDoorIndex > 0) currentDoorIndex - 1 else 0
                loadDoorState(currentDoorIndex)
                renderTabs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderTabs() {
        binding.llDoorTabs.removeAllViews()
        doors.indices.forEach { i ->
            val isActive = i == currentDoorIndex
            val tab = TextView(this).apply {
                text     = "DOOR ${i + 1}"
                textSize = 14f
                setPadding(48, 32, 48, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }

                setTextColor(if (isActive) Color.parseColor("#CC0007") else Color.parseColor("#6B7280"))
                setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

                val bgColor     = if (isActive) "#FEE2E2" else "#F3F4F6"
                val strokeColor = if (isActive) "#CC0007" else "#D1D5DB"
                background = GradientDrawable().apply {
                    shape       = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(bgColor))
                    cornerRadii = floatArrayOf(24f, 24f, 24f, 24f, 0f, 0f, 0f, 0f)
                    setStroke(if (isActive) 3 else 2, Color.parseColor(strokeColor))
                }
                setOnClickListener { switchDoor(i) }
            }
            binding.llDoorTabs.addView(tab)
        }
        binding.btnDeleteDoor.visibility = if (doors.size > 1) View.VISIBLE else View.GONE
    }

    private fun saveCurrentDoorState() {
        val door = doors[currentDoorIndex]
        textBindings.forEach       { it.setter(door, it.view.text.toString()) }
        singleChipBindings.forEach { it.setter(door, getSingleChipValue(it.group)) }
        multiChipBindings.forEach  { it.setter(door, getMultiChipValues(it.group)) }
        repairRows.forEach         { it.setter(door, getSingleChipValue(it.chipGroup)) }
        door.drawingBase64 = getDrawingBase64()
    }

    private fun loadDoorState(index: Int) {
        try {
            isLoadingState = true
            val door = doors[index]

            textBindings.forEach       { it.view.setText(it.getter(door)) }
            singleChipBindings.forEach { setSingleChipValue(it.group, it.getter(door)) }
            multiChipBindings.forEach  { setMultiChipValues(it.group, it.getter(door)) }

            repairRows.forEach { row ->
                val status = row.getter(door)
                setSingleChipValue(row.chipGroup, status)
                row.detailLayout.visibility   = status.visibleIf(NOT_OK_LIST)
                row.repReplLayout?.visibility = status.visibleIf(REP_REPL_LIST)
            }

            binding.incInspection.llSealThickness.visibility =
                if (door.sealTypeList.any { it.contains("Bottom Seal") }) View.VISIBLE else View.GONE

            PhotoTarget.entries.forEach { target ->
                containerFor(target)?.let { container ->
                    renderUriThumbnails(doors[index].photoUrisFor(target), container)
                }
            }
        } finally {
            isLoadingState = false
        }

        with(binding.incDoor.measurementDrawing) {
            val door = doors[index]
            showWidthLine  = door.width.isNotEmpty()
            showHeightLine = door.height.isNotEmpty()
            showHeadLine   = door.headroom.isNotEmpty()
            showLeftLine   = door.leftSide.isNotEmpty()
            showRightLine  = door.rightSide.isNotEmpty()
            invalidate()
        }
    }

    private fun getSingleChipValue(group: ChipGroup): String {
        val id = group.checkedChipId
        return if (id != View.NO_ID) group.findViewById<Chip>(id)?.text?.toString() ?: "N/A" else "N/A"
    }

    private fun getMultiChipValues(group: ChipGroup): MutableList<String> =
        (0 until group.childCount)
            .mapNotNull { group.getChildAt(it) as? Chip }
            .filter { it.isChecked }
            .mapTo(mutableListOf()) { it.text.toString() }

    private fun setSingleChipValue(group: ChipGroup, value: String) {
        group.clearCheck()
        (0 until group.childCount)
            .mapNotNull { group.getChildAt(it) as? Chip }
            .firstOrNull { it.text.toString() == value }
            ?.isChecked = true
    }

    private fun setMultiChipValues(group: ChipGroup, values: List<String>) {
        group.clearCheck()
        (0 until group.childCount)
            .mapNotNull { group.getChildAt(it) as? Chip }
            .forEach { it.isChecked = it.text.toString() in values }
    }

    private fun applyChipColors() {
        val integrityRed = Color.parseColor("#CC0007")
        val white        = Color.WHITE
        val blackText    = Color.parseColor("#1A1C1E")

        val bgStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(integrityRed, white)
        )
        val textStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(white, blackText)
        )
        val strokeStateList = ColorStateList.valueOf(integrityRed)

        val allGroups = listOf(
            binding.incDoor.rgMountTo, binding.incDoor.rgFramed,
            binding.incDoor.rgVoltage, binding.incDoor.rgTrackSize,
            binding.incConcrete.rgConcreteReady,

            binding.incOperator.rgOpFramed, binding.incOperator.rgOpMount,
            binding.incOperator.rgOpVoltage, binding.incOperator.rgOpPhase, binding.incOperator.rgOpHP,
            binding.incOperator.rgOpSafety, binding.incOperator.rgOpWallStation,
            binding.incOperator.rgOpWallLocation, binding.incOperator.rgOpRemotes,
            binding.incOperator.rgOpKeypad,

            binding.incInspection.rgStatDoorBalance, binding.incInspection.rgStatSections,
            binding.incInspection.rgStatStruts, binding.incInspection.rgStatTrack,
            binding.incInspection.rgStatSprings, binding.incInspection.rgStatDrums,
            binding.incInspection.rgStatCables, binding.incInspection.rgStatBearingsShaft,
            binding.incInspection.rgStatBearingPlates, binding.incInspection.rgStatHinges,
            binding.incInspection.rgStatRollers, binding.incInspection.rgStatBrackets,
            binding.incInspection.rgStatWeatherseal, binding.incInspection.rgStatMotor,
            binding.incInspection.rgStatLimits, binding.incInspection.rgStatForceSettings,
            binding.incInspection.rgStatSafetyReverse, binding.incInspection.rgStatChainBelt,
            binding.incInspection.rgStatWallStation, binding.incInspection.rgStatPhotoEyes,
            binding.incInspection.rgStatLightCurtain, binding.incInspection.rgStatSensingEdge,

            binding.incInspection.rgTrackHorizontal, binding.incInspection.rgTrackVertical,
            binding.incInspection.rgSpringType, binding.incInspection.rgSpringWind,
            binding.incInspection.rgShaftSize, binding.incInspection.rgShaftType,
            binding.incInspection.rgPlateSize, binding.incInspection.rgPlateLocation,
            binding.incInspection.rgHingeGauge, binding.incInspection.rgRollerMaterial,

            binding.incInspection.rgBracketType,

            binding.incInspection.rgSealType, binding.incInspection.rgSealLocation,
            binding.incInspection.rgSealThickness
        )

        for (cg in allGroups) {
            for (i in 0 until cg.childCount) {
                val chip = cg.getChildAt(i) as? Chip ?: continue
                chip.chipBackgroundColor = bgStateList
                chip.setTextColor(textStateList)
                chip.checkedIconTint     = ColorStateList.valueOf(white)
                chip.chipStrokeColor     = strokeStateList
                chip.chipStrokeWidth     = 3f
            }
        }
    }

    private fun setupTextFormatting() {
        val measurementFields = with(binding) {
            listOf(
                incDoor.etWidth, incDoor.etHeight, incDoor.etHeadroom,
                incDoor.etLeftSide, incDoor.etRightSide, incDoor.etBackroom,
                incInspection.etSecWidth,
                incInspection.etHorizontalLength, incInspection.etVerticalLength,
                incInspection.etDrumFrontDiameter, incInspection.etDrumBackDiameter,
                incInspection.etSpringLength, incInspection.etCableLength,
                incConcrete.etConcreteDepthLeft,  incConcrete.etConcreteWidthLeft,
                incConcrete.etConcreteLengthLeft, incConcrete.etConcreteDepthRight,
                incConcrete.etConcreteWidthRight, incConcrete.etConcreteLengthRight
            )
        }

        measurementFields.forEach { et ->
            et.keyListener = DigitsKeyListener.getInstance("0123456789'\" ")
            et.addTextChangedListener(object : TextWatcher {
                private var updating = false
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updating || s.isNullOrEmpty()) return
                    val d = s.toString().replace(Regex("\\D"), "")
                    if (d.isEmpty()) { updating = true; s.clear(); updating = false; return }
                    val f = if (d.length <= 2) "${d}'"
                    else "${d.substring(0, d.length - 2)}' ${d.substring(d.length - 2)}\""
                    if (f != s.toString()) {
                        updating = true
                        s.replace(0, s.length, f)
                        et.setSelection(f.length)
                        updating = false
                    }
                }
            })
        }

        with(binding.incDoor) {
            etWidth.doAfterTextChanged     { if (!isLoadingState) { measurementDrawing.showWidthLine  = !it.isNullOrEmpty(); measurementDrawing.invalidate() } }
            etHeight.doAfterTextChanged    { if (!isLoadingState) { measurementDrawing.showHeightLine = !it.isNullOrEmpty(); measurementDrawing.invalidate() } }
            etHeadroom.doAfterTextChanged  { if (!isLoadingState) { measurementDrawing.showHeadLine   = !it.isNullOrEmpty(); measurementDrawing.invalidate() } }
            etLeftSide.doAfterTextChanged  { if (!isLoadingState) { measurementDrawing.showLeftLine   = !it.isNullOrEmpty(); measurementDrawing.invalidate() } }
            etRightSide.doAfterTextChanged { if (!isLoadingState) { measurementDrawing.showRightLine  = !it.isNullOrEmpty(); measurementDrawing.invalidate() } }
        }
    }

    private fun getDrawingBase64(): String {
        val bmp = createBitmap(
            binding.incDoor.measurementDrawing.width,
            binding.incDoor.measurementDrawing.height,
            Bitmap.Config.ARGB_8888
        )
        binding.incDoor.measurementDrawing.draw(Canvas(bmp))
        val os = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        return Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
    }

    private fun showPhotoOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Add photos")
            .setItems(arrayOf("Take photo", "Choose from gallery")) { _, which ->
                if (which == 0) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        launchCamera()
                    } else {
                        requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                } else {
                    pickMultiplePhotosLauncher.launch("image/*")
                }
            }.show()
    }

    private fun createTempImageUri(): Uri {
        val tempFile = File.createTempFile("temp_camera_", ".jpg", cacheDir)
            .apply { createNewFile(); deleteOnExit() }
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val dir  = File(cacheDir, "sitecheck_photos").also { it.mkdirs() }
        val file = File(dir, "compressed_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return Uri.fromFile(file)
    }

    private fun processAndAddPhoto(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val original = contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) } ?: return@launch

                val rotation = contentResolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    ) {
                        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val maxDim = 1080f
                val scale  = minOf(maxDim / original.width, maxDim / original.height)
                val matrix = Matrix().apply {
                    if (scale < 1f)    postScale(scale, scale)
                    if (rotation != 0f) postRotate(rotation)
                }

                val final         = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                val compressedUri = saveBitmapToCache(final)

                withContext(Dispatchers.Main) {
                    val target = PhotoTarget.entries.firstOrNull { it.name == activePhotoTarget }
                        ?: return@withContext
                    doors[currentDoorIndex].photoUrisFor(target).add(compressedUri.toString())

                    containerFor(target)?.let {
                        renderUriThumbnails(doors[currentDoorIndex].photoUrisFor(target), it)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SiteCheckActivity, "Failed to load image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun containerFor(target: PhotoTarget): LinearLayout? = when (target) {
        PhotoTarget.DOOR     -> binding.incDoor.llDoorPhotos
        PhotoTarget.CONCRETE -> binding.incConcrete.llConcretePhotos
        PhotoTarget.SECTION  -> binding.incInspection.llSecPhotos
        PhotoTarget.TRACK    -> binding.incInspection.llTrackPhotos
        PhotoTarget.SPRING   -> binding.incInspection.llSpringPhotos
        PhotoTarget.DRUM     -> binding.incInspection.llDrumPhotos
        PhotoTarget.CABLE    -> binding.incInspection.llCablePhotos
        PhotoTarget.HINGE    -> binding.incInspection.llHingePhotos
        PhotoTarget.ROLLER   -> binding.incInspection.llRollerPhotos
        PhotoTarget.BRACKET  -> binding.incInspection.llBracketPhotos
        PhotoTarget.SEAL     -> binding.incInspection.llSealPhotos
        PhotoTarget.MOTOR    -> binding.incInspection.llMotorPhotos
        PhotoTarget.CHAIN    -> binding.incInspection.llChainPhotos
        PhotoTarget.WS       -> binding.incInspection.llWsPhotos
        PhotoTarget.PE       -> binding.incInspection.llPePhotos
        PhotoTarget.LC       -> binding.incInspection.llLcPhotos
        PhotoTarget.SE       -> binding.incInspection.llSePhotos
        else -> null
    }

    private fun renderUriThumbnails(uriList: List<String>, container: LinearLayout) {
        container.removeAllViews()
        uriList.forEach { uriString ->
            runCatching {
                container.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(0, 0, 16, 0) }
                    scaleType    = ImageView.ScaleType.CENTER_CROP
                    setImageURI(Uri.parse(uriString))
                })
            }
        }
    }

    private fun String.visibleIf(list: List<String>) =
        if (this in list) View.VISIBLE else View.GONE
}

// 🔥 THIS HOLDS EVERYTHING TOGETHER SECURELY
data class DraftSaveState(
    val draftId: String,
    val jobNum: String,
    val checkType: String,
    val configJson: String,
    val doorsJson: String,
    val timestamp: Long
)