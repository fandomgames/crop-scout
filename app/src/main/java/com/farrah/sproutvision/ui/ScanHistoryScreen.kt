package com.farrah.sproutvision.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.formatVerdictTrace
import com.farrah.sproutvision.analyze.verdictTrace
import com.farrah.sproutvision.data.ScanRecordEntity
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.util.formatDate

/**
 * Photo timeline of one tray's scans, newest first. Lives in the Manage
 * panel only — the HUD stays untouched. Tapping a row shows the full photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    tray: TrayEntity,
    records: List<ScanRecordEntity>,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var fullPhoto by remember { mutableStateOf<ScanRecordEntity?>(null) }

    // Back dismisses the full-photo viewer before leaving the screen.
    BackHandler(enabled = fullPhoto != null) { fullPhoto = null }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("History · ${tray.name}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close menu")
                        }
                    }
                )
            }
        ) { padding ->
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No scans yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan this tray and its photo history will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        ScanHistoryPhotoRow(
                            record = record,
                            onClick = { fullPhoto = record }
                        )
                    }
                }
            }
        }

        // Full-photo viewer as an inline overlay, NOT a Dialog: platform
        // dialogs crash on Quest (window type mismatch — same reason Toasts
        // here must use the application context).
        fullPhoto?.let { record ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(onClick = { fullPhoto = null })
            ) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                        // Swallow taps so they don't dismiss through the card.
                        .clickable(enabled = true, onClick = {})
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(4f / 3f)
                        ) {
                            val bitmap = remember(record.photoPath) {
                                decodeBitmap(record.photoPath, sampleSize = 1)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Scan photo from ${formatDate(record.timestamp)}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(16.dp)
                                )
                            } else {
                                Text(
                                    "No photo for this scan",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            IconButton(
                                onClick = { fullPhoto = null },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close photo")
                            }
                        }
                        HistoryTraceBlock(record = record)
                    }
                }
            }
        }
    }
}

/**
 * Pinned verdict trace for one history entry: same four fields as the
 * verdict card (models, SAM coverage, timestamp, image hash), read-only,
 * collapsed by default. Pre-trace rows show — for the missing fields.
 */
@Composable
private fun HistoryTraceBlock(record: ScanRecordEntity) {
    var expanded by remember(record.id) { mutableStateOf(false) }
    val display = remember(record) { formatVerdictTrace(record.verdictTrace()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (expanded) "Verdict trace ▾" else "Verdict trace ▸",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.clickable { expanded = !expanded }
        )
        if (expanded) {
            HistoryTraceField("Models", display.models)
            HistoryTraceField("SAM coverage", display.coverage)
            HistoryTraceField("Scanned", display.timestamp)
            HistoryTraceField("Image hash", display.imageHash)
        }
    }
}

@Composable
private fun HistoryTraceField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ScanHistoryPhotoRow(record: ScanRecordEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            val thumbnail = remember(record.photoPath) {
                decodeBitmap(record.photoPath, sampleSize = 4)
            }
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDate(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Health ${record.healthPct}% · coverage ${record.coveragePct}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.issues.isNotBlank()) {
                    Text(
                        record.issues,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

private fun decodeBitmap(path: String?, sampleSize: Int) =
    try {
        if (path.isNullOrBlank()) null
        else BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
