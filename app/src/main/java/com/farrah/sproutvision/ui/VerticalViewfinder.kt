package com.farrah.sproutvision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Whether the 9:16 viewfinder guide shows: vertical mode ON, panels up,
 * Manage closed, guide not hidden — and never on the scan screen, so the
 * guide can't distract from the capture moment. Results keep the guide:
 * it frames the verdict for the recording.
 */
fun verticalViewfinderVisible(
    verticalVideo: Boolean,
    cleanMode: Boolean,
    manageVisible: Boolean,
    hudScreen: HudScreen,
    hideViewfinder: Boolean = false
): Boolean =
    verticalVideo && !cleanMode && !manageVisible &&
        hudScreen !is HudScreen.Scan && !hideViewfinder

/**
 * The 9:16 shorts viewfinder: a transparent guide-only overlay. Thin
 * semi-transparent full-frame border, brighter corner brackets, and a
 * small scrimmed "9:16" label up top.
 *
 * Guide only: nothing here is clickable, grabbable, or resizable, and the
 * middle is fully transparent — passthrough shows through untouched, with
 * no dimming inside or outside the frame. The panel sits 2 cm behind the
 * compact HUD, so HUD controls always win ray input where they overlap.
 */
@Composable
fun VerticalViewfinderOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val border = Color.White.copy(alpha = 0.45f)
            val accent = Color.White.copy(alpha = 0.9f)
            val inset = 2.dp.toPx()
            drawRect(
                color = border,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                style = Stroke(width = 2.dp.toPx())
            )
            // Corner brackets: short L strokes over the border corners.
            val arm = 72.dp.toPx()
            val width = 5.dp.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            // Top-left.
            drawLine(accent, Offset(left, top + arm), Offset(left, top), width)
            drawLine(accent, Offset(left, top), Offset(left + arm, top), width)
            // Top-right.
            drawLine(accent, Offset(right - arm, top), Offset(right, top), width)
            drawLine(accent, Offset(right, top), Offset(right, top + arm), width)
            // Bottom-left.
            drawLine(accent, Offset(left, bottom - arm), Offset(left, bottom), width)
            drawLine(accent, Offset(left, bottom), Offset(left + arm, bottom), width)
            // Bottom-right.
            drawLine(accent, Offset(right, bottom - arm), Offset(right, bottom), width)
            drawLine(accent, Offset(right, bottom), Offset(right - arm, bottom), width)
        }
        // Rule: no text over passthrough without a scrim — fixed white on
        // rounded black, never theme colors (both themes vanish on black).
        Box(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                "9:16",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
