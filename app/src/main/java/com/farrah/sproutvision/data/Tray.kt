package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** One grow tray. The latest scan analysis is snapshotted onto the tray for quick display. */
@Entity(tableName = "trays")
data class TrayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val variety: String,
    val seededAtEpochMs: Long,
    // Null = not set yet: trays are created without it and it is added
    // later from the detail screen. Every countdown must null-guard.
    val expectedDaysToHarvest: Int?,
    val currentStage: GrowthStage = GrowthStage.SEEDED,
    val notes: String = "",
    val growIntent: GrowIntent = GrowIntent.MICROGREEN,
    // Last scan analysis snapshot
    val lastHealthScore: Int? = null,
    val lastCoveragePercent: Int? = null,
    val lastIssues: String = "",
    val lastRecommendation: String = "",
    val lastAnalyzedAtEpochMs: Long? = null,
    val lastAnalyzedByDemo: Boolean = true,
    // World-space anchor for the floating tray card (null = unplaced).
    // The rack doesn't move, so saved poses are stable anchors.
    val anchorX: Float? = null,
    val anchorY: Float? = null,
    val anchorZ: Float? = null
) {
    fun hasAnchor(): Boolean = anchorX != null && anchorY != null && anchorZ != null
}

/** Timestamped stage transitions for a tray (the grow timeline). */
@Entity(
    tableName = "stage_entries",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trayId")]
)
data class StageEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val stage: GrowthStage,
    val timestampEpochMs: Long,
    val note: String = ""
)

/** One saved scan analysis. The tray row keeps a snapshot of the latest. */
@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trayId", "timestampEpochMs"])]
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val timestampEpochMs: Long,
    val variety: String,
    val stage: GrowthStage,
    val healthScore: Int,
    val coveragePercent: Int,
    val issues: String = "",
    val recommendation: String = "",
    val isDemo: Boolean = true,
    /** Trace: models label, null for scans saved before the trace feature. */
    val modelsUsed: String? = null,
    /** Trace: SAM-measured coverage, null when unavailable or pre-trace. */
    val samCoverage: Int? = null,
    /** Trace: SHA-256 hex of the captured JPEG bytes, null for pre-trace scans. */
    val imageHash: String? = null
)

class Converters {
    @TypeConverter
    fun stageToString(stage: GrowthStage): String = stage.name

    @TypeConverter
    fun stringToStage(name: String): GrowthStage = GrowthStage.fromName(name)

    @TypeConverter
    fun intentToString(intent: GrowIntent): String = intent.name

    @TypeConverter
    fun stringToIntent(name: String): GrowIntent = GrowIntent.fromName(name)
}
