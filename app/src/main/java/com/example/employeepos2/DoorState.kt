package com.example.employeepos2.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DoorState(
    var width: String = "",
    var height: String = "",
    var headroom: String = "",
    var leftSide: String = "",
    var rightSide: String = "",
    var backroom: String = "",
    var mountTo: String = "N/A",
    var framed: String = "N/A",
    var voltage: String = "N/A",
    var concreteReady: String = "N/A",
    var exBrand: String = "",
    var exModel: String = "",
    var exColor: String = "",
    var exTrack: String = "N/A",
    var doorNotes: String = "",
    var concreteNotes: String = "",
    var cDepthL: String = "",
    var cDepthR: String = "",
    var cWidthL: String = "",
    var cWidthR: String = "",
    var cLengthL: String = "",
    var cLengthR: String = "",
    var opFramed: String = "N/A",
    var opMount: String = "N/A",
    var opBrand: String = "",
    var opModel: String = "",
    var opVoltage: String = "",
    var opPhase: String = "",
    var opHP: String = "",
    var opSafetyList: MutableList<String> = mutableListOf(),
    var opWallStation: String = "N/A",
    var opWallLocation: String = "N/A",
    var opRemotes: String = "N/A",
    var opKeypad: String = "N/A",
    var statDoorBalance: String = "N/A",
    var statSections: String = "N/A",
    var statStruts: String = "N/A",
    var statTrack: String = "N/A",
    var statSprings: String = "N/A",
    var statDrums: String = "N/A",
    var statCables: String = "N/A",
    var statBearingsShaft: String = "N/A",
    var statBearingPlates: String = "N/A",
    var statHinges: String = "N/A",
    var statRollers: String = "N/A",
    var statBrackets: String = "N/A",
    var statWeatherseal: String = "N/A",
    var statMotor: String = "N/A",
    var statLimits: String = "N/A",
    var statForceSettings: String = "N/A",
    var statSafetyReverse: String = "N/A",
    var statChainBelt: String = "N/A",
    var statWallStation: String = "N/A",
    var statPhotoEyes: String = "N/A",
    var statLightCurtain: String = "N/A",
    var statSensingEdge: String = "N/A",
    var doorBalanceNotes: String = "",
    var secNum: String = "",
    var secWidth: String = "",
    var secHeight: String = "",
    var secGlass: String = "",
    var secNotes: String = "",
    var strutQty: String = "",
    var strutNotes: String = "",
    var trackHorizontalList: MutableList<String> = mutableListOf(),
    var trackVerticalList: MutableList<String> = mutableListOf(),
    var horizontalLength: String = "",
    var verticalLength: String = "",
    var trackNotes: String = "",
    var springType: String = "N/A",
    var springWireSize: String = "",
    var springId: String = "",
    var springLength: String = "",
    var springWindList: MutableList<String> = mutableListOf(),
    var springNotes: String = "",
    var drumModel: String = "",
    var drumFrontDiameter: String = "",
    var drumBackDiameter: String = "",
    var drumLocation: String = "N/A",
    var drumNotes: String = "",
    var cableDiameter: String = "",
    var cableLength: String = "",
    var cableNotes: String = "",
    var shaftSizeList: MutableList<String> = mutableListOf(),
    var shaftTypeList: MutableList<String> = mutableListOf(),
    var shaftNotes: String = "",
    var plateSize: String = "N/A",
    var plateLocationList: MutableList<String> = mutableListOf(),
    var plateNotes: String = "",
    var hingeType: String = "",
    var hingeGauge: String = "N/A",
    var hingeQty: String = "",
    var hingeNotes: String = "",
    var rollerMaterial: String = "N/A",
    var rollerShortQty: String = "",
    var rollerLongQty: String = "",
    var rollerNotes: String = "",

    var bracketType: MutableList<String> = mutableListOf(),
    var bracketNotes: String = "",

    var sealTypeList: MutableList<String> = mutableListOf(),
    var sealColor: String = "",
    var sealLocationList: MutableList<String> = mutableListOf(),
    var sealThickness: String = "N/A",
    var sealNotes: String = "",

    var motorNotes: String = "",
    var limitsNotes: String = "",
    var forceNotes: String = "",
    var srNotes: String = "",
    var chainNotes: String = "",
    var wsModel: String = "",
    var wsNotes: String = "",
    var peNotes: String = "",
    var lcNotes: String = "",
    var seNotes: String = "",
    var drawingBase64: String = "",
    val doorPhotoUris: MutableList<String> = mutableListOf(),
    val concretePhotoUris: MutableList<String> = mutableListOf(),
    val secPhotoUris: MutableList<String> = mutableListOf(),
    val trackPhotoUris: MutableList<String> = mutableListOf(),
    val springPhotoUris: MutableList<String> = mutableListOf(),
    val drumPhotoUris: MutableList<String> = mutableListOf(),
    val cablePhotoUris: MutableList<String> = mutableListOf(),
    val shaftPhotoUris: MutableList<String> = mutableListOf(),
    val hingePhotoUris: MutableList<String> = mutableListOf(),
    val rollerPhotoUris: MutableList<String> = mutableListOf(),
    val bracketPhotoUris: MutableList<String> = mutableListOf(),
    val sealPhotoUris: MutableList<String> = mutableListOf(),
    val motorPhotoUris: MutableList<String> = mutableListOf(),
    val chainPhotoUris: MutableList<String> = mutableListOf(),
    val wsPhotoUris: MutableList<String> = mutableListOf(),
    val pePhotoUris: MutableList<String> = mutableListOf(),
    val lcPhotoUris: MutableList<String> = mutableListOf(),
    val sePhotoUris: MutableList<String> = mutableListOf(),
) : Parcelable {

    fun photoUrisFor(target: PhotoTarget): MutableList<String> = when (target) {
        PhotoTarget.DOOR     -> doorPhotoUris
        PhotoTarget.CONCRETE -> concretePhotoUris
        PhotoTarget.SECTION  -> secPhotoUris
        PhotoTarget.TRACK    -> trackPhotoUris
        PhotoTarget.SPRING   -> springPhotoUris
        PhotoTarget.DRUM     -> drumPhotoUris
        PhotoTarget.CABLE    -> cablePhotoUris
        PhotoTarget.SHAFT    -> shaftPhotoUris
        PhotoTarget.HINGE    -> hingePhotoUris
        PhotoTarget.ROLLER   -> rollerPhotoUris
        PhotoTarget.BRACKET  -> bracketPhotoUris
        PhotoTarget.SEAL     -> sealPhotoUris
        PhotoTarget.MOTOR    -> motorPhotoUris
        PhotoTarget.CHAIN    -> chainPhotoUris
        PhotoTarget.WS       -> wsPhotoUris
        PhotoTarget.PE       -> pePhotoUris
        PhotoTarget.LC       -> lcPhotoUris
        PhotoTarget.SE       -> sePhotoUris
    }
}

enum class PhotoTarget {
    DOOR, CONCRETE, SECTION, TRACK, SPRING, DRUM, CABLE, SHAFT,
    HINGE, ROLLER, BRACKET, SEAL, MOTOR, CHAIN, WS, PE, LC, SE
}