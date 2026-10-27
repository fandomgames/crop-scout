package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved scan with its captured photo. Written alongside [ScanEntity] on
 * every scan save; shown as a photo timeline in the Manage panel's history.
 */
@Entity(
    tableName = "scan_records",
    foreignKeys = [
        ForeignKey(
            entity = TrayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trayId", "timestamp"])]
)
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trayId: Long,
    val timestamp: Long,
    val healthPct: Int,
    val coveragePct: Int,
    val issues: String = "",
    /** Absolute path of the captured JPEG under filesDir/scans, null when no photo. */
    val photoPath: String? = null
)
