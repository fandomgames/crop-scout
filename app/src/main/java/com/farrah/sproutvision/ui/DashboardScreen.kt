package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.util.MILLIS_PER_DAY
import com.farrah.sproutvision.util.daysUntil
import com.farrah.sproutvision.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    trays: List<TrayEntity>,
    onTrayClick: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onScorecard: () -> Unit,
    onHerbarium: () -> Unit,
    onExportHistory: () -> Unit,
    placementMode: Boolean,
    onTogglePlacement: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SproutVision") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add tray")
            }
        }
    ) { padding ->
        if (trays.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No trays yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to log your first tray.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onHerbarium) {
                    Text("Herbarium")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onScorecard,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scorecard")
                        }
                        OutlinedButton(
                            onClick = onHerbarium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Herbarium")
                        }
                        OutlinedButton(
                            onClick = onExportHistory,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export scan history")
                        }
                        OutlinedButton(
                            onClick = onTogglePlacement,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (placementMode) "Done placing" else "Place tray cards")
                        }
                    }
                }
                items(trays, key = { it.id }) { tray ->
                    TrayCard(tray = tray, onClick = { onTrayClick(tray.id) })
                }
            }
        }
    }
}

@Composable
fun TrayCard(tray: TrayEntity, onClick: () -> Unit) {
    val expected = tray.expectedDaysToHarvest
    val harvestAt = expected?.let { tray.seededAtEpochMs + it * MILLIS_PER_DAY }
    val daysLeft = harvestAt?.let { daysUntil(it) }
    val elapsed = if (expected != null && daysLeft != null) {
        (expected - daysLeft).coerceIn(0, expected)
    } else 0
    val progress = if (expected != null && expected > 0) {
        elapsed.toFloat() / expected.toFloat()
    } else 0f

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tray.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                StageBadge(tray.currentStage)
            }
            Text(
                tray.variety,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Grow-out trays and trays without expected days skip the
            // harvest countdown bar and warnings.
            if (tray.growIntent != GrowIntent.GROW_OUT && daysLeft != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }
            val status = if (tray.growIntent == GrowIntent.GROW_OUT) {
                "Growing out · seeded ${formatDate(tray.seededAtEpochMs)}"
            } else {
                val left = daysLeft
                if (left == null) {
                    "No harvest date set · seeded ${formatDate(tray.seededAtEpochMs)}"
                } else if (left >= 0) {
                    "Harvest in ~$left day(s) · seeded ${formatDate(tray.seededAtEpochMs)}"
                } else {
                    "Harvest overdue by ${-left} day(s) · seeded ${formatDate(tray.seededAtEpochMs)}"
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (tray.lastHealthScore != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last scan: health ${tray.lastHealthScore}/100" +
                        (if (tray.lastAnalyzedByDemo) " (demo)" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
/**
 * @param backed swaps the translucent tint for a solid dark backing
 * for translucent HUD scrims; theme-background callers leave it off.
 */
fun StageBadge(stage: GrowthStage, compact: Boolean = false, backed: Boolean = false) {
    val color = when (stage) {
        GrowthStage.SEEDED -> Color(0xFF9E9E9E)
        GrowthStage.GERMINATION -> Color(0xFF66BB6A)
        GrowthStage.BLACKOUT -> Color(0xFF616161)
        GrowthStage.LIGHT -> Color(0xFF2E7D32)
        GrowthStage.HARVEST -> Color(0xFFF9A825)
    }
    Surface(
        color = if (backed) hudTextBackingColor() else color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            stage.displayName,
            color = color,
            style = if (compact) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp
            )
        )
    }
}
