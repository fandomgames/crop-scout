package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.analyze.GOLDEN_SHOT_IDS
import com.farrah.sproutvision.analyze.ValidationRow
import com.farrah.sproutvision.analyze.parseValidationReport

/**
 * Golden-tray validation table (read-only): SAM-measured coverage vs the
 * frozen by-eye numbers. Data comes from golden-validation.json in
 * assets (written Mac-side by :validation:validateGolden). Missing file,
 * malformed JSON, or null shots all render as —, never as 0% and never
 * a crash. No scan button, no actions — numbers only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidationScreen(
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rows = remember {
        try {
            val json = context.assets.open("golden-validation.json")
                .bufferedReader().use { it.readText() }
            parseValidationReport(json)
        } catch (_: Exception) {
            null
        } ?: GOLDEN_SHOT_IDS.map { ValidationRow(it, null, null, null) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Golden validation") },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValidationTableRow("Shot", "SAM %", "By-eye %", "Diff", header = true)
            rows.forEach { row ->
                ValidationTableRow(
                    shortId(row.id),
                    row.samCoverage?.toString() ?: "—",
                    row.byEye?.toString() ?: "—",
                    row.difference?.let { "%+d".format(it) } ?: "—"
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SAM counts literal plant pixels; humans perceive canopy — a gap is expected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shortId(id: String): String = id.removePrefix("shot").replace('-', ' ')

@Composable
private fun ValidationTableRow(
    shot: String,
    sam: String,
    byEye: String,
    diff: String,
    header: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValidationCell(shot, Modifier.weight(2f), header)
        ValidationCell(sam, Modifier.weight(1f), header)
        ValidationCell(byEye, Modifier.weight(1f), header)
        ValidationCell(diff, Modifier.weight(1f), header)
    }
}

@Composable
private fun ValidationCell(text: String, modifier: Modifier, header: Boolean) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
