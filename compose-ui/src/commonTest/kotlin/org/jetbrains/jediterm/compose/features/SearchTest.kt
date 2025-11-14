package org.jetbrains.jediterm.compose.features

import androidx.compose.ui.graphics.Color
import kotlin.test.*

class SearchTest {

    private val sampleBuffer = listOf(
        "The quick brown fox",
        "jumps over the lazy dog",
        "The fox is quick and clever",
        "QUICK movements are important"
    )

    @Test
    fun testSearchControllerInitialization() {
        val controller = SearchController()
        val state = controller.searchState.value

        assertFalse(state.isActive)
        assertEquals("", state.query)
        assertEquals(0, state.matchCount)
        assertFalse(state.hasMatches)
    }

    @Test
    fun testStartSearch() {
        val controller = SearchController()

        controller.startSearch("test", caseSensitive = false, useRegex = false)

        val state = controller.searchState.value
        assertTrue(state.isActive)
        assertEquals("test", state.query)
        assertFalse(state.isCaseSensitive)
        assertFalse(state.isRegex)
    }

    @Test
    fun testCloseSearch() {
        val controller = SearchController()

        controller.startSearch("test")
        assertTrue(controller.searchState.value.isActive)

        controller.closeSearch()
        assertFalse(controller.searchState.value.isActive)
    }

    @Test
    fun testPlainTextSearch() {
        val matches = searchInBuffer(sampleBuffer, "quick")

        assertEquals(3, matches.size)
        assertEquals(0, matches[0].row)
        assertEquals(1, matches[1].row) // In "lazy"? No, it's not there
        assertEquals(2, matches[1].row)
    }

    @Test
    fun testCaseSensitiveSearch() {
        val matchesInsensitive = searchInBuffer(sampleBuffer, "quick", caseSensitive = false)
        val matchesSensitive = searchInBuffer(sampleBuffer, "quick", caseSensitive = true)

        assertTrue(matchesInsensitive.size > matchesSensitive.size)
        assertEquals(2, matchesSensitive.size) // "quick" appears twice in lowercase
    }

    @Test
    fun testRegexSearch() {
        val matches = searchInBuffer(sampleBuffer, "\\bfox\\b", useRegex = true)

        assertEquals(2, matches.size)
        assertEquals(0, matches[0].row)
        assertEquals(2, matches[1].row)
    }

    @Test
    fun testRegexSearchWithPattern() {
        val matches = searchInBuffer(sampleBuffer, "q[a-z]+k", useRegex = true)

        assertTrue(matches.size >= 2)
        assertEquals("quick", matches[0].matchedText.lowercase())
    }

    @Test
    fun testSearchMultipleMatches() {
        val buffer = listOf("the the the")
        val matches = searchInBuffer(buffer, "the")

        assertEquals(3, matches.size)
        assertEquals(0, matches[0].startColumn)
        assertEquals(4, matches[1].startColumn)
        assertEquals(8, matches[2].startColumn)
    }

    @Test
    fun testEmptyQueryReturnsNoMatches() {
        val matches = searchInBuffer(sampleBuffer, "")

        assertEquals(0, matches.size)
    }

    @Test
    fun testInvalidRegexReturnsNoMatches() {
        val matches = searchInBuffer(sampleBuffer, "[invalid(", useRegex = true)

        assertEquals(0, matches.size)
    }

    @Test
    fun testNavigateToNextMatch() {
        val controller = SearchController()
        controller.startSearch("test")

        val matches = listOf(
            SearchController.SearchMatch(0, 0, 4, "test"),
            SearchController.SearchMatch(1, 5, 9, "test"),
            SearchController.SearchMatch(2, 10, 14, "test")
        )
        controller.updateMatches(matches)

        assertEquals(0, controller.searchState.value.currentMatchIndex)

        controller.nextMatch()
        assertEquals(1, controller.searchState.value.currentMatchIndex)

        controller.nextMatch()
        assertEquals(2, controller.searchState.value.currentMatchIndex)

        controller.nextMatch() // Wrap around
        assertEquals(0, controller.searchState.value.currentMatchIndex)
    }

    @Test
    fun testNavigateToPreviousMatch() {
        val controller = SearchController()
        controller.startSearch("test")

        val matches = listOf(
            SearchController.SearchMatch(0, 0, 4, "test"),
            SearchController.SearchMatch(1, 5, 9, "test"),
            SearchController.SearchMatch(2, 10, 14, "test")
        )
        controller.updateMatches(matches)

        assertEquals(0, controller.searchState.value.currentMatchIndex)

        controller.previousMatch() // Wrap to end
        assertEquals(2, controller.searchState.value.currentMatchIndex)

        controller.previousMatch()
        assertEquals(1, controller.searchState.value.currentMatchIndex)

        controller.previousMatch()
        assertEquals(0, controller.searchState.value.currentMatchIndex)
    }

    @Test
    fun testToggleCaseSensitive() {
        val controller = SearchController()
        controller.startSearch("test", caseSensitive = false)

        assertFalse(controller.searchState.value.isCaseSensitive)

        controller.toggleCaseSensitive()
        assertTrue(controller.searchState.value.isCaseSensitive)

        controller.toggleCaseSensitive()
        assertFalse(controller.searchState.value.isCaseSensitive)
    }

    @Test
    fun testToggleRegex() {
        val controller = SearchController()
        controller.startSearch("test", useRegex = false)

        assertFalse(controller.searchState.value.isRegex)

        controller.toggleRegex()
        assertTrue(controller.searchState.value.isRegex)

        controller.toggleRegex()
        assertFalse(controller.searchState.value.isRegex)
    }

    @Test
    fun testGetSearchHighlightColor() {
        val matches = listOf(
            SearchController.SearchMatch(0, 5, 10, "quick"),
            SearchController.SearchMatch(1, 8, 13, "quick")
        )

        // Current match should have different color
        val currentColor = getSearchHighlightColor(0, 7, matches, 0)
        assertNotNull(currentColor)
        assertEquals(Color(0xFFFFAA00), currentColor)

        // Other match should have different color
        val otherColor = getSearchHighlightColor(1, 10, matches, 0)
        assertNotNull(otherColor)
        assertNotEquals(Color(0xFFFFAA00), otherColor)

        // No match position
        val noColor = getSearchHighlightColor(2, 5, matches, 0)
        assertNull(noColor)
    }

    @Test
    fun testSearchMatchPosition() {
        val match = SearchController.SearchMatch(5, 10, 15, "hello")

        assertEquals(5, match.row)
        assertEquals(10, match.startColumn)
        assertEquals(15, match.endColumn)
        assertEquals("hello", match.matchedText)
    }

    @Test
    fun testUpdateMatchesWithEmptyList() {
        val controller = SearchController()
        controller.startSearch("test")

        controller.updateMatches(emptyList())

        val state = controller.searchState.value
        assertFalse(state.hasMatches)
        assertEquals(0, state.matchCount)
        assertEquals(-1, state.currentMatchIndex)
    }
}
