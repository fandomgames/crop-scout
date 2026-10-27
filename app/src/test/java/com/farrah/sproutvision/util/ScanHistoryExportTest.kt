package com.farrah.sproutvision.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the scan-history CSV: exact header/column order for the DaVinci
 * composite, and quoting of fields containing commas or quotes.
 */
class ScanHistoryExportTest {

    @Test
    fun `header and row columns match the documented order`() {
        val csv = ScanHistoryExport.buildCsv(
            listOf(
                ScanCsvRow(
                    date = "2026-10-16 14:05",
                    tray = "Tray A",
                    variety = "Radish",
                    day = 5,
                    health = 82,
                    topIssue = "leggy stems"
                )
            )
        )

        assertEquals(
            "date,tray,variety,day,health,top_issue\n" +
                "2026-10-16 14:05,Tray A,Radish,5,82,leggy stems\n",
            csv
        )
    }

    @Test
    fun `fields with commas or quotes are quoted and escaped`() {
        val csv = ScanHistoryExport.buildCsv(
            listOf(
                ScanCsvRow(
                    date = "2026-10-16 14:05",
                    tray = "Tray, B",
                    variety = "Radish",
                    day = 5,
                    health = 60,
                    topIssue = "mold, \"fuzzy\" spots"
                )
            )
        )

        assertEquals(
            "date,tray,variety,day,health,top_issue\n" +
                "2026-10-16 14:05,\"Tray, B\",Radish,5,60,\"mold, \"\"fuzzy\"\" spots\"\n",
            csv
        )
    }

    @Test
    fun `empty history still emits the header`() {
        assertEquals(
            "date,tray,variety,day,health,top_issue\n",
            ScanHistoryExport.buildCsv(emptyList())
        )
    }
}
