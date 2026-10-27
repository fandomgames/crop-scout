package com.farrah.sproutvision.analyze

/**
 * Analyzes a tray photo. Two implementations exist:
 * - [DemoAnalyzer]: scripted results, no network, no camera — for the XR emulator.
 * - [GeminiAnalyzer]: real vision analysis via the Gemini Developer API — for real glasses.
 */
interface PlantAnalyzer {
    /**
     * [onEvent] receives live progress notes (uploading, waiting, retries)
     * for the analyzing feed, so a stall is visible while it happens.
     */
    suspend fun analyze(
        imageBytes: ByteArray,
        varietyHint: String?,
        onEvent: (String) -> Unit = {}
    ): AnalysisResult
}
