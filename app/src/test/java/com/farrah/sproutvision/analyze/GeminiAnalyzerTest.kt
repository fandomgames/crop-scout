package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Guards the Gemini model fallback chain (pure and unit-testable).
 * Transport needs the network; the chain order, URL shape, and
 * transient classification below are asserted so a wrong model or
 * failover rule cannot slip in silently.
 */
class GeminiAnalyzerTest {

    @Test
    fun `model chain tries proven 3-5 first with 3-8 as fallback`() {
        assertEquals(
            listOf("gemini-3.5-flash", "gemini-3.8-flash"),
            GEMINI_MODEL_CHAIN
        )
    }

    @Test
    fun `first attempt is impatient, failover is patient`() {
        assertEquals(25, FIRST_ATTEMPT_READ_TIMEOUT_S)
        assertEquals(60, FAILOVER_READ_TIMEOUT_S)
        assertTrue(FIRST_ATTEMPT_READ_TIMEOUT_S < FAILOVER_READ_TIMEOUT_S)
    }

    @Test
    fun `model urls use the v1beta generateContent endpoint`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            geminiModelUrl("gemini-3.8-flash")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
            geminiModelUrl("gemini-3.5-flash")
        )
    }

    @Test
    fun `only rate-limit and overload codes fail over`() {
        assertTrue(isTransientGeminiCode(429))
        assertTrue(isTransientGeminiCode(503))
        assertFalse(isTransientGeminiCode(200))
        assertFalse(isTransientGeminiCode(400))
        assertFalse(isTransientGeminiCode(401))
        assertFalse(isTransientGeminiCode(404))
    }

    @Test
    fun `transient errors stay IOExceptions for existing callers`() {
        val e = TransientGeminiException(503, "Gemini API error 503: overloaded")
        // Must compile: existing catch(IOException) sites are unaffected.
        val asIo: IOException = e
        assertEquals(503, e.code)
        assertEquals("Gemini API error 503: overloaded", asIo.message)
    }
}
