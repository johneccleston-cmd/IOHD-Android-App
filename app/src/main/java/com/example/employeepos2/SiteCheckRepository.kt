package com.example.employeepos2.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.employeepos2.model.CheckType
import com.example.employeepos2.model.DoorState
import com.example.employeepos2.model.PhotoTarget
import com.example.employeepos2.model.SiteCheckConfig
import com.example.employeepos2.worker.SiteCheckSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SiteCheckRepository {

    private fun uriToBase64(context: Context, uriString: String): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uriString))
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP) else ""
        } catch (e: Exception) { "" }
    }

    suspend fun submitSiteCheck(
        context: Context,
        config: SiteCheckConfig,
        doors: List<DoorState>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val addressParts = config.location.split(",")
            val streetOnly = addressParts.firstOrNull()?.trim() ?: config.location
            val cityStateZip = if (addressParts.size > 1) {
                addressParts.drop(1).joinToString(",").trim()
            } else {
                "N/A"
            }

            val masterPayload = JSONObject().apply {
                put("jobNum", config.jobNum)
                put("techName", config.techName)
                put("customer_name", config.customerName)
                put("street_1", streetOnly)
                put("city", cityStateZip)
                put("present", config.present)
                put("lift", config.lift)
                put("replacing", config.replacing)
                put("concreteProblem", config.concreteProblem)
                put("checkType", config.checkType.name)
                put("jobType", config.checkType.name)
            }

            val doorsArray = JSONArray()
            for ((index, door) in doors.withIndex()) {

                fun getBase64Array(target: PhotoTarget): JSONArray {
                    val arr = JSONArray()
                    door.photoUrisFor(target).forEach { arr.put(uriToBase64(context, it)) }
                    return arr
                }

                doorsArray.put(JSONObject().apply {
                    put("doorNum", (index + 1).toString())
                    put("width", door.width)
                    put("height", door.height)
                    put("headroom", door.headroom)
                    put("leftSide", door.leftSide)
                    put("rightSide", door.rightSide)
                    put("backroom", door.backroom)
                    put("mountTo", door.mountTo)
                    put("framed", door.framed)
                    put("concreteReady", door.concreteReady)
                    put("voltage", door.voltage)
                    put("exBrand", door.exBrand)
                    put("exModel", door.exModel)
                    put("exColor", door.exColor)
                    put("exTrack", door.exTrack)
                    put("opFramed", door.opFramed)
                    put("opMount", door.opMount)
                    put("opBrand", door.opBrand)
                    put("opModel", door.opModel)
                    put("opVoltage", door.opVoltage)
                    put("opPhase", door.opPhase)
                    put("opHP", door.opHP)
                    put("opSafety", JSONArray(door.opSafetyList))
                    put("opWallStation", door.opWallStation)
                    put("opWallLocation", door.opWallLocation)
                    put("opRemotes", door.opRemotes)
                    put("opKeypad", door.opKeypad)

                    put("statSections", door.statSections)
                    put("statStruts", door.statStruts)
                    put("statTrack", door.statTrack)
                    put("statSprings", door.statSprings)
                    put("statDoorBalance", door.statDoorBalance)
                    put("statDrums", door.statDrums)
                    put("statCables", door.statCables)
                    put("statBearingsShaft", door.statBearingsShaft)
                    put("statBearingPlates", door.statBearingPlates)
                    put("statHinges", door.statHinges)
                    put("statRollers", door.statRollers)
                    put("statBrackets", door.statBrackets)
                    put("statWeatherseal", door.statWeatherseal)
                    put("statMotor", door.statMotor)
                    put("statLimits", door.statLimits)
                    put("statForceSettings", door.statForceSettings)
                    put("statChainBelt", door.statChainBelt)
                    put("statWallStation", door.statWallStation)
                    put("statPhotoEyes", door.statPhotoEyes)
                    put("statSafetyReverse", door.statSafetyReverse)
                    put("statLightCurtain", door.statLightCurtain)
                    put("statSensingEdge", door.statSensingEdge)

                    put("secNum", door.secNum)
                    put("secWidth", door.secWidth)
                    put("secHeight", door.secHeight)
                    put("secGlass", door.secGlass)
                    put("secNotes", door.secNotes)

                    put("strutQty", door.strutQty)
                    put("strutNotes", door.strutNotes)

                    put("trackHorizontal", door.trackHorizontalList.joinToString(", "))
                    put("trackVertical", door.trackVerticalList.joinToString(", "))
                    put("horizontalLength", door.horizontalLength)
                    put("verticalLength", door.verticalLength)
                    put("trackNotes", door.trackNotes)

                    put("springType", door.springType)
                    put("springWireSize", door.springWireSize)
                    put("springId", door.springId)
                    put("springLength", door.springLength)
                    put("springWind", door.springWindList.joinToString(", "))
                    put("springNotes", door.springNotes)

                    put("doorBalanceNotes", door.doorBalanceNotes)

                    put("drumModel", door.drumModel)
                    put("drumFrontDiameter", door.drumFrontDiameter)
                    put("drumBackDiameter", door.drumBackDiameter)
                    put("drumLocation", door.drumLocation)
                    put("drumNotes", door.drumNotes)

                    put("cableDiameter", door.cableDiameter)
                    put("cableLength", door.cableLength)
                    put("cableNotes", door.cableNotes)

                    put("shaftSize", door.shaftSizeList.joinToString(", "))
                    put("shaftType", door.shaftTypeList.joinToString(", "))
                    put("shaftNotes", door.shaftNotes)

                    put("plateSize", door.plateSize)
                    put("plateLocation", door.plateLocationList.joinToString(", "))
                    put("plateNotes", door.plateNotes)

                    put("hingeType", door.hingeType)
                    put("hingeGauge", door.hingeGauge)
                    put("hingeQty", door.hingeQty)
                    put("hingeNotes", door.hingeNotes)

                    put("rollerMaterial", door.rollerMaterial)
                    put("rollerShortQty", door.rollerShortQty)
                    put("rollerLongQty", door.rollerLongQty)
                    put("rollerNotes", door.rollerNotes)

                    // 🔥 FIXED: Bracket is now mapped as a comma-separated list, bracketSide removed.
                    put("bracketType", door.bracketType.joinToString(", "))
                    put("bracketNotes", door.bracketNotes)

                    put("sealType", door.sealTypeList.joinToString(", "))
                    put("sealColor", door.sealColor)
                    put("sealLocation", door.sealLocationList.joinToString(", "))
                    put("sealThickness", door.sealThickness)
                    put("sealNotes", door.sealNotes)

                    put("motorNotes", door.motorNotes)
                    put("limitsNotes", door.limitsNotes)
                    put("forceNotes", door.forceNotes)
                    put("chainNotes", door.chainNotes)

                    put("wsModel", door.wsModel)
                    put("wsNotes", door.wsNotes)
                    put("peNotes", door.peNotes)
                    put("srNotes", door.srNotes)
                    put("lcNotes", door.lcNotes)
                    put("seNotes", door.seNotes)

                    put("doorNotes", door.doorNotes)
                    put("concreteNotes", door.concreteNotes)
                    put("concreteDepth", "L: ${door.cDepthL} / R: ${door.cDepthR}")
                    put("concreteWidth", "L: ${door.cWidthL} / R: ${door.cWidthR}")
                    put("concreteLength", "L: ${door.cLengthL} / R: ${door.cLengthR}")

                    put("drawing", door.drawingBase64)

                    put("doorPhotos", getBase64Array(PhotoTarget.DOOR))
                    put("concretePhotos", getBase64Array(PhotoTarget.CONCRETE))
                    put("secPhotos", getBase64Array(PhotoTarget.SECTION))
                    put("trackPhotos", getBase64Array(PhotoTarget.TRACK))
                    put("springPhotos", getBase64Array(PhotoTarget.SPRING))
                    put("drumPhotos", getBase64Array(PhotoTarget.DRUM))
                    put("cablePhotos", getBase64Array(PhotoTarget.CABLE))
                    put("shaftPhotos", getBase64Array(PhotoTarget.SHAFT))
                    put("hingePhotos", getBase64Array(PhotoTarget.HINGE))
                    put("rollerPhotos", getBase64Array(PhotoTarget.ROLLER))
                    put("bracketPhotos", getBase64Array(PhotoTarget.BRACKET))
                    put("sealPhotos", getBase64Array(PhotoTarget.SEAL))
                    put("motorPhotos", getBase64Array(PhotoTarget.MOTOR))
                    put("chainPhotos", getBase64Array(PhotoTarget.CHAIN))
                    put("wsPhotos", getBase64Array(PhotoTarget.WS))
                    put("pePhotos", getBase64Array(PhotoTarget.PE))
                    put("lcPhotos", getBase64Array(PhotoTarget.LC))
                    put("sePhotos", getBase64Array(PhotoTarget.SE))
                })
            }
            masterPayload.put("doors", doorsArray)

            val payloadString = masterPayload.toString()
            val filename = "site_check_${System.currentTimeMillis()}.json"
            val file = File(context.filesDir, filename)
            file.writeText(payloadString)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWork = OneTimeWorkRequestBuilder<SiteCheckSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf("FILE_NAME" to filename))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "sitecheck_$filename",
                    ExistingWorkPolicy.KEEP,
                    syncWork
                )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}