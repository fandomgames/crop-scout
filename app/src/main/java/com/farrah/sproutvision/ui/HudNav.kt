package com.farrah.sproutvision.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Screens for the compact grow-HUD panel (the one that stays visible while
 * recording). Kept separate from the tray-management panel's nav so the HUD
 * stays small and readable on video.
 */
sealed interface HudScreen {
    data object Home : HudScreen
    data class Scan(val trayId: Long) : HudScreen
    data class Result(val trayId: Long) : HudScreen
    /** Wild-plant capture (no tray — fully separate from tray scanning). */
    data object Identify : HudScreen
    /** Wild-plant identification card (never the tray result screen). */
    data object IdentifyResult : HudScreen
}

@Composable
fun HudNav(vm: SproutViewModel, compact: Boolean = false) {
    val trays by vm.trays.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    when (val s = vm.hudScreen) {
        // Scan/Result need no compact variant: the scan preview is weight-
        // based and the result column scrolls, so both fit the small panel.
        is HudScreen.Home -> HudHomeScreen(vm = vm, trays = trays, compact = compact)

        is HudScreen.Scan -> {
            val tray = trays.firstOrNull { it.id == s.trayId }
            if (tray == null) {
                LaunchedEffect(Unit) { vm.hudScreen = HudScreen.Home }
            } else {
                // Leaving mid-scan cancels the in-flight analysis, and a
                // finished run only advances if this scan is still showing
                // (no yank to Result after the user backed out).
                BackHandler { vm.cancelAnalyze(); vm.hudScreen = HudScreen.Home }
                ScanScreen(
                    vm = vm,
                    tray = tray,
                    demoMode = settings.demoMode,
                    analyzing = vm.analyzing,
                    onAnalyze = { bytes ->
                        vm.analyze(tray, bytes) {
                            if (vm.hudScreen == HudScreen.Scan(tray.id)) {
                                vm.hudScreen = HudScreen.Result(tray.id)
                            }
                        }
                    },
                    onCancel = { vm.cancelAnalyze(); vm.hudScreen = HudScreen.Home },
                    // Only the visible HUD opens the camera — a second
                    // openCamera would disconnect the live session.
                    cameraEnabled = scanCameraEnabled(compact, vm.verticalHudActive)
                )
            }
        }

        is HudScreen.Result -> {
            val tray = trays.firstOrNull { it.id == s.trayId }
            if (tray == null) {
                LaunchedEffect(Unit) { vm.hudScreen = HudScreen.Home }
            } else {
                val baseline by vm.observeBaseline(s.trayId)
                    .collectAsStateWithLifecycle(initialValue = null)
                BackHandler {
                    vm.discardAnalysis()
                    vm.hudScreen = HudScreen.Home
                }
                HudResultScreen(
                    vm = vm,
                    tray = tray,
                    highlightEnabled = settings.highlightTray,
                    baseline = baseline,
                    voiceEnabled = settings.voiceReadout,
                    chimeEnabled = settings.scanChime,
                    onDone = { vm.hudScreen = HudScreen.Home }
                )
            }
        }

        is HudScreen.Identify -> {
            // Leaving mid-ID cancels the in-flight run, and a finished run
            // only advances if this capture is still showing with a result
            // (a failed run stays here showing the error).
            BackHandler { vm.cancelIdentify(); vm.hudScreen = HudScreen.Home }
            IdentifyScreen(
                vm = vm,
                demoMode = settings.demoMode,
                identifying = vm.identifying,
                onIdentify = { bytes ->
                    vm.identifyPlant(bytes) {
                        if (vm.hudScreen == HudScreen.Identify &&
                            vm.pendingIdentification != null
                        ) {
                            vm.hudScreen = HudScreen.IdentifyResult
                        }
                    }
                },
                onCancel = { vm.cancelIdentify(); vm.hudScreen = HudScreen.Home },
                // Only the visible HUD opens the camera — a second
                // openCamera would disconnect the live session.
                cameraEnabled = scanCameraEnabled(compact, vm.verticalHudActive)
            )
        }

        is HudScreen.IdentifyResult -> {
            val result = vm.pendingIdentification
            if (result == null) {
                LaunchedEffect(Unit) { vm.hudScreen = HudScreen.Home }
            } else {
                BackHandler {
                    vm.discardIdentification()
                    vm.hudScreen = HudScreen.Home
                }
                IdentifyResultScreen(
                    vm = vm,
                    result = result,
                    onDone = {
                        vm.discardIdentification()
                        vm.hudScreen = HudScreen.Home
                    }
                )
            }
        }
    }
}
