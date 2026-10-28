package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the golden-validation.json reader: writer-format conformance
 * (field names exactly as :validation writes them), null shots, and
 * malformed input that must never crash the view.
 */
class ValidationReportTest {

    @Test
    fun `writer format parses to ordered rows with differences`() {
        val json = "{\"shots\":[" +
            "{\"id\":\"shot2-uneven\",\"samCoverage\":45,\"byEye\":40," +
            "\"difference\":5,\"plantInstances\":33,\"trayInstances\":1,\"note\":null}," +
            "{\"id\":\"shot1-full\",\"samCoverage\":72,\"byEye\":70," +
            "\"difference\":2,\"plantInstances\":31,\"trayInstances\":2,\"note\":null}" +
            "]}"
        val rows = parseValidationReport(json)!!
        assertEquals(5, rows.size)
        // Golden-ID order, not file order; difference recomputed.
        assertEquals(ValidationRow("shot1-full", 72, 70, 2), rows[0])
        assertEquals(ValidationRow("shot2-uneven", 45, 40, 5), rows[1])
        // Absent shots fill as null rows, never 0%.
        assertEquals(ValidationRow("shot3-gap", null, null, null), rows[2])
        assertEquals(ValidationRow("shot4-poorlight", null, null, null), rows[3])
        assertEquals(ValidationRow("shot5-partial", null, null, null), rows[4])
    }

    @Test
    fun `null coverage parses with a null difference`() {
        val json = "{\"shots\":[" +
            "{\"id\":\"shot3-gap\",\"samCoverage\":null,\"byEye\":5," +
            "\"difference\":null,\"plantInstances\":null,\"trayInstances\":null," +
            "\"note\":\"framing caveat\"}" +
            "]}"
        val rows = parseValidationReport(json)!!
        assertEquals(ValidationRow("shot3-gap", null, 5, null), rows[2])
    }

    @Test
    fun `malformed documents return null instead of throwing`() {
        assertNull(parseValidationReport("not json"))
        assertNull(parseValidationReport(""))
    }

    @Test
    fun `non-array shots degrade to null rows`() {
        val rows = parseValidationReport("{\"shots\":42}")!!
        assertEquals(5, rows.size)
        assertEquals(ValidationRow("shot1-full", null, null, null), rows[0])
    }
}
