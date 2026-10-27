package com.farrah.sproutvision.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farrah.sproutvision.data.GrowIntent
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.util.daysSince
import com.farrah.sproutvision.util.formatDate

/**
 * The grow HUD home: one compact card designed to sit in the corner of the
 * view while Farrah records her plants with the Quest system recorder. Big
 * touch targets (hand pinch / controller ray), no clutter in frame.
 *
 * [compact] is the dense layout for the 0.45 × 0.32 m vertical-capture
 * panel: same controls (tray switcher, Scan tray, Manage, 9:16 toggle),
 * just smaller, so every button stays reachable on the small panel.
 */
/** Trays shown in the HUD switcher: everything still growing. */
fun visibleHudTrays(trays: List<TrayEntity>): List<TrayEntity> =
    trays.filter { it.currentStage != GrowthStage.HARVEST }

/**
 * The HUD card's harvest line. Microgreen trays get the usual countdown;
 * grow-out trays get no countdown and no overdue warnings — the card
 * already shows day count, health, and coverage. Unset expected days
 * (trays are created without them) show no countdown either.
 */
fun hudHarvestText(day: Int, expectedDaysToHarvest: Int?, growIntent: GrowIntent): String {
    if (growIntent == GrowIntent.GROW_OUT) return "growing out"
    if (expectedDaysToHarvest == null) return "no harvest date"
    val harvestIn = expectedDaysToHarvest - day
    return when {
        harvestIn < 0 -> "Harvest overdue"
        harvestIn == 0 -> "Harvest today"
        else -> "Harvest in ~$harvestIn d"
    }
}

/**
 * Switcher selection: the current tray while still active, else the next
 * active tray after its position in the full list (wrapping). Null when no
 * active tray remains — the HUD shows its empty state.
 */
fun nextHudTray(trays: List<TrayEntity>, currentId: Long?): TrayEntity? {
    val visible = visibleHudTrays(trays)
    if (visible.isEmpty()) return null
    visible.firstOrNull { it.id == currentId }?.let { return it }
    val fullPos = trays.indexOfFirst { it.id == currentId }
    return visible.minBy { v ->
        val p = trays.indexOfFirst { it.id == v.id }
        if (p > fullPos) p else p + trays.size
    }
}

