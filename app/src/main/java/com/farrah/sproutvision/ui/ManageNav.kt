package com.farrah.sproutvision.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farrah.sproutvision.util.ScanExportResult
import kotlinx.coroutines.launch

/**
 * Navigation for the large tray-management panel. The HUD panel owns Scan and
 * Result now — from here, "Scan tray" drops back to the HUD and opens its
 * scanner, so recording is never interrupted by panel-hopping.
 */
sealed interface ManageScreen {
    data object Dashboard : ManageScreen
    data object AddTray : ManageScreen
    data object Settings : ManageScreen
    data object Scorecard : ManageScreen
    data object Herbarium : ManageScreen
    data object Validation : ManageScreen
    data class Detail(val trayId: Long) : ManageScreen
    data class History(val trayId: Long) : ManageScreen
    data class HerbariumDetail(val entryId: Long) : ManageScreen
}

@Composable
fun ManageNav(vm: SproutViewModel) {
    var screen by remember { mutableStateOf<ManageScreen>(ManageScreen.Dashboard) }
    val trays by vm.trays.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val herbarium by vm.herbarium.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Back on the manage panel hides the panel (only when it's visible).
    BackHandler(enabled = vm.manageVisible) { vm.manageVisible = false }

    // One-shot deep-link from the HUD (e.g. empty state → Add tray).
    LaunchedEffect(vm.manageOpenRequest) {
        vm.manageOpenRequest?.let {
            screen = it
            vm.manageOpenRequest = null
        }
    }

    when (val s = screen) {
        is ManageScreen.Dashboard -> DashboardScreen(
            trays = trays,
            onTrayClick = { screen = ManageScreen.Detail(it) },
            onAdd = { screen = ManageScreen.AddTray },
            onSettings = { screen = ManageScreen.Settings },
            onScorecard = { screen = ManageScreen.Scorecard },
            onHerbarium = { screen = ManageScreen.Herbarium },
            onValidation = { screen = ManageScreen.Validation },
            placementMode = vm.placementMode,
            onTogglePlacement = { vm.placementMode = !vm.placementMode },
            onExportHistory = {
                scope.launch {
                    val outcome = vm.exportScanHistory(context)
                    val message = when (outcome) {
                        ScanExportResult.Empty -> "No scans yet — nothing to export."
                        is ScanExportResult.Saved -> "Saved ${outcome.fileName} to Downloads."
                        ScanExportResult.Failed -> "Export failed."
                    }
                    // Application context: a Toast on the panel activity
                    // context crashes on Quest (window type mismatch).
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            },
            onClose = { vm.manageVisible = false }
        )

        is ManageScreen.AddTray -> {
            BackHandler { screen = ManageScreen.Dashboard }
            AddTrayScreen(
                savedVarieties = settings.customVarieties,
                onSave = { name, variety, seededAt ->
                    vm.addTray(name, variety, seededAt)
                    screen = ManageScreen.Dashboard
                },
                onCancel = { screen = ManageScreen.Dashboard },
                onClose = { vm.manageVisible = false }
            )
        }

        is ManageScreen.Detail -> {
            val tray by vm.observeTray(s.trayId).collectAsStateWithLifecycle(initialValue = null)
            val stages by vm.observeStages(s.trayId).collectAsStateWithLifecycle(initialValue = emptyList())
            val scans by vm.observeScans(s.trayId).collectAsStateWithLifecycle(initialValue = emptyList())
            val baseline by vm.observeBaseline(s.trayId)
                .collectAsStateWithLifecycle(initialValue = null)
            BackHandler { screen = ManageScreen.Dashboard }
            val current = tray
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TrayDetailScreen(
                    tray = current,
                    stages = stages,
                    scans = scans,
                    onBack = { screen = ManageScreen.Dashboard },
                    onScan = {
                        vm.manageVisible = false
                        vm.hudTrayId = current.id
                        vm.hudScreen = HudScreen.Scan(current.id)
                    },
                    onAdvance = { vm.advanceStage(current) },
                    onSetGrowIntent = { vm.setGrowIntent(current, it) },
                    onSetExpectedDays = { vm.updateExpectedDays(current, it) },
                    onClearCardAnchor = { vm.clearTrayAnchor(current.id) },
                    onSaveNotes = { vm.saveNotes(current, it) },
                    onSaveEntry = { existing, stage, timestampMs, note ->
                        vm.saveStageEntry(current, existing, stage, timestampMs, note)
                    },
                    onDeleteEntry = { vm.deleteStageEntry(current, it) },
                    onDelete = {
                        vm.deleteTray(current)
                        screen = ManageScreen.Dashboard
                    },
                    onClose = { vm.manageVisible = false },
                    onClearAnalysis = { vm.clearAnalysis(current) },
                    onDeleteScan = { vm.deleteScan(current, it) },
                    onHistory = { screen = ManageScreen.History(current.id) },
                    baseline = baseline,
                    onSnapshotBaseline = { vm.snapshotBaseline(current) }
                )
            }
        }

        is ManageScreen.Scorecard -> {
            val scorecards by vm.scorecards.collectAsStateWithLifecycle()
            BackHandler { screen = ManageScreen.Dashboard }
            ScorecardScreen(
                scorecards = scorecards,
                onSaveTrial = vm::saveTrial,
                onBack = { screen = ManageScreen.Dashboard },
                onClose = { vm.manageVisible = false }
            )
        }

        is ManageScreen.Validation -> {
            BackHandler { screen = ManageScreen.Dashboard }
            ValidationScreen(
                onBack = { screen = ManageScreen.Dashboard },
                onClose = { vm.manageVisible = false }
            )
        }

        is ManageScreen.History -> {
            val tray by vm.observeTray(s.trayId).collectAsStateWithLifecycle(initialValue = null)
            val records by vm.observeScanRecords(s.trayId)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            BackHandler { screen = ManageScreen.Detail(s.trayId) }
            val current = tray
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ScanHistoryScreen(
                    tray = current,
                    records = records,
                    onBack = { screen = ManageScreen.Detail(s.trayId) },
                    onClose = { vm.manageVisible = false }
                )
            }
        }

        is ManageScreen.Herbarium -> {
            BackHandler { screen = ManageScreen.Dashboard }
            HerbariumGridScreen(
                entries = herbarium,
                onOpen = { screen = ManageScreen.HerbariumDetail(it) },
                onBack = { screen = ManageScreen.Dashboard },
                onClose = { vm.manageVisible = false }
            )
        }

        is ManageScreen.HerbariumDetail -> {
            val entry = herbarium.firstOrNull { it.id == s.entryId }
            BackHandler { screen = ManageScreen.Herbarium }
            if (entry == null) {
                // Deleted (or still loading) — fall back to the grid.
                LaunchedEffect(Unit) { screen = ManageScreen.Herbarium }
            } else {
                HerbariumDetailScreen(
                    entry = entry,
                    onBack = { screen = ManageScreen.Herbarium },
                    onClose = { vm.manageVisible = false },
                    onDelete = {
                        vm.deleteHerbariumEntry(entry)
                        screen = ManageScreen.Herbarium
                    }
                )
            }
        }

        is ManageScreen.Settings -> {
            BackHandler { screen = ManageScreen.Dashboard }
            SettingsScreen(
                settings = settings,
                onApiKey = vm::setApiKey,
                onMetaApiKey = vm::setMetaApiKey,
                onUseMetaAi = vm::setUseMetaAi,
                onUseSamMeasurement = vm::setUseSamMeasurement,
                onDailyCap = vm::setDailyDetectionCap,
                onDemoMode = vm::setDemoMode,
                onDarkMode = vm::setDarkMode,
                onHighlightTray = vm::setHighlightTray,
                onVoiceReadout = vm::setVoiceReadout,
                onScanChime = vm::setScanChime,
                onVerticalVideo = vm::setVerticalVideo,
                hideViewfinder = vm.hideViewfinder,
                onHideViewfinder = { vm.hideViewfinder = it },
                onNotifications = vm::setNotificationsEnabled,
                trays = trays,
                onDeleteTray = vm::deleteTray,
                onClearData = vm::clearAllData,
                onLoadDemoTrays = vm::loadDemoTrays,
                onBack = { screen = ManageScreen.Dashboard },
                onClose = { vm.manageVisible = false }
            )
        }
    }
}
