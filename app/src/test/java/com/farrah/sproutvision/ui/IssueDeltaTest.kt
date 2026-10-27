package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the before/after delta helpers: splitting the stored "; "-joined
 * issues encoding, and the case-insensitive resolved/ongoing/new comparison
 * (which keeps the original display text).
 */
class IssueDeltaTest {

    @Test
    fun `splitIssues handles the stored encoding and blanks`() {
        assertEquals(
            listOf("mold", "leggy stems", "dry spots"),
            splitIssues("mold; leggy stems ;dry spots")
        )
        assertEquals(emptyList<String>(), splitIssues(""))
        assertEquals(emptyList<String>(), splitIssues("  "))
    }

    @Test
    fun `diffIssues compares case-insensitively and keeps display text`() {
        val delta = diffIssues(
            before = listOf("Mold", "Leggy stems"),
            now = listOf("leggy STEMS", "Dry spots")
        )

        assertEquals(listOf("Mold"), delta.resolved)
        assertEquals(listOf("leggy STEMS"), delta.ongoing)
        assertEquals(listOf("Dry spots"), delta.new)
    }

    @Test
    fun `diffIssues with no changes reports everything ongoing`() {
        val delta = diffIssues(
            before = listOf("Mold"),
            now = listOf("mold")
        )

        assertEquals(emptyList<String>(), delta.resolved)
        assertEquals(listOf("mold"), delta.ongoing)
        assertEquals(emptyList<String>(), delta.new)
    }
}
