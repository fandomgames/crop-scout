package com.farrah.sproutvision.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farrah.sproutvision.analyze.AnalysisResult
import kotlinx.coroutines.delay

/** Elapsed milliseconds as tenths of a second ("4.2s"). Locale-independent. */
fun formatElapsed(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    return "${safe / 1000}.${(safe % 1000) / 100}s"
}

/** Rolling typical duration ("usually ~8s"), or a measuring placeholder. */
fun formatTypical(typicalMs: Long?): String =
    if (typicalMs == null) "measuring…"
    else "usually ~${(typicalMs + 500) / 1000}s"

/**
 * Live analyzing status: what the pipeline is doing, an elapsed timer, the
 * typical duration for this analyzer, and the last few timestamped feed
 * lines — so a stall is visible while it happens, not afterwards. The
 * timer ticks at 5 Hz — cheap enough for the HUD panel.
 */
/**
 * @param textBacked backs every line for translucent HUD scrims;
 * theme-background callers (scan/identify screens) leave it off.
 */
@Composable
fun AnalyzeStatusSection(
    stage: String,
    startedAtMs: Long,
    typicalMs: Long?,
    modifier: Modifier = Modifier,
    contentColor: Color,
    events: List<AnalyzeEvent> = emptyList(),
    textBacked: Boolean = false
) {
    var nowMs by remember(startedAtMs) { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            delay(200)
            nowMs = SystemClock.elapsedRealtime()
        }
    }
    val backing = if (textBacked) Modifier.hudTextBacking() else Modifier

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (stage.isNotBlank()) {
            Text(
                text = stage,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = backing
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Elapsed ${formatElapsed(nowMs - startedAtMs)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = backing
            )
            Text(
                text = formatTypical(typicalMs),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.75f),
                modifier = backing
            )
        }
        events.takeLast(3).forEach { e ->
            Text(
                text = "+${formatElapsed(e.elapsedMs)}  ${e.text}",
                fontSize = 14.sp,
                color = contentColor.copy(alpha = 0.85f),
                modifier = backing
            )
        }
    }
}

/**
 * Framing coaching from an analysis result: tells the grower whether the
 * headset view needs adjusting for a better analysis — tray missing, cut
 * off, too small, or off-center. Null means no adjustment needed (or, for
 * scripted demo results, no box to judge).
 */
fun framingAdvice(result: AnalysisResult): String? {
    if (!result.trayVisible) {
        return "No tray visible — aim the headset at the tray."
    }
    val box = result.trayBox ?: return null
    val edge = 1000 - EDGE_PX
    if (box.x0 <= EDGE_PX || box.y0 <= EDGE_PX || box.x1 >= edge || box.y1 >= edge) {
        return "Tray is cut off at the frame edge — pull back or recenter."
    }
    val areaFrac = (box.x1 - box.x0).coerceAtLeast(0) *
        (box.y1 - box.y0).coerceAtLeast(0) / 1_000_000f
    if (areaFrac < MIN_AREA_FRAC) {
        return "Tray fills only ${(areaFrac * 100).toInt()}% of the frame — move closer."
    }
    val cx = (box.x0 + box.x1) / 2
    val cy = (box.y0 + box.y1) / 2
    if (cx < CENTER_MIN) return "Tray is left of center — aim a bit left."
    if (cx > CENTER_MAX) return "Tray is right of center — aim a bit right."
    if (cy < CENTER_MIN) return "Tray is high in frame — aim a bit up."
    if (cy > CENTER_MAX) return "Tray is low in frame — aim a bit down."
    return null
}

private const val EDGE_PX = 15
private const val MIN_AREA_FRAC = 0.10f
private const val CENTER_MIN = 300
private const val CENTER_MAX = 700

/**
 * One-line framing coaching. Renders nothing when no adjustment is needed.
 */
/**
 * @param textBacked backs the line for translucent HUD scrims;
 * theme-background callers (scan screen) leave it off.
 */
@Composable
fun FramingAdviceLine(
    result: AnalysisResult,
    modifier: Modifier = Modifier,
    contentColor: Color,
    textBacked: Boolean = false
) {
    val advice = remember(result) { framingAdvice(result) }
    if (advice != null) {
        Text(
            text = "◉ $advice",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = if (textBacked) modifier.hudTextBacking() else modifier
        )
    }
}
