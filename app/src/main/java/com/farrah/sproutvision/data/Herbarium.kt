package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.farrah.sproutvision.analyze.IdentifyConfidence
import com.farrah.sproutvision.analyze.PlantCareSheet
import com.farrah.sproutvision.analyze.PlantIdentification

/**
 * One saved wild-plant identification (the herbarium collection). Written
 * only on an explicit Save tap — identification itself never auto-saves —
 * and kept fully separate from trays, scans, and the scorecard.
 */
@Entity(tableName = "herbarium")
data class HerbariumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val commonName: String,
    val scientificName: String,
    val family: String,
    /** [IdentifyConfidence] name; anything unknown reopens as LOW. */
    val confidence: String,
    val photoPath: String?,
    /** Filled in manually — Quest has no GPS (e.g. "Petrifying Springs, Oct 19"). */
    val locationNote: String,
    val sun: String = "",
    val water: String = "",
    val soil: String = "",
    val hardinessZones: String = "",
    val bloomTime: String = "",
    val matureSize: String = "",
    val funFact: String = "",
    val betterPhotoTip: String = ""
)

/** Builds the herbarium row for an explicit Save — the only write path. */
fun herbariumEntryOf(
    result: PlantIdentification,
    photoPath: String?,
    timestampMs: Long,
    locationNote: String
): HerbariumEntity = HerbariumEntity(
    timestampEpochMs = timestampMs,
    commonName = result.commonName,
    scientificName = result.scientificName,
    family = result.family,
    confidence = result.confidence.name,
    photoPath = photoPath,
    locationNote = locationNote,
    sun = result.care.sun,
    water = result.care.water,
    soil = result.care.soil,
    hardinessZones = result.care.hardinessZones,
    bloomTime = result.care.bloomTime,
    matureSize = result.care.matureSize,
    funFact = result.care.funFact,
    betterPhotoTip = result.betterPhotoTip
)

/** Reopens a saved entry as the full identification card. */
fun HerbariumEntity.toIdentification(): PlantIdentification = PlantIdentification(
    commonName = commonName,
    scientificName = scientificName,
    family = family,
    confidence = IdentifyConfidence.fromName(confidence),
    care = PlantCareSheet(
        sun = sun,
        water = water,
        soil = soil,
        hardinessZones = hardinessZones,
        bloomTime = bloomTime,
        matureSize = matureSize,
        funFact = funFact
    ),
    betterPhotoTip = betterPhotoTip
)
