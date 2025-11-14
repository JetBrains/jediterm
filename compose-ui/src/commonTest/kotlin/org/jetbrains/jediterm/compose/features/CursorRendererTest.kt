package org.jetbrains.jediterm.compose.features

import androidx.compose.ui.graphics.Color
import org.jetbrains.jediterm.compose.CursorRenderer
import org.jetbrains.jediterm.compose.TerminalRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorRendererTest {

    @Test
    fun testCursorRendererCreation() {
        val renderer = ComposeCursorRenderer()
        assertTrue(renderer.isBlinkVisible.value, "Cursor should be initially visible")
    }

    @Test
    fun testBlinkToggle() {
        val renderer = ComposeCursorRenderer()

        val initialState = renderer.isBlinkVisible.value
        renderer.toggleBlink()
        val afterToggle = renderer.isBlinkVisible.value

        assertEquals(!initialState, afterToggle, "Blink state should toggle")

        renderer.toggleBlink()
        val afterSecondToggle = renderer.isBlinkVisible.value

        assertEquals(initialState, afterSecondToggle, "Blink state should toggle back")
    }

    @Test
    fun testBlinkReset() {
        val renderer = ComposeCursorRenderer()

        renderer.toggleBlink() // Make it invisible
        assertFalse(renderer.isBlinkVisible.value, "Should be invisible after toggle")

        renderer.resetBlink()
        assertTrue(renderer.isBlinkVisible.value, "Should be visible after reset")
    }

    @Test
    fun testCreateCursorState() {
        val state = createCursorState(
            col = 10,
            row = 5,
            style = CursorRenderer.CursorStyle.BLOCK,
            color = Color.White,
            isVisible = true,
            isBlinking = true
        )

        assertEquals(10, state.col)
        assertEquals(5, state.row)
        assertEquals(CursorRenderer.CursorStyle.BLOCK, state.style)
        assertEquals(Color.White, state.color)
        assertTrue(state.isVisible)
        assertTrue(state.isBlinking)
    }

    @Test
    fun testCursorStateWithDifferentStyles() {
        val blockState = createCursorState(style = CursorRenderer.CursorStyle.BLOCK)
        val underlineState = createCursorState(style = CursorRenderer.CursorStyle.UNDERLINE)
        val barState = createCursorState(style = CursorRenderer.CursorStyle.VERTICAL_BAR)

        assertEquals(CursorRenderer.CursorStyle.BLOCK, blockState.style)
        assertEquals(CursorRenderer.CursorStyle.UNDERLINE, underlineState.style)
        assertEquals(CursorRenderer.CursorStyle.VERTICAL_BAR, barState.style)
    }

    @Test
    fun testCursorRendererWithInvisibleCursor() {
        val renderer = ComposeCursorRenderer()
        val cellDimensions = TerminalRenderer.CellDimensions(10f, 20f)

        val invisibleState = createCursorState(isVisible = false)

        // Should not throw and should handle invisible state
        renderer.renderCursor(invisibleState, cellDimensions)
    }

    @Test
    fun testCursorRendererWithBlinkingDisabled() {
        val renderer = ComposeCursorRenderer()
        val cellDimensions = TerminalRenderer.CellDimensions(10f, 20f)

        val nonBlinkingState = createCursorState(isBlinking = false)

        // Should render even when blinking is toggled off
        renderer.toggleBlink() // Make it invisible
        renderer.renderCursor(nonBlinkingState, cellDimensions)
    }
}
