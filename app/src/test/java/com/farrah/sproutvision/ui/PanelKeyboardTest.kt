package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the in-panel keyboard's edit contract: keys append at the end,
 * backspace drops the last char, and backspace on empty text is a no-op.
 * (The Quest system keyboard never appears in these panels, so this is
 * the only typing path for tray names and API keys.)
 */
class PanelKeyboardTest {

    @Test
    fun `typing appends characters in order`() {
        var text = ""
        for (c in "Beets") {
            text = panelKeyboardEdit(text, PanelKey.Char(c))
        }
        assertEquals("Beets", text)
    }

    @Test
    fun `typing appends digits and key symbols`() {
        var text = ""
        for (c in "AIza-7_.") {
            text = panelKeyboardEdit(text, PanelKey.Char(c))
        }
        assertEquals("AIza-7_.", text)
    }

    @Test
    fun `backspace drops the last character`() {
        assertEquals("Bee", panelKeyboardEdit("Beet", PanelKey.Backspace))
    }

    @Test
    fun `backspace on empty text is a no-op`() {
        assertEquals("", panelKeyboardEdit("", PanelKey.Backspace))
    }

    @Test
    fun `typo correction by backspace then retype`() {
        var text = "Bext"
        text = panelKeyboardEdit(text, PanelKey.Backspace)
        text = panelKeyboardEdit(text, PanelKey.Backspace)
        text = panelKeyboardEdit(text, PanelKey.Char('e'))
        text = panelKeyboardEdit(text, PanelKey.Char('t'))
        text = panelKeyboardEdit(text, PanelKey.Char('s'))
        assertEquals("Beets", text)
    }
}
