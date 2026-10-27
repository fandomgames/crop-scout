package com.farrah.sproutvision.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the saved-custom-variety encoding and merge rules:
 * round-trip, blank tolerance, the cap, and what auto-save
 * ignores (blanks, built-in defaults, duplicates).
 */
class CustomVarietiesTest {

    private val defaults = setOf("Radish", "Basil")

    @Test
    fun `round trip preserves values including commas`() {
        val values = listOf("Beets", "Thai Basil, Holy", "Mizuna ")
        assertEquals(listOf("Beets", "Thai Basil, Holy", "Mizuna"), parseCustomVarieties(joinCustomVarieties(values)))
    }

    @Test
    fun `blank input parses empty`() {
        assertEquals(emptyList<String>(), parseCustomVarieties(null))
        assertEquals(emptyList<String>(), parseCustomVarieties(""))
        assertEquals(emptyList<String>(), parseCustomVarieties("   "))
    }

    @Test
    fun `blank lines are dropped`() {
        assertEquals(
            listOf("Beets", "Kale"),
            parseCustomVarieties("Beets\n\n   \nKale\n")
        )
    }

    @Test
    fun `oldest entries beyond the cap are trimmed`() {
        val values = (1..25).map { "V$it" }
        val parsed = parseCustomVarieties(joinCustomVarieties(values))
        assertEquals(20, parsed.size)
        assertEquals((6..25).map { "V$it" }, parsed)
        assertEquals(20, MAX_CUSTOM_VARIETIES)
    }

    @Test
    fun `merge adds a trimmed new name`() {
        assertEquals(
            listOf("Beets", "Mizuna"),
            mergeCustomVariety(listOf("Beets"), "  Mizuna ", defaults)
        )
    }

    @Test
    fun `merge ignores blanks defaults and dupes`() {
        assertEquals(listOf("Beets"), mergeCustomVariety(listOf("Beets"), "   ", defaults))
        assertEquals(listOf("Beets"), mergeCustomVariety(listOf("Beets"), "basil", defaults))
        assertEquals(listOf("Beets"), mergeCustomVariety(listOf("Beets"), "BEETS", defaults))
    }

    @Test
    fun `merge keeps first-seen spelling and trims oldest past cap`() {
        val full = (1..20).map { "V$it" }
        assertEquals(full, mergeCustomVariety(full, "v1", defaults))
        val grown = mergeCustomVariety(full, "New", defaults)
        assertEquals(20, grown.size)
        assertEquals("New", grown.last())
        assertEquals((2..20).map { "V$it" } + "New", grown)
    }
}
