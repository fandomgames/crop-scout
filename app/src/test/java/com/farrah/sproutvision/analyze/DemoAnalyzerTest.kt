package com.farrah.sproutvision.analyze

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the scripted analyzer: still returns its variety-aware result,
 * and emits live progress for the analyzing feed.
 */
class DemoAnalyzerTest {

    @Test
    fun `demo run emits progress and returns its result`() {
        val events = mutableListOf<String>()
        val result = runBlocking {
            DemoAnalyzer().analyze(ByteArray(10), "Basil") { events += it }
        }

        assertTrue(events.isNotEmpty())
        assertEquals(true, result.isDemo)
        assertEquals("Basil (Genovese)", result.variety)
    }
}
