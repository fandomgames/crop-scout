package com.farrah.sproutvision.analyze

import com.farrah.sproutvision.data.GrowthStage
import kotlinx.coroutines.delay

/**
 * Scripted analyzer used in demo mode. The emulator's camera is simulated, so a
 * real photo can't be captured there — this returns plausible, variety-aware
 * results so every screen and flow is fully testable without hardware or an API key.
 */
class DemoAnalyzer : PlantAnalyzer {

    override suspend fun analyze(
        imageBytes: ByteArray,
        varietyHint: String?,
        onEvent: (String) -> Unit
    ): AnalysisResult {
        onEvent("Running scripted analysis…")
        delay(1_200) // simulate network/vision latency
        val hint = varietyHint?.lowercase().orEmpty()
        return when {
            "basil" in hint -> AnalysisResult(
                variety = "Basil (Genovese)",
                stage = GrowthStage.LIGHT,
                healthScore = 88,
                issues = listOf("Slight legginess along tray edges"),
                coveragePercent = 92,
                recommendation = "Lower lights to ~6 inches above the canopy and keep the fan on for stockier stems.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
            "broccoli" in hint -> AnalysisResult(
                variety = "Broccoli (Waltham)",
                stage = GrowthStage.GERMINATION,
                healthScore = 94,
                issues = emptyList(),
                coveragePercent = 78,
                recommendation = "Even germination — move to blackout for 2–3 days once cotyledons fully open.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
            "sunflower" in hint -> AnalysisResult(
                variety = "Sunflower",
                stage = GrowthStage.LIGHT,
                healthScore = 81,
                issues = listOf("A few seed hulls still stuck on cotyledons"),
                coveragePercent = 85,
                recommendation = "Mist lightly and gently brush stuck hulls off; harvest at 2–3 inches tall.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
            "kale" in hint -> AnalysisResult(
                variety = "Kale",
                stage = GrowthStage.BLACKOUT,
                healthScore = 90,
                issues = emptyList(),
                coveragePercent = 65,
                recommendation = "Keep the blackout cover on until stems stretch, then move to light.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
            "radish" in hint -> AnalysisResult(
                variety = "Radish",
                stage = GrowthStage.LIGHT,
                healthScore = 95,
                issues = emptyList(),
                coveragePercent = 96,
                recommendation = "Vigorous and even — harvest within the next day or two before stems toughen.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
            else -> AnalysisResult(
                variety = varietyHint?.ifBlank { null } ?: "Microgreens",
                stage = GrowthStage.LIGHT,
                healthScore = 90,
                issues = emptyList(),
                coveragePercent = 88,
                recommendation = "Tray looks healthy — keep watering from the bottom and harvest on schedule.",
                isDemo = true,
                trayVisible = true,
                trayBox = null,
                confidence = 92
            )
        }
    }
}
