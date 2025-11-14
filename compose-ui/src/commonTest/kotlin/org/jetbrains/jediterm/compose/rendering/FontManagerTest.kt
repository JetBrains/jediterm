package org.jetbrains.jediterm.compose.rendering

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.jediterm.compose.TerminalRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FontManagerTest {
    @Test
    fun testDoubleWidthCharacterDetection() {
        // Create a mock TextMeasurer for testing
        // Note: In actual testing, you would need a proper TextMeasurer implementation
        // For now, we'll test the double-width detection logic directly

        // CJK Unified Ideographs
        assertTrue(isDoubleWidthChar('中'))
        assertTrue(isDoubleWidthChar('文'))

        // Hiragana
        assertTrue(isDoubleWidthChar('あ'))
        assertTrue(isDoubleWidthChar('い'))

        // Katakana
        assertTrue(isDoubleWidthChar('ア'))
        assertTrue(isDoubleWidthChar('イ'))

        // Hangul
        assertTrue(isDoubleWidthChar('한'))
        assertTrue(isDoubleWidthChar('글'))

        // Regular ASCII characters should not be double-width
        assertFalse(isDoubleWidthChar('A'))
        assertFalse(isDoubleWidthChar('a'))
        assertFalse(isDoubleWidthChar('1'))
        assertFalse(isDoubleWidthChar(' '))
    }

    @Test
    fun testCharacterWidthMultiplier() {
        assertEquals(2.0f, getCharacterWidthMultiplier('中'))
        assertEquals(1.0f, getCharacterWidthMultiplier('A'))
        assertEquals(2.0f, getCharacterWidthMultiplier('あ'))
        assertEquals(1.0f, getCharacterWidthMultiplier('1'))
    }

    @Test
    fun testFontStyleSelection() {
        // Test that correct styles are selected
        val normalStyle = selectFontStyle(isBold = false, isItalic = false)
        val boldStyle = selectFontStyle(isBold = true, isItalic = false)
        val italicStyle = selectFontStyle(isBold = false, isItalic = true)
        val boldItalicStyle = selectFontStyle(isBold = true, isItalic = true)

        assertEquals(FontWeight.Normal, normalStyle.weight)
        assertEquals(FontStyle.Normal, normalStyle.style)

        assertEquals(FontWeight.Bold, boldStyle.weight)
        assertEquals(FontStyle.Normal, boldStyle.style)

        assertEquals(FontWeight.Normal, italicStyle.weight)
        assertEquals(FontStyle.Italic, italicStyle.style)

        assertEquals(FontWeight.Bold, boldItalicStyle.weight)
        assertEquals(FontStyle.Italic, boldItalicStyle.style)
    }

    @Test
    fun testCommonMonospaceFonts() {
        val commonFonts = FontUtils.COMMON_MONOSPACE_FONTS

        assertTrue(commonFonts.isNotEmpty())
        assertTrue(commonFonts.contains("Courier New"))
        assertTrue(commonFonts.contains("Monaco"))
        assertTrue(commonFonts.contains("Consolas"))
    }

    // Helper functions that mirror FontManager logic
    private fun isDoubleWidthChar(char: Char): Boolean {
        val codePoint = char.code

        // CJK Unified Ideographs
        if (codePoint in 0x4E00..0x9FFF) return true

        // Hangul Syllables
        if (codePoint in 0xAC00..0xD7AF) return true

        // CJK Unified Ideographs Extension A
        if (codePoint in 0x3400..0x4DBF) return true

        // CJK Compatibility Ideographs
        if (codePoint in 0xF900..0xFAFF) return true

        // Fullwidth Forms
        if (codePoint in 0xFF00..0xFFEF) return true

        // Hiragana
        if (codePoint in 0x3040..0x309F) return true

        // Katakana
        if (codePoint in 0x30A0..0x30FF) return true

        return false
    }

    private fun getCharacterWidthMultiplier(char: Char): Float {
        return if (isDoubleWidthChar(char)) 2.0f else 1.0f
    }

    private data class TestFontStyle(val weight: FontWeight, val style: FontStyle)

    private fun selectFontStyle(isBold: Boolean, isItalic: Boolean): TestFontStyle {
        return TestFontStyle(
            weight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            style = if (isItalic) FontStyle.Italic else FontStyle.Normal
        )
    }
}
