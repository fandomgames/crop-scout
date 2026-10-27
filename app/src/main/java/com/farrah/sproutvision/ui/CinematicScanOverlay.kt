package com.farrah.sproutvision.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farrah.sproutvision.R
import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.analyze.TrayBox

/**
 * The signature on-camera beat: a ~2.5s scan sequence played over the
 * captured frame before results appear. A horizontal sweep line travels down
 * the photo while bounding boxes animate in around each detected problem
 * zone, with an "ANALYZING…" indicator pinned to the bottom.
 *
 * Deliberately lightweight for the Quest 3 mobile GPU: one bitmap, one
 * one-shot sweep animation, one alpha pulse, plain Canvas rects — no
 * particles, no blur, no per-frame allocations outside draw. Tap skips.
 */
@Composable
fun CinematicScanOverlay(
    photo: ByteArray?,
    result: AnalysisResult,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(photo) {
        if (photo == null || photo.isEmpty()) null
        else try {
            BitmapFactory.decodeByteArray(photo, 0, photo.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val placeholder = painterResource(id = R.drawable.placeholder_tray)
    // Aspect of whatever is displayed, so boxes map onto the fitted image.
    val imageAspect = bitmap?.let { it.width.toFloat() / it.height.toFloat() }
        ?: with(placeholder.intrinsicSize) { if (height > 0) width / height else 4f / 3f }

    val zones = remember(result) { problemZones(result) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(1f, animationSpec = tween(SWEEP_MS, easing = LinearEasing))
        onFinished()
    }

    val pulse by rememberInfiniteTransition(label = "analyzing-pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "analyzing-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
            .semantics { contentDescription = "Analyzing tray scan" }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = placeholder,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            // Fit-rect of the image inside this canvas (matches ContentScale.Fit).
            var fitW = size.width
            var fitH = fitW / imageAspect
            if (fitH > size.height) {
                fitH = size.height
                fitW = fitH * imageAspect
            }
            val offX = (size.width - fitW) / 2f
            val offY = (size.height - fitH) / 2f
            fun mapX(v: Int) = offX + v / 1000f * fitW
            fun mapY(v: Int) = offY + v / 1000f * fitH

            // Dim the frame slightly so the sweep and boxes read on video.
            drawRect(Color.Black.copy(alpha = 0.25f))

            // Sweep band + bright line.
            val sweepY = offY + sweep.value * fitH
            drawRect(
                color = Color(0xFF7CFC00).copy(alpha = 0.10f),
                topLeft = Offset(offX, (sweepY - size.height * 0.06f).coerceAtLeast(offY)),
                size = Size(fitW, size.height * 0.06f)
            )
            drawLine(
                color = Color(0xFF7CFC00),
                start = Offset(offX, sweepY),
                end = Offset(offX + fitW, sweepY),
                strokeWidth = 3.dp.toPx()
            )

            // Problem-zone boxes fade/scale in as the sweep passes them.
            zones.forEachIndexed { i, zone ->
                val appearAt = 0.12f + i * 0.22f
                val t = ((sweep.value - appearAt) / 0.18f).coerceIn(0f, 1f)
                if (t > 0f) {
                    val x0 = mapX(zone.x0)
                    val y0 = mapY(zone.y0)
                    val w = (mapX(zone.x1) - x0).coerceAtLeast(1f)
                    val h = (mapY(zone.y1) - y0).coerceAtLeast(1f)
                    // Scale-in: grow from the box center.
                    val inset = (1f - t) * 0.18f
                    val ix = w * inset / 2f
                    val iy = h * inset / 2f
                    drawRect(
                        color = Color(0xFFFFB300).copy(alpha = t),
                        topLeft = Offset(x0 + ix, y0 + iy),
                        size = Size(w - ix * 2f, h - iy * 2f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Text(
                text = "ANALYZING…  ${result.issues.size} zone" +
                    (if (result.issues.size == 1) "" else "s"),
                color = Color.White.copy(alpha = pulse),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

/** Sweep duration: the whole beat lands in ~2.5s. */
private const val SWEEP_MS = 2400

/**
 * Deterministic problem zones for the cinematic overlay, in 0-1000
 * normalized coords. The analyzer reports one tray box plus a free-text
 * issue list, so each issue gets one horizontal band of the tray box (up to
 * [maxZones]); with no issues the whole tray box is the single zone.
 */
fun problemZones(result: AnalysisResult, maxZones: Int = 3): List<TrayBox> {
    val base = result.trayBox ?: TrayBox(60, 120, 940, 880)
    val n = result.issues.size.coerceIn(1, maxZones)
    if (n == 1) return listOf(base)
    val h = base.y1 - base.y0
    val gap = (h * 0.04f).toInt().coerceAtLeast(8)
    val bandH = (h - gap * (n - 1)) / n
    return List(n) { i ->
        val y0 = base.y0 + i * (bandH + gap)
        // Alternate a slight horizontal inset so stacked bands read as
        // distinct zones instead of one striped column.
        val inset = if (i % 2 == 0) (base.x1 - base.x0) / 18 else 0
        TrayBox(base.x0 + inset, y0, base.x1 - inset, y0 + bandH)
    }
}
