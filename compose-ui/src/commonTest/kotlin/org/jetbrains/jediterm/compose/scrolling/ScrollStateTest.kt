package org.jetbrains.jediterm.compose.scrolling

import kotlin.test.*

/**
 * Tests for TerminalScrollState
 */
class TerminalScrollStateTest {

    @Test
    fun testInitialState() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        assertEquals(0, state.scrollPosition.value)
        assertEquals(100, state.maxScrollPosition.value)
        assertTrue(state.isAtBottom.value)
        assertFalse(state.isScrolling.value)
    }

    @Test
    fun testScrollTo() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.scrollTo(50)
        assertEquals(50, state.scrollPosition.value)
        assertFalse(state.isAtBottom.value)

        // Test clamping to max
        state.scrollTo(150)
        assertEquals(100, state.scrollPosition.value)

        // Test clamping to min
        state.scrollTo(-10)
        assertEquals(0, state.scrollPosition.value)
        assertTrue(state.isAtBottom.value)
    }

    @Test
    fun testScrollBy() {
        val state = TerminalScrollState(
            initialScrollPosition = 50,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.scrollBy(10)
        assertEquals(60, state.scrollPosition.value)

        state.scrollBy(-20)
        assertEquals(40, state.scrollPosition.value)

        // Test clamping
        state.scrollBy(100)
        assertEquals(100, state.scrollPosition.value)

        state.scrollBy(-200)
        assertEquals(0, state.scrollPosition.value)
        assertTrue(state.isAtBottom.value)
    }

    @Test
    fun testScrollToBottom() {
        val state = TerminalScrollState(
            initialScrollPosition = 50,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.scrollToBottom()
        assertEquals(0, state.scrollPosition.value)
        assertTrue(state.isAtBottom.value)
    }

    @Test
    fun testScrollToTop() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.scrollToTop()
        assertEquals(100, state.scrollPosition.value)
        assertFalse(state.isAtBottom.value)
    }

    @Test
    fun testScrollByPages() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.scrollByPages(1f)
        assertEquals(24, state.scrollPosition.value)

        state.scrollByPages(0.5f)
        assertEquals(36, state.scrollPosition.value)

        state.scrollByPages(-1f)
        assertEquals(12, state.scrollPosition.value)
    }

    @Test
    fun testUpdateMaxScrollPosition() {
        val state = TerminalScrollState(
            initialScrollPosition = 50,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        state.updateMaxScrollPosition(200)
        assertEquals(200, state.maxScrollPosition.value)
        assertEquals(50, state.scrollPosition.value)

        // Test clamping when max is reduced
        state.updateMaxScrollPosition(30)
        assertEquals(30, state.maxScrollPosition.value)
        assertEquals(30, state.scrollPosition.value)
    }

    @Test
    fun testGetScrollFraction() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        // At bottom
        assertEquals(1.0f, state.getScrollFraction())

        // At top
        state.scrollTo(100)
        assertEquals(0.0f, state.getScrollFraction())

        // In middle
        state.scrollTo(50)
        assertEquals(0.5f, state.getScrollFraction())

        // With no scroll (maxScrollPosition = 0)
        state.updateMaxScrollPosition(0)
        assertEquals(1.0f, state.getScrollFraction())
    }

    @Test
    fun testViewportInfo() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        // At bottom
        val bottomViewport = state.currentViewport
        assertEquals(100, bottomViewport.firstVisibleLine)
        assertEquals(123, bottomViewport.lastVisibleLine)
        assertEquals(124, bottomViewport.totalLines)
        assertEquals(24, bottomViewport.visibleLineCount)
        assertTrue(bottomViewport.isAtBottom)

        // Scroll up
        state.scrollTo(50)
        val midViewport = state.currentViewport
        assertEquals(50, midViewport.firstVisibleLine)
        assertEquals(73, midViewport.lastVisibleLine)
        assertFalse(midViewport.isAtBottom)

        // At top
        state.scrollToTop()
        val topViewport = state.currentViewport
        assertEquals(0, topViewport.firstVisibleLine)
        assertEquals(23, topViewport.lastVisibleLine)
        assertTrue(topViewport.isAtTop)
    }

    @Test
    fun testGetLineIndexForViewportRow() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        // At bottom
        assertEquals(100, state.getLineIndexForViewportRow(0))
        assertEquals(123, state.getLineIndexForViewportRow(23))

        // Scroll up
        state.scrollTo(50)
        assertEquals(50, state.getLineIndexForViewportRow(0))
        assertEquals(73, state.getLineIndexForViewportRow(23))
    }

    @Test
    fun testLineIndexToBufferOffset() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        // Line in history
        val (isInHistory1, offset1) = state.lineIndexToBufferOffset(50)
        assertTrue(isInHistory1)
        assertEquals(50, offset1)

        // Line in screen buffer
        val (isInHistory2, offset2) = state.lineIndexToBufferOffset(110)
        assertFalse(isInHistory2)
        assertEquals(10, offset2)

        // First line in screen buffer
        val (isInHistory3, offset3) = state.lineIndexToBufferOffset(100)
        assertFalse(isInHistory3)
        assertEquals(0, offset3)
    }

    @Test
    fun testAutoScrollToBottomIfNeeded() {
        val state = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )

        // Should auto-scroll when at bottom
        state.updateMaxScrollPosition(110)
        state.autoScrollToBottomIfNeeded()
        assertEquals(0, state.scrollPosition.value)

        // Should NOT auto-scroll when not at bottom
        state.scrollTo(50)
        state.updateMaxScrollPosition(120)
        state.autoScrollToBottomIfNeeded()
        assertEquals(50, state.scrollPosition.value)
    }
}
