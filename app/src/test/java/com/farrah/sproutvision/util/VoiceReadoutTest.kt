package com.farrah.sproutvision.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the spoken verdict: variety and day count first, then the fixed
 * frame plus top issue, always under 20 words by construction (long
 * issues are truncated to 8 words).
 */
class VoiceReadoutTest {

    private fun wordCount(s: String) = s.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    @Test
    fun `healthy tray gets the no-issues frame`() {
        assertEquals(
            "Kale, day 4 — Tray health 82. No issues spotted.",
            verdictUtterance("Kale", 4, 82, null)
        )
    }

    @Test
    fun `top issue is read out inline`() {
        assertEquals(
            "Kale, day 4 — Tray health 82. Top issue: leggy stems.",
            verdictUtterance("Kale", 4, 82, "leggy stems")
        )
    }

    @Test
    fun `long issues truncate so the utterance stays under 20 words`() {
        val spoken = verdictUtterance(
            "Kale",
            4,
            60,
            "one two three four five six seven eight nine ten eleven twelve"
        )

        assertEquals(
            "Kale, day 4 — Tray health 60. Top issue: one two three four five six seven eight.",
            spoken
        )
        assertTrue(wordCount(spoken) < 20)
    }
}
