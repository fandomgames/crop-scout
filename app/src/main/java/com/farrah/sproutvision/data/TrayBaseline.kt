package com.farrah.sproutvision.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One tray's "before" snapshot for the before/after delta view. Written by
 * the "Snapshot baseline" action on the tray detail screen; later scans for
 * the tray compare against it (health then → now, resolved/new issues).
 * One row per tray — re-snapshotting replaces the previous baseline.
 */
@Entity(
    tableName = "baselines",
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
data class BaselineEntity(
    @PrimaryKey val trayId: Long,
    val timestampEpochMs: Long,
    val healthScore: Int,
    val coveragePercent: Int,
    /** Issues joined with "; ", same encoding as [ScanEntity.issues]. */
    val issues: String = ""
)
