package com.farrah.sproutvision.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farrah.sproutvision.analyze.TrayBox
import com.farrah.sproutvision.data.BaselineEntity
import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.TrayEntity
import com.farrah.sproutvision.data.VarietyGuides
import com.farrah.sproutvision.util.ScanChime
import com.farrah.sproutvision.util.ShareImage
import com.farrah.sproutvision.util.VoiceReadout
import com.farrah.sproutvision.util.daysSince
import com.farrah.sproutvision.util.formatDate
import com.farrah.sproutvision.util.verdictUtterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Compact scan result for the HUD panel: detected stage, health, issues and
 * recommendation, with Apply / Save / Discard. Fits the small HUD panel so
 * Farrah never has to leave the recording view to log a scan.
 */
@Composable
fun HudResultScreen(
    vm: SproutViewModel,
    tray: TrayEntity,
    highlightEnabled: Boolean,
    baseline: BaselineEntity?,
    voiceEnabled: Boolean,
    chimeEnabled: Boolean,
    onDone: () -> Unit
) {
    val result = vm.pendingAnalysis
    val analyzing = vm.analyzing
    val error = vm.lastError
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cinematic beat: the sweep + zone boxes play over the captured frame
    // first; results (and the voice verdict) land when it finishes.
    val stamp = vm.pendingScanTimestamp
    var cinematicDone by remember(stamp) { mutableStateOf(false) }
    val revealed = result != null && (!result.trayVisible || cinematicDone)
    LaunchedEffect(stamp, revealed) {
        if (!revealed) return@LaunchedEffect
        // Chime first (independent toggle), then the spoken verdict.
        if (chimeEnabled) ScanChime.play()
        if (voiceEnabled) {
            val r = vm.pendingAnalysis ?: return@LaunchedEffect
            if (r.trayVisible) {
                VoiceReadout.speak(
                    context,
                    verdictUtterance(
                        tray.variety,
                        daysSince(tray.seededAtEpochMs),
                        r.healthScore,
                        r.issues.firstOrNull()
                    )
                )
            } else {
                VoiceReadout.speak(context, "No tray detected.")
            }
        }
    }

    // Translucent panel scrim: the room and plants stay visible
    // around the HUD edges. Rule: no text over passthrough without a
    // backing behind it — every text sits on hudTextBacking() (or a
    // solid component surface), fixed white text, never theme colors
    // (both themes' on-surface/primary vanish on black).
    Surface(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = hudPanelScrim()
    ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Scan: ${tray.name}",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f).hudTextBacking()
            )
            TextButton(onClick = { vm.discardAnalysis(); onDone() }) {
                Text("Close", color = Color.White, modifier = Modifier.hudTextBacking())
            }
        }

        when {
            analyzing -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Analyzing…",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally).hudTextBacking()
                )
                Spacer(Modifier.height(12.dp))
                AnalyzeStatusSection(
                    stage = vm.analyzeStage,
                    startedAtMs = vm.analyzeStartedAtMs,
                    typicalMs = vm.typicalAnalyzeMs,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    events = vm.analyzeEvents,
                    textBacked = true
                )
                Spacer(Modifier.weight(1f))
            }

            error != null -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF5252),
                    modifier = Modifier.hudTextBacking()
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { vm.hudScreen = HudScreen.Scan(tray.id) },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rescan", color = Color.White, modifier = Modifier.hudTextBacking())
                }
            }

            result != null -> if (!result.trayVisible) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No tray detected", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("No grow tray was visible in the capture. Aim at a tray and try again.")
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { vm.discardAnalysis(); vm.hudScreen = HudScreen.Scan(tray.id) }) {
                                    Text("Retry scan")
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { vm.discardAnalysis(); onDone() }) {
                                    Text("Dismiss", modifier = Modifier.hudTextBacking())
                                }
                            }
                        }
                    }
                }
            } else if (!cinematicDone) {
                CinematicScanOverlay(
                    photo = vm.pendingPhoto,
                    result = result,
                    onFinished = { cinematicDone = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VerdictCard(tray = tray, result = result)
                    baseline?.let { before ->
                        BaselineDeltaCard(
                            beforeHealth = before.healthScore,
                            beforeIssues = splitIssues(before.issues),
                            beforeLabel = formatDate(before.timestampEpochMs),
                            nowHealth = result.healthScore,
                            nowIssues = result.issues,
                            nowLabel = formatDate(stamp)
                        )
                    }
                    val photo = vm.pendingPhoto
                    val samOverlay = vm.pendingSamOverlay
                    val overlay = remember(samOverlay) { samOverlay?.asImageBitmap() }
                    if (highlightEnabled && photo != null &&
                        (result.trayBox != null || overlay != null)
                    ) {
                        TrayPhotoHighlight(photo = photo, box = result.trayBox, overlay = overlay)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Detected: ${result.stage.displayName}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.hudTextBacking()
                        )
                        Text(
                            "${result.healthScore}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.hudTextBacking()
                        )
                    }
                    Text(
                        "Coverage ${result.coveragePercent}%" +
                            if (result.isDemo) " · demo analysis" else "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.hudTextBacking()
                    )
                    if (result.issues.isNotEmpty()) {
                        Text(
                            result.issues.take(3).joinToString("\n") { "• $it" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.hudTextBacking()
                        )
                    }
                    if (result.recommendation.isNotBlank()) {
                        Text(
                            result.recommendation,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.hudTextBacking()
                        )
                    }
                    // Framing coaching for the next scan (hidden when fine).
                    FramingAdviceLine(
                        result = result,
                        contentColor = Color(0xFFFFB300),
                        textBacked = true
                    )
                    GrowGuideCard(tray)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.applyDetectedStage(tray); onDone() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply stage")
                    }
                    OutlinedButton(
                        onClick = { vm.saveAnalysis(tray); onDone() },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", color = Color.White, modifier = Modifier.hudTextBacking())
                    }
                    OutlinedButton(
                        onClick = {
                            val photo = vm.pendingPhoto
                            val stamp = vm.pendingScanTimestamp
                            scope.launch(Dispatchers.IO) {
                                try {
                                    ShareImage.share(
                                        context.applicationContext,
                                        photo,
                                        tray.name,
                                        stamp,
                                        result.healthScore,
                                        result.coveragePercent,
                                        result.issues.joinToString("; ")
                                    )
                                } catch (e: Exception) {
                                    Log.e("SproutShare", "Share failed", e)
                                    withContext(Dispatchers.Main) {
                                        // Application context: a Toast on the
                                        // panel activity context crashes on Quest
                                        // (window type mismatch).
                                        Toast.makeText(
                                            context.applicationContext,
                                            "Share failed: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share", color = Color.White, modifier = Modifier.hudTextBacking())
                    }
                    TextButton(onClick = { vm.discardAnalysis(); onDone() }) {
                        Text("Discard", color = Color.White, modifier = Modifier.hudTextBacking())
                    }
                }
            }
        }
    }
    }
}

