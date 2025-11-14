package org.jetbrains.jediterm.compose.scrolling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for TerminalScrollManager
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TerminalScrollManagerTest {

    private lateinit var scrollState: TerminalScrollState
    private lateinit var scope: CoroutineScope
    private lateinit var manager: TerminalScrollManager

    @BeforeTest
    fun setup() {
        scrollState = TerminalScrollState(
            initialScrollPosition = 0,
            initialMaxScrollPosition = 100,
            viewportRows = 24
        )
        scope = CoroutineScope(Dispatchers.Default)
        manager = TerminalScrollManager(scrollState, scope)
    }

    @AfterTest
    fun teardown() {
        manager.dispose()
        scope.cancel()
    }

    @Test
    fun testHandleWheelScroll() {
        manager.config = manager.config.copy(
            linesPerWheelNotch = 3,
            smoothScrollEnabled = false
        )

        // Scroll up (positive deltaY)
        manager.handleWheelScroll(deltaY = 1f, isPixelDelta = false)
        assertEquals(3, scrollState.scrollPosition.value)

        // Scroll down (negative deltaY)
        manager.handleWheelScroll(deltaY = -1f, isPixelDelta = false)
        assertEquals(0, scrollState.scrollPosition.value)
    }

    @Test
    fun testHandleWheelScrollWithPixels() {
        manager.config = manager.config.copy(smoothScrollEnabled = false)

        // Scroll with pixel delta (20 pixels per line)
        manager.handleWheelScroll(deltaY = 60f, isPixelDelta = true)
        assertEquals(3, scrollState.scrollPosition.value)

        manager.handleWheelScroll(deltaY = -40f, isPixelDelta = true)
        assertEquals(1, scrollState.scrollPosition.value)
    }

    @Test
    fun testHandleTouchScroll() {
        manager.config = manager.config.copy(smoothScrollEnabled = false)

        // Touch scroll with pixel delta
        manager.handleTouchScroll(deltaY = 60f)
        assertEquals(3, scrollState.scrollPosition.value)

        manager.handleTouchScroll(deltaY = -40f)
        assertEquals(1, scrollState.scrollPosition.value)
    }

    @Test
    fun testPageUpDown() {
        manager.config = manager.config.copy(smoothScrollEnabled = false)

        // Page up
        manager.pageUp()
        assertEquals(24, scrollState.scrollPosition.value)

        // Page down
        manager.pageDown()
        assertEquals(0, scrollState.scrollPosition.value)
    }

    @Test
    fun testKeyboardScroll() {
        manager.config = manager.config.copy(
            linesPerWheelNotch = 3,
            smoothScrollEnabled = false
        )

        // Arrow up
        manager.handleKeyboardScroll(ScrollKey.UP)
        assertEquals(3, scrollState.scrollPosition.value)

        // Arrow down
        manager.handleKeyboardScroll(ScrollKey.DOWN)
        assertEquals(0, scrollState.scrollPosition.value)

        // Home
        manager.handleKeyboardScroll(ScrollKey.HOME)
        assertEquals(100, scrollState.scrollPosition.value)

        // End
        manager.handleKeyboardScroll(ScrollKey.END)
        assertEquals(0, scrollState.scrollPosition.value)

        // Page up
        manager.handleKeyboardScroll(ScrollKey.PAGE_UP)
        assertEquals(24, scrollState.scrollPosition.value)

        // Page down
        manager.handleKeyboardScroll(ScrollKey.PAGE_DOWN)
        assertEquals(0, scrollState.scrollPosition.value)
    }

    @Test
    fun testKeyboardScrollWithShift() {
        manager.config = manager.config.copy(
            linesPerWheelNotch = 3,
            smoothScrollEnabled = false
        )

        // Arrow up with shift (scroll 1 line)
        manager.handleKeyboardScroll(ScrollKey.UP, KeyModifiers(shift = true))
        assertEquals(1, scrollState.scrollPosition.value)

        // Arrow down with shift
        manager.handleKeyboardScroll(ScrollKey.DOWN, KeyModifiers(shift = true))
        assertEquals(0, scrollState.scrollPosition.value)
    }

    @Test
    fun testUpdateScrollLimits() {
        manager.updateScrollLimits(200)
        assertEquals(200, scrollState.maxScrollPosition.value)

        // Scroll to top
        scrollState.scrollTo(200)
        assertEquals(200, scrollState.scrollPosition.value)

        // Reduce limits - should clamp position
        manager.updateScrollLimits(50)
        assertEquals(50, scrollState.maxScrollPosition.value)
        assertEquals(50, scrollState.scrollPosition.value)
    }

    @Test
    fun testForceScrollToBottom() {
        scrollState.scrollTo(50)
        assertFalse(scrollState.isAtBottom.value)

        manager.forceScrollToBottom()
        assertEquals(0, scrollState.scrollPosition.value)
        assertTrue(scrollState.isAtBottom.value)
    }

    @Test
    fun testAutoScrollToBottomIfNeeded() {
        manager.config = manager.config.copy(autoScrollOnInput = true)

        // Should auto-scroll when at bottom
        scrollState.scrollToBottom()
        manager.autoScrollToBottomIfNeeded()
        assertEquals(0, scrollState.scrollPosition.value)

        // Should NOT auto-scroll when not at bottom
        scrollState.scrollTo(50)
        manager.autoScrollToBottomIfNeeded()
        assertEquals(50, scrollState.scrollPosition.value)
    }

    @Test
    fun testProcessScrollEvent() {
        manager.config = manager.config.copy(smoothScrollEnabled = false)

        // Wheel event
        manager.processScrollEvent(ScrollEvent.Wheel(deltaY = 3f, isPixelDelta = false))
        assertTrue(scrollState.scrollPosition.value > 0)

        // ScrollTo event
        manager.processScrollEvent(ScrollEvent.ScrollTo(position = 50, smooth = false))
        assertEquals(50, scrollState.scrollPosition.value)

        // ScrollBy event
        manager.processScrollEvent(ScrollEvent.ScrollBy(delta = 10, smooth = false))
        assertEquals(60, scrollState.scrollPosition.value)

        // Keyboard event
        manager.processScrollEvent(ScrollEvent.Keyboard(ScrollKey.END))
        assertEquals(0, scrollState.scrollPosition.value)
    }

    @Test
    fun testSmoothScroll() = runTest {
        manager.config = manager.config.copy(
            smoothScrollEnabled = true,
            smoothScrollDurationMs = 100
        )

        val startPos = scrollState.scrollPosition.value
        manager.smoothScrollBy(50)

        // Allow animation to progress
        delay(50)

        // Should be animating
        val midPos = scrollState.scrollPosition.value
        assertTrue(midPos > startPos)
        assertTrue(midPos < 50)

        // Wait for animation to complete
        delay(100)

        // Should reach target
        assertEquals(50, scrollState.scrollPosition.value)
    }
}
