package org.jetbrains.jediterm.compose.rendering

import androidx.compose.ui.graphics.Color
import com.jediterm.terminal.TerminalColor
import org.jetbrains.jediterm.compose.TerminalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorMapperTest {
    private val theme = TerminalState.TerminalTheme.dark()
    private val colorMapper = ColorMapper(theme)

    @Test
    fun testNullColorReturnsDefault() {
        val foreground = colorMapper.toComposeColor(null, isBackground = false)
        val background = colorMapper.toComposeColor(null, isBackground = true)

        assertEquals(theme.defaultForeground, foreground)
        assertEquals(theme.defaultBackground, background)
    }

    @Test
    fun testIndexedColorMapping() {
        // Test standard ANSI colors (0-15)
        val black = colorMapper.toComposeColor(TerminalColor.index(0))
        val red = colorMapper.toComposeColor(TerminalColor.index(1))
        val white = colorMapper.toComposeColor(TerminalColor.index(15))

        // Verify colors are mapped correctly
        assertTrue(black.red < 0.1f && black.green < 0.1f && black.blue < 0.1f)
        assertTrue(red.red > 0.5f)
        assertTrue(white.red > 0.8f && white.green > 0.8f && white.blue > 0.8f)
    }

    @Test
    fun testRgbColorMapping() {
        val customColor = TerminalColor.rgb(100, 150, 200)
        val composeColor = colorMapper.toComposeColor(customColor)

        // Note: Compose Color uses normalized values (0-1)
        assertEquals(100, (composeColor.red * 255).toInt())
        assertEquals(150, (composeColor.green * 255).toInt())
        assertEquals(200, (composeColor.blue * 255).toInt())
    }

    @Test
    fun testColorCube() {
        // Test 216 color cube (indices 16-231)
        val color16 = colorMapper.toComposeColor(TerminalColor.index(16))
        val color231 = colorMapper.toComposeColor(TerminalColor.index(231))

        // Verify they're valid colors
        assertTrue(color16.alpha > 0f)
        assertTrue(color231.alpha > 0f)
    }

    @Test
    fun testGrayscaleColors() {
        // Test 24 grayscale colors (indices 232-255)
        val darkGray = colorMapper.toComposeColor(TerminalColor.index(232))
        val lightGray = colorMapper.toComposeColor(TerminalColor.index(255))

        // Grayscale should have equal RGB components
        assertEquals(darkGray.red, darkGray.green, 0.01f)
        assertEquals(darkGray.green, darkGray.blue, 0.01f)

        // Light gray should be brighter than dark gray
        assertTrue(lightGray.red > darkGray.red)
    }

    @Test
    fun testInverseColors() {
        val fg = Color.White
        val bg = Color.Black

        val (newFg, newBg) = colorMapper.applyInverse(fg, bg)

        assertEquals(bg, newFg)
        assertEquals(fg, newBg)
    }

    @Test
    fun testDimEffect() {
        val brightColor = Color.White
        val dimmedColor = colorMapper.applyDim(brightColor)

        assertTrue(dimmedColor.red < brightColor.red)
        assertTrue(dimmedColor.green < brightColor.green)
        assertTrue(dimmedColor.blue < brightColor.blue)
    }

    @Test
    fun test256ColorPaletteGeneration() {
        val palette = ColorMapper.generate256ColorPalette()

        assertEquals(256, palette.size)

        // Verify first 16 are ANSI colors
        assertEquals(16, palette.take(16).size)

        // Verify color cube (216 colors)
        assertEquals(216, palette.subList(16, 232).size)

        // Verify grayscale (24 colors)
        assertEquals(24, palette.subList(232, 256).size)
    }
}