/**
 * Variety targets for the tray being logged: day count, rough expected
 * height now, blackout length, and harvest height. Full-contrast text so it
 * stays readable on the small HUD panel.
 */
@Composable
private fun GrowGuideCard(tray: TrayEntity) {
    val guide = remember(tray.variety) { VarietyGuides.forVariety(tray.variety) }
    val day = daysSince(tray.seededAtEpochMs)
    val expectedDays = tray.expectedDaysToHarvest
    val expected = expectedDays?.let { VarietyGuides.expectedHeightInches(guide, day, it) }
    val blackoutDone = tray.currentStage.order > GrowthStage.BLACKOUT.order
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Grow guide · ${tray.variety}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Day $day" + (expectedDays?.let { " of ~$it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium
            )
            if (expected != null || day <= 0) {
                Text(
                    if (day <= 0) "Just seeded — too early to measure"
                    else "Should be about ${(expected!! * 10).roundToInt() / 10.0} in now (estimate)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Blackout: ${guide.blackoutLabel()}" + if (blackoutDone) " (done)" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Harvest at ${guide.harvestLabel()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TrayPhotoHighlight(photo: ByteArray, box: TrayBox?, overlay: ImageBitmap?) {
    val bitmap = remember(photo) {
        BitmapFactory.decodeByteArray(photo, 0, photo.size)?.asImageBitmap()
    }
    if (bitmap == null) return
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        Image(
            bitmap = bitmap,
            contentDescription = "Captured tray photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (overlay != null) {
            Image(
                bitmap = overlay,
                contentDescription = "Measured plant pixels",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (box != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(box.x0 / 1000f * size.width, box.y0 / 1000f * size.height),
                    size = Size(
                        (box.x1 - box.x0).coerceAtLeast(0) / 1000f * size.width,
                        (box.y1 - box.y0).coerceAtLeast(0) / 1000f * size.height
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}
