package com.farrah.sproutvision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.formatVerdictTrace
import com.farrah.sproutvision.analyze.verdictTrace
import com.farrah.sproutvision.data.BaselineEntity
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.ScanEntity
import com.farrah.sproutvision.data.StageEntryEntity
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.VarietyDefaults
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.daysUntil
import com.farrah.sproutvision.util.formatDate
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
    onDeleteScan: (ScanEntity) -> Unit,
    onHistory: () -> Unit,
    baseline: BaselineEntity?,
    onSnapshotBaseline: () -> Unit
) {
    var notes by remember(tray.id) { mutableStateOf(tray.notes) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingStage by remember { mutableStateOf<GrowthStage?>(null) }
    var deletingEntry by remember { mutableStateOf<StageEntryEntity?>(null) }
    var showClearScanConfirm by remember { mutableStateOf(false) }
    var deletingScan by remember { mutableStateOf<ScanEntity?>(null) }
    // Harvest confirm, keyed to the tray like notes: backing out clears it.
    var showHarvestConfirm by remember(tray.id) { mutableStateOf(false) }
    var showRemoveCardConfirm by remember(tray.id) { mutableStateOf(false) }
    val harvestAt = tray.expectedDaysToHarvest?.let { tray.seededAtEpochMs + it * MILLIS_PER_DAY }
    val daysLeft = harvestAt?.let { daysUntil(it) }
    val next = tray.currentStage.next()

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
                        onDelete = { deletingScan = scan }
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

}

@Composable
private fun ScanHistoryRow(scan: ScanEntity, onDelete: () -> Unit) {
    var traceExpanded by remember(scan.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { traceExpanded = !traceExpanded }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
