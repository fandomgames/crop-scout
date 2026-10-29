package com.farrah.sproutvision.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.formatVerdictTrace
import com.farrah.sproutvision.analyze.verdictTrace
import com.farrah.sproutvision.data.BaselineEntity
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.SCAN_NO_PHOTO_TEXT
import com.farrah.sproutvision.data.ScanEntity
import com.farrah.sproutvision.data.ScanRecordEntity
import com.farrah.sproutvision.data.scanPhotoPath
import com.farrah.sproutvision.data.StageEntryEntity
import com.farrah.sproutvision.data.PhEcReading
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.FeedingEntry
import com.farrah.sproutvision.data.FeedingSchedule
import com.farrah.sproutvision.data.NutrientProduct
import com.farrah.sproutvision.data.VarietyDefaults
import com.farrah.sproutvision.data.canLogFeeding
import com.farrah.sproutvision.data.canSavePhEc
import com.farrah.sproutvision.data.computeDose
import com.farrah.sproutvision.data.dosageRuleOf
import com.farrah.sproutvision.data.formatDose
import com.farrah.sproutvision.data.formatPhEc
import com.farrah.sproutvision.data.isOutOfRange
import com.farrah.sproutvision.data.latestReading
import com.farrah.sproutvision.data.rangeSuffix
import com.farrah.sproutvision.data.stepReadingEc
import com.farrah.sproutvision.data.stepReadingPh
import com.farrah.sproutvision.data.stepReservoir
import com.farrah.sproutvision.data.stepStrength
import com.farrah.sproutvision.data.stepTargetEcHigh
import com.farrah.sproutvision.data.stepTargetEcLow
import com.farrah.sproutvision.data.stepTargetPhHigh
import com.farrah.sproutvision.data.stepTargetPhLow
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.daysUntil
import com.farrah.sproutvision.util.formatDate
import com.farrah.sproutvision.util.formatDateTime
import com.farrah.sproutvision.util.startOfDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrayDetailScreen(
    tray: TrayEntity,
    stages: List<StageEntryEntity>,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onAdvance: () -> Unit,
    onSetGrowIntent: (GrowIntent) -> Unit,
    onSetExpectedDays: (Int?) -> Unit,
    onClearCardAnchor: () -> Unit,
    onSaveNotes: (String) -> Unit,
    onDelete: () -> Unit,
    onSaveEntry: (
        existing: StageEntryEntity?,
        stage: GrowthStage,
        timestampMs: Long,
        note: String
    ) -> Unit,
    onDeleteEntry: (StageEntryEntity) -> Unit,
    onClose: () -> Unit,
    onClearAnalysis: () -> Unit,
    scans: List<ScanEntity>,
    records: List<ScanRecordEntity>,
    onDeleteScan: (ScanEntity) -> Unit,
    onHistory: () -> Unit,
    baseline: BaselineEntity?,
    onSnapshotBaseline: () -> Unit,
    readings: List<PhEcReading>,
    onSaveReading: (ph: Double?, ec: Double?) -> Unit,
    onSetTargets: (phLow: Double?, phHigh: Double?, ecLow: Double?, ecHigh: Double?) -> Unit,
    nutrients: List<NutrientProduct>,
    schedules: List<FeedingSchedule>,
    feedings: List<FeedingEntry>,
    onSaveSchedule: (nutrientId: Long, strengthPercent: Int, startStage: String?) -> Unit,
    onDeleteSchedule: (FeedingSchedule) -> Unit,
    onLogFeeding: (nutrientId: Long, strengthPercent: Int, reservoirLiters: Double) -> Unit
) {
    var notes by remember(tray.id) { mutableStateOf(tray.notes) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingStage by remember { mutableStateOf<GrowthStage?>(null) }
    var deletingEntry by remember { mutableStateOf<StageEntryEntity?>(null) }
    var showClearScanConfirm by remember { mutableStateOf(false) }
    var deletingScan by remember { mutableStateOf<ScanEntity?>(null) }
    var viewingPhoto by remember { mutableStateOf<ScanEntity?>(null) }
    // Harvest confirm, keyed to the tray like notes: backing out clears it.
    var showHarvestConfirm by remember(tray.id) { mutableStateOf(false) }
    var showRemoveCardConfirm by remember(tray.id) { mutableStateOf(false) }
    val harvestAt = tray.expectedDaysToHarvest?.let { tray.seededAtEpochMs + it * MILLIS_PER_DAY }
    val daysLeft = harvestAt?.let { daysUntil(it) }
    val next = tray.currentStage.next()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tray.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete tray")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (showDeleteConfirm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Delete this tray?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This removes the tray and its stage history.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tray.variety,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StageBadge(tray.currentStage)
            }
            Spacer(Modifier.height(4.dp))
            val harvestText = if (tray.growIntent == GrowIntent.GROW_OUT) {
                "Growing out to full maturity — no harvest date"
            } else {
                val left = daysLeft
                val at = harvestAt
                if (left == null || at == null) {
                    "No expected harvest date set"
                } else if (left >= 0) {
                    "Harvest in ~$left day(s) — expected ${formatDate(at)}"
                } else {
                    "Harvest was ${-left} day(s) ago — expected ${formatDate(at)}"
                }
            }
            Text(
                harvestText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                tray.currentStage.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Expected days are set here, after the fact — creation only
            // asks for name and variety. Steppers, no typing: [+]/[-]
            // adjust, Clear unsets. Grow-out trays skip the countdown.
            if (tray.growIntent == GrowIntent.MICROGREEN) {
                val days = tray.expectedDaysToHarvest
                val typical = VarietyDefaults.daysFor(tray.variety)
                Spacer(Modifier.height(12.dp))
                Text("Expected harvest", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Typical for ${tray.variety}: ~$typical days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSetExpectedDays(days!! - 1) },
                        enabled = days != null && days > 1
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Fewer days")
                    }
                    Text(
                        days?.let { "$it days" } ?: "Not set",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { onSetExpectedDays((days ?: typical - 1) + 1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "More days")
                    }
                    if (days != null) {
                        TextButton(onClick = { onSetExpectedDays(null) }) {
                            Text("Clear")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Growing as", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Grow-out trays skip the microgreen harvest countdown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GrowIntent.entries.forEach { intent ->
                    if (tray.growIntent == intent) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(intent.displayName)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetGrowIntent(intent) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(intent.displayName)
                        }
                    }
                }
            }

            if (tray.hasAnchor()) {
                Spacer(Modifier.height(12.dp))
                Text("Placed card", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This tray has a floating card pinned at the rack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRemoveCardConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove placed card")
                }
            }
            if (showRemoveCardConfirm) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Remove ${tray.name}'s card?",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Unpins the floating card. The tray and its " +
                                "history stay — place it again anytime.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showRemoveCardConfirm = false }) {
                                Text("Keep card")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { showRemoveCardConfirm = false; onClearCardAnchor() }
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Text("Scan tray")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Text("History")
                }
                if (next != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (tray.currentStage.needsHarvestConfirm()) showHarvestConfirm = true
                            else onAdvance()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark as ${next.displayName}")
                    }
                }
            }
            if (showHarvestConfirm) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mark ${tray.name} harvested?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Harvested trays leave the HUD switcher. " +
                                "Delete this log entry later to undo.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showHarvestConfirm = false }) { Text("Keep growing") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showHarvestConfirm = false; onAdvance() }) {
                                Text("Harvest")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Stage timeline", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            StageTimeline(
                current = tray.currentStage,
                entries = stages,
                onEdit = { editingStage = it },
                onDelete = { deletingEntry = it }
            )
            editingStage?.let { stageFor ->
                val existing = stages.firstOrNull { it.stage == stageFor }
                Spacer(Modifier.height(12.dp))
                StageEntryForm(
                    initialStage = stageFor,
                    existing = existing,
                    onDismiss = { editingStage = null },
                    onSave = { stage, timestampMs, note ->
                        onSaveEntry(existing, stage, timestampMs, note)
                        editingStage = null
                    }
                )
            }
            deletingEntry?.let { entry ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Delete this log?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This removes the ${entry.stage.displayName} entry from ${formatDate(entry.timestampEpochMs)}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { deletingEntry = null }) { Text("Cancel") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { deletingEntry = null; onDeleteEntry(entry) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (tray.lastAnalyzedAtEpochMs != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Last scan", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Health ${tray.lastHealthScore ?: "-"}/100 · coverage ${tray.lastCoveragePercent ?: "-"}%")
                        if (tray.lastIssues.isNotBlank()) Text("Issues: ${tray.lastIssues}")
                        if (tray.lastRecommendation.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(tray.lastRecommendation, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Analyzed ${formatDate(tray.lastAnalyzedAtEpochMs)}" +
                                (if (tray.lastAnalyzedByDemo) " (demo)" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (showClearScanConfirm) {
                            Text("Clear this scan\u2019s data?", style = MaterialTheme.typography.bodySmall)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { showClearScanConfirm = false }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showClearScanConfirm = false; onClearAnalysis() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            TextButton(onClick = { showClearScanConfirm = true }) {
                                Text("Clear scan data", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Before/after baseline", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (baseline == null) {
                        Text(
                            "No baseline yet. Later scans compare against this snapshot.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Health ${baseline.healthScore}/100 · ${formatDate(baseline.timestampEpochMs)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (baseline.issues.isNotBlank()) {
                            Text(
                                baseline.issues,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSnapshotBaseline,
                        enabled = tray.lastHealthScore != null
                    ) {
                        Text(if (baseline == null) "Snapshot baseline" else "Update baseline")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (scans.isNotEmpty()) {
                Text("Scan history", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                scans.forEach { scan ->
                    ScanHistoryRow(
                        scan = scan,
                        photoPath = scanPhotoPath(records, scan),
                        onDelete = { deletingScan = scan },
                        onPhoto = { viewingPhoto = scan }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                deletingScan?.let { doomed ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Delete this scan?", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Removes the scan from ${formatDate(doomed.timestampEpochMs)}.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { deletingScan = null }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { deletingScan = null; onDeleteScan(doomed) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            PhEcSection(
                tray = tray,
                readings = readings,
                onSaveReading = onSaveReading,
                onSetTargets = onSetTargets
            )
            Spacer(Modifier.height(16.dp))

            FeedingSection(
                trayId = tray.id,
                nutrients = nutrients,
                schedules = schedules,
                feedings = feedings,
                onSaveSchedule = onSaveSchedule,
                onDeleteSchedule = onDeleteSchedule,
                onLogFeeding = onLogFeeding
            )
            Spacer(Modifier.height(16.dp))

            Text("Notes", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Watering, observations, harvest weight…") }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveNotes(notes) },
                enabled = notes != tray.notes
            ) {
                Text("Save notes")
            }
        }
    }

    // Scan-photo viewer as an inline overlay, NOT a Dialog (Quest window
    // type mismatch). Photo plus scan details; the trace stays in the
    // row, unchanged and hash-only — the photo is not in the trace.
    viewingPhoto?.let { scan ->
        val path = scanPhotoPath(records, scan)
        val bitmap = remember(path) { decodeBitmap(path, sampleSize = 1) }
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = { viewingPhoto = null })
        ) {
            Card(
                modifier = Modifier.fillMaxSize().padding(32.dp)
                    .clickable(enabled = true, onClick = {})
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateTime(scan.timestampEpochMs),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewingPhoto = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close photo")
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Scan photo from ${formatDate(scan.timestampEpochMs)}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                        )
                    } else {
                        Text(
                            SCAN_NO_PHOTO_TEXT,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Health ${scan.healthScore}/100 · coverage ${scan.coveragePercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (scan.issues.isNotBlank()) {
                        Text(
                            scan.issues,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }

}

@Composable
private fun ScanHistoryRow(
    scan: ScanEntity,
    photoPath: String?,
    onDelete: () -> Unit,
    onPhoto: () -> Unit
) {
    var traceExpanded by remember(scan.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { traceExpanded = !traceExpanded }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail tap opens the photo viewer; row tap still
                // toggles the trace, unchanged.
                val thumbnail = remember(photoPath) { decodeBitmap(photoPath, sampleSize = 4) }
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onPhoto)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onPhoto),
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
                        "${formatDate(scan.timestampEpochMs)} · health ${scan.healthScore}/100",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${scan.variety} · ${scan.stage.displayName} · ${scan.coveragePercent}%" +
                            (if (scan.isDemo) " (demo)" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (scan.issues.isNotBlank()) {
                        Text(
                            scan.issues,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete scan")
                }
            }
            ScanRowTraceBlock(scan = scan, expanded = traceExpanded)
        }
    }
}

/**
 * Pinned verdict trace for one per-tray history entry: same four fields
 * as the verdict card and the photo history (models, SAM coverage,
 * timestamp, image hash), read-only, collapsed by default. Pre-trace
 * rows show — for the missing fields.
 */
@Composable
private fun ScanRowTraceBlock(scan: ScanEntity, expanded: Boolean) {
    val display = remember(scan) { formatVerdictTrace(scan.verdictTrace()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (expanded) "Verdict trace ▾" else "Verdict trace ▸",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded) {
            ScanRowTraceField("Models", display.models)
            ScanRowTraceField("SAM coverage", display.coverage)
            ScanRowTraceField("Scanned", display.timestamp)
            ScanRowTraceField("Image hash", display.imageHash)
        }
    }
}

@Composable
private fun ScanRowTraceField(label: String, value: String) {
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

/**
 * Per-tray manual pH/EC log: latest reading, an Add-reading composer
 * (hands-only steppers, date auto-filled to today), editable target
 * ranges, and newest-first history. Log only — readings never feed
 * the verdict. Append-only in v1 (no edit or delete).
 */
@Composable
private fun PhEcSection(
    tray: TrayEntity,
    readings: List<PhEcReading>,
    onSaveReading: (ph: Double?, ec: Double?) -> Unit,
    onSetTargets: (phLow: Double?, phHigh: Double?, ecLow: Double?, ecHigh: Double?) -> Unit
) {
    var ph by remember(tray.id) { mutableStateOf<Double?>(null) }
    var ec by remember(tray.id) { mutableStateOf<Double?>(null) }
    val todayMs = remember { System.currentTimeMillis() }
    val latest = remember(readings) { latestReading(readings) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("pH / EC log", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (latest == null) "Latest: —"
            else "Latest: pH ${formatPhEc(latest.ph)} · EC ${formatPhEc(latest.ec)} · " +
                formatDate(latest.timestampEpochMs),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "New reading · ${formatDate(todayMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PhEcStepperRow(
            label = "pH",
            value = formatPhEc(ph),
            onMinus = { ph = stepReadingPh(ph, false) },
            onPlus = { ph = stepReadingPh(ph, true) }
        )
        PhEcStepperRow(
            label = "EC (mS/cm)",
            value = formatPhEc(ec),
            onMinus = { ec = stepReadingEc(ec, false) },
            onPlus = { ec = stepReadingEc(ec, true) }
        )
        Button(
            onClick = { onSaveReading(ph, ec); ph = null; ec = null },
            enabled = canSavePhEc(ph, ec)
        ) {
            Text("Save reading")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Target ranges",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PhEcStepperRow(
            label = "pH low",
            value = formatPhEc(tray.phTargetLow),
            onMinus = {
                onSetTargets(
                    stepTargetPhLow(tray.phTargetLow, false),
                    tray.phTargetHigh, tray.ecTargetLow, tray.ecTargetHigh
                )
            },
            onPlus = {
                onSetTargets(
                    stepTargetPhLow(tray.phTargetLow, true),
                    tray.phTargetHigh, tray.ecTargetLow, tray.ecTargetHigh
                )
            }
        )
        PhEcStepperRow(
            label = "pH high",
            value = formatPhEc(tray.phTargetHigh),
            onMinus = {
                onSetTargets(
                    tray.phTargetLow,
                    stepTargetPhHigh(tray.phTargetHigh, false), tray.ecTargetLow, tray.ecTargetHigh
                )
            },
            onPlus = {
                onSetTargets(
                    tray.phTargetLow,
                    stepTargetPhHigh(tray.phTargetHigh, true), tray.ecTargetLow, tray.ecTargetHigh
                )
            }
        )
        PhEcStepperRow(
            label = "EC low",
            value = formatPhEc(tray.ecTargetLow),
            onMinus = {
                onSetTargets(
                    tray.phTargetLow, tray.phTargetHigh,
                    stepTargetEcLow(tray.ecTargetLow, false), tray.ecTargetHigh
                )
            },
            onPlus = {
                onSetTargets(
                    tray.phTargetLow, tray.phTargetHigh,
                    stepTargetEcLow(tray.ecTargetLow, true), tray.ecTargetHigh
                )
            }
        )
        PhEcStepperRow(
            label = "EC high",
            value = formatPhEc(tray.ecTargetHigh),
            onMinus = {
                onSetTargets(
                    tray.phTargetLow, tray.phTargetHigh, tray.ecTargetLow,
                    stepTargetEcHigh(tray.ecTargetHigh, false)
                )
            },
            onPlus = {
                onSetTargets(
                    tray.phTargetLow, tray.phTargetHigh, tray.ecTargetLow,
                    stepTargetEcHigh(tray.ecTargetHigh, true)
                )
            }
        )
        if (readings.isEmpty()) {
            Text(
                "No readings yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            readings.forEach { reading ->
                PhEcReadingRow(reading = reading, tray = tray)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PhEcStepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text("-") }
        Text(value, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun PhEcReadingRow(reading: PhEcReading, tray: TrayEntity) {
    val flagged = isOutOfRange(reading.ph, tray.phTargetLow, tray.phTargetHigh) ||
        isOutOfRange(reading.ec, tray.ecTargetLow, tray.ecTargetHigh)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatDate(reading.timestampEpochMs) +
                " · pH ${formatPhEc(reading.ph)}" +
                rangeSuffix(reading.ph, tray.phTargetLow, tray.phTargetHigh) +
                " · EC ${formatPhEc(reading.ec)}" +
                rangeSuffix(reading.ec, tray.ecTargetLow, tray.ecTargetHigh),
            style = MaterialTheme.typography.bodyMedium,
            color = if (flagged) MaterialTheme.colorScheme.error else Color.Unspecified
        )
        if (reading.note.isNotBlank()) {
            Text(
                reading.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Per-tray feeding: schedules plus the feeding log. Hands-only — nutrient
 * picks and steppers, no typing. The dose preview and the logged dose both
 * come from the pure [computeDose], never an LLM. Log only — feeding
 * never feeds the verdict.
 */
@Composable
private fun FeedingSection(
    trayId: Long,
    nutrients: List<NutrientProduct>,
    schedules: List<FeedingSchedule>,
    feedings: List<FeedingEntry>,
    onSaveSchedule: (nutrientId: Long, strengthPercent: Int, startStage: String?) -> Unit,
    onDeleteSchedule: (FeedingSchedule) -> Unit,
    onLogFeeding: (nutrientId: Long, strengthPercent: Int, reservoirLiters: Double) -> Unit
) {
    var pickedId by remember(trayId) { mutableStateOf<Long?>(null) }
    var strength by remember(trayId) { mutableStateOf(100) }
    var reservoir by remember(trayId) { mutableStateOf(0.0) }
    var startStage by remember(trayId) { mutableStateOf<String?>(null) }
    val picked = nutrients.firstOrNull { it.id == pickedId }
    val todayMs = remember { System.currentTimeMillis() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Feeding", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (nutrients.isEmpty()) {
            Text(
                "No nutrients in inventory — add some in Manage first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Nutrient",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            nutrients.forEach { product ->
                TextButton(onClick = { pickedId = product.id }) {
                    Text(
                        (if (product.id == pickedId) "● " else "○ ") + product.name,
                        color = if (product.id == pickedId) MaterialTheme.colorScheme.primary
                        else Color.Unspecified
                    )
                }
            }
            PhEcStepperRow(
                label = "Strength",
                value = "$strength%",
                onMinus = { strength = stepStrength(strength, false) },
                onPlus = { strength = stepStrength(strength, true) }
            )
            Text(
                "Start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FeedingStagePick(
                    label = "Anytime",
                    selected = startStage == null,
                    onClick = { startStage = null }
                )
                GrowthStage.entries.forEach { stage ->
                    FeedingStagePick(
                        label = stage.displayName,
                        selected = startStage == stage.name,
                        onClick = { startStage = stage.name }
                    )
                }
            }
            Button(
                onClick = { pickedId?.let { onSaveSchedule(it, strength, startStage) } },
                enabled = pickedId != null
            ) {
                Text("Save schedule")
            }
            Spacer(Modifier.height(8.dp))
            if (schedules.isEmpty()) {
                Text(
                    "No schedules yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                schedules.forEach { schedule ->
                    FeedingScheduleRow(
                        schedule = schedule,
                        productName = nutrients.firstOrNull { it.id == schedule.nutrientId }?.name
                            ?: "Removed nutrient",
                        onDelete = { onDeleteSchedule(schedule) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Log feeding · ${formatDate(todayMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PhEcStepperRow(
                label = "Reservoir (L)",
                value = String.format(java.util.Locale.US, "%.1f", reservoir),
                onMinus = { reservoir = stepReservoir(reservoir, false) },
                onPlus = { reservoir = stepReservoir(reservoir, true) }
            )
            Text(
                text = "Dose: " + formatDose(
                    computeDose(
                        picked?.let(::dosageRuleOf), reservoir, strength
                    ),
                    picked?.doseUnit ?: "ml"
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    pickedId?.let { onLogFeeding(it, strength, reservoir) }
                    reservoir = 0.0
                },
                enabled = canLogFeeding(pickedId, reservoir)
            ) {
                Text("Log feeding")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (feedings.isEmpty()) {
            Text(
                "No feedings logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            feedings.forEach { entry ->
                FeedingEntryRow(
                    entry = entry,
                    productName = nutrients.firstOrNull { it.id == entry.nutrientId }?.name
                        ?: "Removed nutrient"
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FeedingStagePick(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            (if (selected) "● " else "○ ") + label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}

@Composable
private fun FeedingScheduleRow(
    schedule: FeedingSchedule,
    productName: String,
    onDelete: () -> Unit
) {
    var deleteGate by remember(schedule.id) { mutableStateOf<ConfirmGate<Unit>>(ConfirmGate.Idle) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$productName · ${schedule.strengthPercent}% · " +
                    (schedule.startStage?.let { GrowthStage.fromName(it).displayName }
                        ?: "anytime"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (deleteGate is ConfirmGate.Staged) {
                TextButton(onClick = { deleteGate = cancelAct() }) { Text("Cancel") }
                TextButton(onClick = {
                    val (act, next) = takeAct(deleteGate)
                    deleteGate = next
                    if (act != null) onDelete()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { deleteGate = stageAct(Unit) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FeedingEntryRow(entry: FeedingEntry, productName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatDate(entry.timestampEpochMs) +
                " · $productName · ${entry.strengthPercent}%" +
                " · ${formatDose(entry.doseAmount, entry.doseUnit)}" +
                " / ${String.format(java.util.Locale.US, "%.1f", entry.reservoirLiters)} L",
            style = MaterialTheme.typography.bodyMedium
        )
        if (entry.note.isNotBlank()) {
            Text(
                entry.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StageTimeline(
    current: GrowthStage,
    entries: List<StageEntryEntity>,
    onEdit: (GrowthStage) -> Unit,
    onDelete: (StageEntryEntity) -> Unit
) {
    Column {
        GrowthStage.entries.forEach { stage ->
            val entry = entries.firstOrNull { it.stage == stage }
            val reached = stage.order <= current.order
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (reached) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (reached) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stage.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (stage == current) FontWeight.Bold else FontWeight.Normal
                    )
                    val sub = when {
                        entry != null -> formatDate(entry.timestampEpochMs)
                        reached -> "Logged"
                        else -> "Pending"
                    }
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry?.note?.isNotBlank() == true) {
                        Text(entry.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onEdit(stage) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${stage.displayName} log")
                }
                if (entry != null) {
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${stage.displayName} log")
                    }
                }
            }
        }
    }
}

@Composable
private fun StageEntryForm(
    initialStage: GrowthStage,
    existing: StageEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (stage: GrowthStage, timestampMs: Long, note: String) -> Unit
) {
    var stage by remember { mutableStateOf(existing?.stage ?: initialStage) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var dateMs by remember {
        mutableStateOf(startOfDay(existing?.timestampEpochMs ?: System.currentTimeMillis()))
    }
    var stageExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (existing == null) "Add log" else "Edit log",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text("Stage", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { stageExpanded = !stageExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stage.displayName)
            }
            if (stageExpanded) {
                Column {
                    GrowthStage.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(
                                    selected = stage == option,
                                    onClick = { stage = option; stageExpanded = false }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = stage == option, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(option.displayName)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("What did you observe?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { dateMs -= MILLIS_PER_DAY }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                }
                Text(
                    formatDate(dateMs),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { dateMs += MILLIS_PER_DAY }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(stage, dateMs, note.trim()) }) { Text("Save") }
            }
        }
    }
}
