package com.farrah.sproutvision.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/**
 * Design sizes for the HUD panels, in dp. MUST match the
 * DpDisplayOptions in QuestSproutActivity.registerPanels: the live
 * scale factor is the current panel size relative to these.
 */
internal const val HUD_DESIGN_WIDTH_DP = 780f
internal const val HUD_DESIGN_HEIGHT_DP = 500f
internal const val HUD_COMPACT_DESIGN_WIDTH_DP = 450f
internal const val HUD_COMPACT_DESIGN_HEIGHT_DP = 320f

/**
 * Uniform HUD scale: live panel size relative to design size. The
 * smaller axis wins so content never overflows — identical layout
 * proportions at any panel size. Degenerate sizes yield 1 (no-op).
 */
internal fun hudScaleFactor(
    viewWidthDp: Float,
    viewHeightDp: Float,
    designWidthDp: Float = HUD_DESIGN_WIDTH_DP,
    designHeightDp: Float = HUD_DESIGN_HEIGHT_DP
): Float {
    if (viewWidthDp <= 0f || viewHeightDp <= 0f) return 1f
    if (designWidthDp <= 0f || designHeightDp <= 0f) return 1f
    return min(viewWidthDp / designWidthDp, viewHeightDp / designHeightDp)
}

/**
 * Scales every dp/sp in [content] with the live panel size. Crisp by
 * construction: sizes re-resolve through a scaled Density (vector
 * layout and text), never a bitmap upscale of the rendered panel.
 * Wrap each HUD panel root with its own design size.
 */
@Composable
fun ScaledHudPanel(
    designWidth: Dp,
    designHeight: Dp,
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        val s = hudScaleFactor(
            maxWidth.value, maxHeight.value, designWidth.value, designHeight.value
        )
        val base = LocalDensity.current
        // Scale density only: dp resolves through density, sp through
        // density × fontScale — both shrink by s, user fontScale kept.
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * s, base.fontScale)
        ) {
            content()
        }
    }
}
