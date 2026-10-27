package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.VarietyScorecard
import kotlin.math.roundToInt

/**
 * Crop trial scorecard: one leaderboard row per variety, sorted by composite
 * score. Germination % and average health come from scan history; days to
 * harvest, yield, and taste are manual entries via the per-row edit form
 * (same card + dirty-gated Save pattern as the tray detail screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardScreen(
    scorecards: List<VarietyScorecard>,
    onSaveTrial: (variety: String, days: Int?, yieldGrams: Int?, taste: Int?) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop trial scorecard") },
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
        if (scorecards.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No varieties yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add trays and scan them — each variety lands on the leaderboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scorecards, key = { it.varietyKey }) { card ->
                    ScorecardRow(card = card, onSaveTrial = onSaveTrial)
                }
            }
        }
    }
}

@Composable
private fun ScorecardRow(
    card: VarietyScorecard,
    onSaveTrial: (variety: String, days: Int?, yieldGrams: Int?, taste: Int?) -> Unit
) {
    var editing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Germ ${card.germinationPct?.let { "$it%" } ?: "—"}" +
                            " · Health ${card.avgHealth?.let { "$it" } ?: "—"}" +
                            " · ${card.trayCount} tray(s), ${card.scanCount} scan(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${card.composite.roundToInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "SCORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Harvest ${card.daysToHarvest?.let { "day $it" } ?: "—"}" +
                    " · Yield ${card.yieldGrams?.let { "$it g" } ?: "—"}" +
                    " · Taste ${card.tasteRating?.let { "$it/5" } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (!editing) {
                OutlinedButton(onClick = { editing = true }) {
                    Text(if (card.daysToHarvest == null && card.yieldGrams == null && card.tasteRating == null) "Log trial data" else "Edit trial data")
                }
            } else {
                TrialEntryForm(
                    card = card,
                    onDismiss = { editing = false },
                    onSave = { days, yield, taste ->
                        onSaveTrial(card.displayName, days, yield, taste)
                        editing = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TrialEntryForm(
    card: VarietyScorecard,
    onDismiss: () -> Unit,
    onSave: (days: Int?, yieldGrams: Int?, taste: Int?) -> Unit
) {
    var days by remember { mutableStateOf(card.daysToHarvest?.toString().orEmpty()) }
    var yield by remember { mutableStateOf(card.yieldGrams?.toString().orEmpty()) }
    var taste by remember { mutableStateOf(card.tasteRating?.toString().orEmpty()) }

    val daysVal = days.trim().toIntOrNull()?.takeIf { it in 1..60 }
    val yieldVal = yield.trim().toIntOrNull()?.takeIf { it in 0..50_000 }
    val tasteVal = taste.trim().toIntOrNull()?.takeIf { it in 1..5 }
    val valid = (days.isBlank() || daysVal != null) &&
        (yield.isBlank() || yieldVal != null) &&
        (taste.isBlank() || tasteVal != null)
    val dirty = days.trim() != card.daysToHarvest?.toString().orEmpty() ||
        yield.trim() != card.yieldGrams?.toString().orEmpty() ||
        taste.trim() != card.tasteRating?.toString().orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = days,
                onValueChange = { days = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Days to harvest") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = days.isNotBlank() && daysVal == null,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = yield,
                onValueChange = { yield = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Yield (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = yield.isNotBlank() && yieldVal == null,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = taste,
                onValueChange = { taste = it.filter { c -> c.isDigit() }.take(1) },
                label = { Text("Taste 1–5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = taste.isNotBlank() && tasteVal == null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSave(daysVal, yieldVal, tasteVal) },
                enabled = valid && dirty
            ) {
                Text("Save")
            }
        }
    }
}