@Composable
fun HudHomeScreen(vm: SproutViewModel, trays: List<TrayEntity>, compact: Boolean = false) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Translucent panel scrim: the room and plants stay visible
    // around the HUD edges. Rule: no text over passthrough without a
    // backing behind it — every text sits on hudTextBacking() (or a
    // solid component surface), fixed white text, never theme colors
    // (both themes' on-surface/primary vanish on black).
    Surface(
        modifier = Modifier.fillMaxSize()
            .padding(
                horizontal = if (compact) 10.dp else 16.dp,
                vertical = if (compact) 8.dp else 12.dp
            ),
        shape = RoundedCornerShape(16.dp),
        color = hudPanelScrim()
    ) {
    // NOTE: no background gesture hides the panels — clean mode is
    // entered ONLY via the explicit Clean button below. A background
    // long-press used to live here and fired during press-dwell-drag
    // scroll attempts, hiding all panels mid-scroll (XR pinch aiming
    // routinely exceeds the long-press timeout before drag slop).
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header: title + capture-aid toggles + manage-panel toggle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SproutVision",
                style = if (compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f).hudTextBacking()
            )
            OutlinedButton(
                onClick = { vm.toggleVerticalVideo() },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = if (compact)
                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.ContentPadding
            ) {
                Text(
                    if (settings.verticalVideo) "9:16 ✓" else "9:16",
                    style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.hudTextBacking()
                )
            }
            // 9:16 only: hide the frame guide while keeping the compact
            // HUD (crop icon; dimmed while hidden). Backup lives in
            // Settings next to the Vertical video switch.
            if (compact) {
                IconButton(onClick = { vm.hideViewfinder = !vm.hideViewfinder }) {
                    Icon(
                        Icons.Filled.Crop,
                        contentDescription = if (vm.hideViewfinder) {
                            "Show frame guide"
                        } else {
                            "Hide frame guide"
                        },
                        tint = if (vm.hideViewfinder) {
                            Color.White.copy(alpha = 0.4f)
                        } else {
                            Color.White
                        }
                    )
                }
            }
            IconButton(onClick = { vm.panelLocked = !vm.panelLocked }) {
                Icon(
                    if (vm.panelLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (vm.panelLocked) "Unlock panels" else "Lock panels",
                    tint = Color.White
                )
            }
            IconButton(onClick = { vm.cleanMode = true }) {
                Icon(
                    Icons.Filled.VisibilityOff,
                    contentDescription = "Clean mode (hide panels)",
                    tint = Color.White
                )
            }
            OutlinedButton(
                onClick = { vm.manageVisible = true },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = if (compact)
                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.ContentPadding
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    "Manage",
                    style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.hudTextBacking()
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

        // Harvested trays are done growing: hidden from the working HUD
        // (they stay in Manage, history, and the scorecard).
        val visible = visibleHudTrays(trays)
        if (visible.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    vm.manageVisible = true
                    vm.manageOpenRequest = ManageScreen.AddTray
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = if (compact)
                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.ContentPadding
            ) {
                Text(
                    "Add your first tray",
                    style = if (compact) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            // Wild ID needs no trays, so it stays reachable on a fresh install.
            OutlinedButton(
                onClick = { vm.hudScreen = HudScreen.Identify },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = if (compact)
                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.ContentPadding
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    "Identify wild plant",
                    style = if (compact) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.hudTextBacking()
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            // If the current tray was harvested, the switcher advances to
            // the next active tray instead of sticking on a hidden one.
            val tray = nextHudTray(trays, vm.hudTrayId) ?: visible.first()
            // Harvest confirm, keyed to the tray: switching trays clears it.
            var confirmHarvest by remember(tray.id) { mutableStateOf(false) }
            val index = visible.indexOf(tray)
            fun select(i: Int) {
                vm.hudTrayId = visible[(i + visible.size) % visible.size].id
            }

            // Tray switcher — walk down the rack without opening Manage.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { select(index - 1) }) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Previous tray",
                        tint = Color.White
                    )
                }
                Text(
                    tray.name,
                    style = if (compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f).hudTextBacking(),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { select(index + 1) }) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next tray",
                        tint = Color.White
                    )
                }
            }

            HudTrayCard(
                tray = tray,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                compact = compact
            )

            // Quick actions — 56dp row, easy to hit with a pinch. The step
            // into HARVEST asks inline first (harvested trays leave the
            // HUD); the confirm swaps into this same footprint, keyed to
            // the tray, so it fits the compact panel too.
            if (confirmHarvest) {
                Text(
                    "Mark ${tray.name} harvested? It leaves the HUD.",
                    style = if (compact) MaterialTheme.typography.bodySmall
                        else MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().hudTextBacking(),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = if (compact) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    Button(
                        onClick = { confirmHarvest = false; vm.advanceStage(tray) },
                        modifier = Modifier.weight(1f).height(if (compact) 48.dp else 52.dp),
                        contentPadding = if (compact)
                            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        else ButtonDefaults.ContentPadding
                    ) {
                        Text(
                            "Harvest",
                            style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedButton(
                        onClick = { confirmHarvest = false },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(if (compact) 48.dp else 52.dp),
                        contentPadding = if (compact)
                            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        else ButtonDefaults.ContentPadding
                    ) {
                        Text(
                            "Keep growing",
                            style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelLarge,
                            modifier = Modifier.hudTextBacking()
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = if (compact) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    Button(
                        onClick = { vm.hudScreen = HudScreen.Scan(tray.id) },
                        modifier = Modifier.weight(1f).height(if (compact) 48.dp else 52.dp),
                        contentPadding = if (compact)
                            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        else ButtonDefaults.ContentPadding
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                        Text(
                            "Scan tray",
                            style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (tray.currentStage.needsHarvestConfirm()) confirmHarvest = true
                            else vm.advanceStage(tray)
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(if (compact) 48.dp else 52.dp),
                        contentPadding = if (compact)
                            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        else ButtonDefaults.ContentPadding
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                        Text(
                            "Log stage",
                            style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.labelLarge,
                            modifier = Modifier.hudTextBacking()
                        )
                    }
                }
                // Wild-plant ID beside the grow actions: separate mode, no tray needed.
                OutlinedButton(
                    onClick = { vm.hudScreen = HudScreen.Identify },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 52.dp),
                    contentPadding = if (compact)
                        PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    else ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                    Text(
                        "Identify wild plant",
                        style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.labelLarge,
                        modifier = Modifier.hudTextBacking()
                    )
                }
            }
        }

        // The recording hint is dual-mode advice — compact IS the recording
        // mode, so the footer is dropped there to free 20dp for the card.
        if (!compact) {
            Text(
                "Recording? Start the Quest recorder — this HUD stays in frame.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth().hudTextBacking(),
                textAlign = TextAlign.Center
            )
        }
    }
    }
}

@Composable
private fun HudTrayCard(tray: TrayEntity, modifier: Modifier = Modifier, compact: Boolean = false) {
    val day = daysSince(tray.seededAtEpochMs)
    val harvestText = hudHarvestText(day, tray.expectedDaysToHarvest, tray.growIntent)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Day $day",
                    style = if (compact) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.headlineMedium
                )
                StageBadge(stage = tray.currentStage, compact = compact, backed = true)
            }
            Text(
                "${tray.variety} · seeded ${formatDate(tray.seededAtEpochMs)}",
                style = if (compact) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge
            )
            val health = tray.lastHealthScore
            Text(
                if (health != null)
                    "Health $health% · coverage ${tray.lastCoveragePercent ?: "?"}% · $harvestText"
                else
                    "Not scanned yet · $harvestText",
                style = if (compact) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge
            )
            if (tray.lastIssues.isNotBlank()) {
                Text(
                    tray.lastIssues,
                    style = if (compact) MaterialTheme.typography.bodySmall
                        else MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
        }
    }
}
