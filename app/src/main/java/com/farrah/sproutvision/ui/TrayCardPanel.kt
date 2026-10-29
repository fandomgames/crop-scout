package com.farrah.sproutvision.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.SCAN_NO_PHOTO_TEXT
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.latestScanPhoto
import com.farrah.sproutvision.ui.theme.SproutVisionTheme
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.daysSince
import com.farrah.sproutvision.util.daysUntil
import com.farrah.sproutvision.util.formatDate
import com.farrah.sproutvision.util.formatDateTime

/**
 * Whether a tray's floating card is shown: anchored cards always (except in
 * clean mode); unanchored ghosts only during placement mode.
 */
fun trayCardVisible(hasAnchor: Boolean, placementMode: Boolean, cleanMode: Boolean): Boolean =
    !cleanMode && (hasAnchor || placementMode)

/**
 * Root for one runtime tray-card panel. Observes the same ViewModel state as
 * the HUD, so a fresh scan updates the card immediately.
 */
@Composable
fun TrayCardRoute(vm: SproutViewModel, trayId: Long) {
    val trays by vm.trays.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val records by vm.observeScanRecords(trayId).collectAsStateWithLifecycle(initialValue = emptyList())
    val tray = trays.firstOrNull { it.id == trayId } ?: return
    val ghost = vm.placementMode && !tray.hasAnchor()
    SproutVisionTheme(darkMode = settings.darkMode) {
        TrayCardPanel(
            tray = tray,
            ghost = ghost,
            expanded = !ghost && vm.expandedCardTrayId == trayId,
            latestPhotoPath = latestScanPhoto(records),
            onToggleExpand = { vm.toggleCardExpanded(trayId) },
            onScan = { vm.openTrayScan(trayId) },
            onDrop = { vm.dropCardTrayId = trayId }
        )
    }
}

@Composable
fun TrayCardPanel(
    tray: TrayEntity,
    ghost: Boolean,
    expanded: Boolean,
    latestPhotoPath: String?,
    onToggleExpand: () -> Unit,
    onScan: () -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ghosts are translucent; tapping one drops it in place, grabbing moves it.
    Card(
        modifier = modifier.fillMaxSize()
            .then(if (ghost) Modifier.alpha(0.6f) else Modifier)
            .clickable(onClick = { if (ghost) onDrop() else onToggleExpand() })
    ) {
        if (ghost) {
            GhostBody(tray)
        } else if (expanded) {
            ExpandedBody(tray, latestPhotoPath, onToggleExpand, onScan)
        } else {
            CompactBody(tray)
        }
    }
}

@Composable
private fun CompactBody(tray: TrayEntity) {
    val day = remember(tray.id, tray.seededAtEpochMs) { daysSince(tray.seededAtEpochMs) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tray.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = tray.lastHealthScore?.let { "$it%" } ?: "—",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = healthColor(tray.lastHealthScore)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Day $day · ${tray.variety}",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to expand",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandedBody(
    tray: TrayEntity,
    latestPhotoPath: String?,
    onCollapse: () -> Unit,
    onScan: () -> Unit
) {
    val day = remember(tray.id, tray.seededAtEpochMs) { daysSince(tray.seededAtEpochMs) }
    val expected = tray.expectedDaysToHarvest
    val harvestAt = expected?.let { tray.seededAtEpochMs + it * MILLIS_PER_DAY }
    val daysLeft = remember(tray.id, harvestAt) { harvestAt?.let { daysUntil(it) } }
    val harvestText = if (tray.growIntent == GrowIntent.GROW_OUT) "Growing out"
    else if (expected == null || daysLeft == null) "No harvest date set"
    else if (daysLeft >= 0) "${formatDate(harvestAt!!)} · in ${daysLeft}d"
    else "${formatDate(harvestAt!!)} · ${-daysLeft}d overdue"
    val issues = remember(tray.id, tray.lastIssues) {
        tray.lastIssues.split(";").map { it.trim() }.filter { it.isNotBlank() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tray.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.Close, contentDescription = "Collapse card")
            }
        }
        // Latest scan photo on top; no photo reads as placeholder text,
        // never a fake photo, never a crash.
        val bitmap = remember(latestPhotoPath) { decodeBitmap(latestPhotoPath, sampleSize = 2) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Latest scan photo for ${tray.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        } else {
            Text(
                text = SCAN_NO_PHOTO_TEXT,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Day $day · Health ${tray.lastHealthScore?.let { "$it%" } ?: "—"}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Coverage ${tray.lastCoveragePercent?.let { "$it%" } ?: "—"}",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (issues.isEmpty()) "Issues: none"
            else "Issues: ${issues.take(2).joinToString("; ")}",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        Text(
            text = "Harvest $harvestText",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = tray.lastAnalyzedAtEpochMs?.let { "Last scan ${formatDateTime(it)}" }
                ?: "Not scanned yet",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Scan this tray", fontSize = 18.sp)
        }
    }
}

@Composable
private fun GhostBody(tray: TrayEntity) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tray.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to drop here · or grab to move",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun healthColor(health: Int?) = when {
    health == null -> MaterialTheme.colorScheme.onSurfaceVariant
    health >= 80 -> MaterialTheme.colorScheme.primary
    health >= 60 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
