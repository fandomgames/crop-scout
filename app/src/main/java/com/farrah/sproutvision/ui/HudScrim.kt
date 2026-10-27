package com.farrah.sproutvision.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * HUD translucency visual model. The panel background is translucent
 * so the room and plants stay visible around the HUD edges; every
 * text element instead sits on its own solid dark backing, at least
 * as dark as the old full-panel scrim, so readability under grow
 * lights is unchanged. White text throughout, unchanged.
 *
 * Requires the HUD layers to use ALPHA_BLEND (see registerPanels):
 * the default MASKED mode would render translucency as solid/holey.
 */
const val HUD_PANEL_SCRIM_ALPHA = 0.3f

/** Text backing darkness: at least the old 0.7 full-panel scrim. */
const val HUD_TEXT_BACKING_ALPHA = 0.75f

fun hudPanelScrim(): Color = Color.Black.copy(alpha = HUD_PANEL_SCRIM_ALPHA)

fun hudTextBackingColor(): Color = Color.Black.copy(alpha = HUD_TEXT_BACKING_ALPHA)

/**
 * Solid dark backing for one text element sitting on the translucent
 * panel. Applied directly to the Text composable: it fills exactly
 * the text's own bounds (plus a small corner radius), so layout,
 * positions, and sizes do not change. Do NOT use inside components
 * that already paint a solid background (buttons, cards, fields) —
 * their own surface is the backing.
 */
fun Modifier.hudTextBacking(): Modifier =
    background(hudTextBackingColor(), RoundedCornerShape(4.dp))
