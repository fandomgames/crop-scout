package com.farrah.sproutvision.analyze

import com.farrah.sproutvision.settings.AppSettings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wild-ID identifier order — the same order as tray scan:
 * Demo (if demoMode) > Meta (if useMetaAi + Meta key) >
 * Gemini (if Gemini key) > Demo. A blank Gemini key must NOT force
 * demo when a Meta key is present.
 */
class PlantIdentifierSelectionTest {

    private fun settings(
        demoMode: Boolean = false,
        useMetaAi: Boolean = false,
        metaApiKey: String = "",
        geminiApiKey: String = ""
    ) = AppSettings(
        demoMode = demoMode,
        useMetaAi = useMetaAi,
        metaApiKey = metaApiKey,
        geminiApiKey = geminiApiKey
    )

    @Test
    fun `demo mode always uses the scripted identifier`() {
        assertTrue(
            selectPlantIdentifier(
                settings(demoMode = true, useMetaAi = true, metaApiKey = "m", geminiApiKey = "g")
            ) is DemoPlantIdentifier
        )
    }

    @Test
    fun `meta key wins over blank gemini key`() {
        assertTrue(
            selectPlantIdentifier(
                settings(useMetaAi = true, metaApiKey = "m")
            ) is MetaPlantIdentifier
        )
    }

    @Test
    fun `meta wins when both keys are present`() {
        assertTrue(
            selectPlantIdentifier(
                settings(useMetaAi = true, metaApiKey = "m", geminiApiKey = "g")
            ) is MetaPlantIdentifier
        )
    }

    @Test
    fun `gemini runs when meta is on but its key is blank`() {
        assertTrue(
            selectPlantIdentifier(
                settings(useMetaAi = true, geminiApiKey = "g")
            ) is GeminiPlantIdentifier
        )
    }

    @Test
    fun `gemini runs when the meta toggle is off`() {
        assertTrue(
            selectPlantIdentifier(
                settings(metaApiKey = "m", geminiApiKey = "g")
            ) is GeminiPlantIdentifier
        )
    }

    @Test
    fun `blank keys fall back to demo`() {
        assertTrue(selectPlantIdentifier(settings()) is DemoPlantIdentifier)
    }
}
