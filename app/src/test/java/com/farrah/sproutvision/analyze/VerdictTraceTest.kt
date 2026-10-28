package com.farrah.sproutvision.analyze

import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.ScanEntity
import com.farrah.sproutvision.data.ScanRecordEntity
import com.farrah.sproutvision.util.formatDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pinned verdict trace: hash determinism, model labels for
 * every analyzer path, and null-safe display (pre-trace rows show —,
 * never invented values).
 */
class VerdictTraceTest {

    @Test
    fun `sha256 matches the known-answer vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `sha256 is deterministic and sensitive to input`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val first = sha256Hex(bytes)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(first, sha256Hex(bytes.copyOf()))
        assertTrue(first != sha256Hex(byteArrayOf(1, 2, 3, 4, 6)))
        assertTrue(first != sha256Hex(ByteArray(0)))
    }

    @Test
    fun `models label covers the SAM path`() {
        assertEquals(
            "sam-3.1 + muse-spark",
            traceModelsLabel(
                demo = false, useMeta = true, samMeasured = true, samErrorDetail = null
            )
        )
    }

    @Test
    fun `models label covers Spark-only fallback with the SAM reason`() {
        assertEquals(
            "muse-spark only (SAM 3.1 returned no output text)",
            traceModelsLabel(
                demo = false, useMeta = true, samMeasured = false,
                samErrorDetail = "SAM 3.1 returned no output text"
            )
        )
        assertEquals(
            "muse-spark only (turned off in Settings)",
            traceModelsLabel(
                demo = false, useMeta = true, samMeasured = false,
                samErrorDetail = "turned off in Settings"
            )
        )
    }

    @Test
    fun `models label covers Spark-only fallback without a reason`() {
        assertEquals(
            "muse-spark only",
            traceModelsLabel(
                demo = false, useMeta = true, samMeasured = false, samErrorDetail = null
            )
        )
        assertEquals(
            "muse-spark only",
            traceModelsLabel(
                demo = false, useMeta = true, samMeasured = false, samErrorDetail = "  "
            )
        )
    }

    @Test
    fun `models label covers the Gemini and demo paths`() {
        assertEquals(
            "gemini",
            traceModelsLabel(
                demo = false, useMeta = false, samMeasured = false, samErrorDetail = null
            )
        )
        assertEquals(
            "demo",
            traceModelsLabel(
                demo = true, useMeta = false, samMeasured = false, samErrorDetail = null
            )
        )
        // Demo mode wins even when the Meta toggle is on (scripted analyzer ran).
        assertEquals(
            "demo",
            traceModelsLabel(
                demo = true, useMeta = true, samMeasured = false, samErrorDetail = null
            )
        )
    }

    @Test
    fun `all-null trace renders dashes everywhere`() {
        val display = formatVerdictTrace(VerdictTrace(null, null, null, null))
        assertEquals(VerdictTraceDisplay("—", "—", "—", "—"), display)
    }

    @Test
    fun `present trace fields format with units, null coverage never reads 0`() {
        val stamp = 1_791_000_000_000L
        val display = formatVerdictTrace(
            VerdictTrace(
                models = "sam-3.1 + muse-spark",
                samCoverage = 42,
                timestampMs = stamp,
                imageHash = "ab".repeat(32)
            )
        )
        assertEquals("sam-3.1 + muse-spark", display.models)
        assertEquals("42%", display.coverage)
        assertEquals(formatDateTime(stamp), display.timestamp)
        assertEquals("ab".repeat(32), display.imageHash)
        assertEquals(
            "—",
            formatVerdictTrace(VerdictTrace("gemini", null, stamp, "cd")).coverage
        )
    }

    @Test
    fun `pre-trace result maps to null fields without crashing`() {
        val trace = AnalysisResult(
            variety = "Basil",
            stage = GrowthStage.LIGHT,
            healthScore = 80,
            issues = emptyList(),
            coveragePercent = 70,
            recommendation = "",
            isDemo = true,
            trayVisible = true,
            trayBox = null
        ).verdictTrace()
        assertNull(trace.models)
        assertNull(trace.samCoverage)
        assertNull(trace.timestampMs)
        assertNull(trace.imageHash)
    }

    @Test
    fun `pre-trace tray scan keeps its timestamp and nulls the new fields`() {
        val trace = ScanEntity(
            trayId = 1, timestampEpochMs = 67890L, variety = "Basil",
            stage = GrowthStage.LIGHT, healthScore = 80, coveragePercent = 70
        ).verdictTrace()
        assertNull(trace.models)
        assertNull(trace.samCoverage)
        assertEquals(67890L, trace.timestampMs)
        assertNull(trace.imageHash)
    }

    @Test
    fun `pre-trace history row keeps its timestamp and nulls the new fields`() {
        val trace = ScanRecordEntity(
            trayId = 1, timestamp = 12345L, healthPct = 80, coveragePct = 70
        ).verdictTrace()
        assertNull(trace.models)
        assertNull(trace.samCoverage)
        assertEquals(12345L, trace.timestampMs)
        assertNull(trace.imageHash)
    }
}
