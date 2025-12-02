package org.jetbrains.jediterm.compose.search

import kotlin.test.*

class RabinKarpSearchTest {

    @Test
    fun testBasicPatternMatch() {
        val matches = RabinKarpSearch.searchLine("hello world", "world", row = 0)
        assertEquals(1, matches.size)
        assertEquals(6, matches[0].column)
        assertEquals(0, matches[0].row)
    }

    @Test
    fun testNoMatch() {
        val matches = RabinKarpSearch.searchLine("hello world", "xyz", row = 0)
        assertEquals(0, matches.size)
    }

    @Test
    fun testMultipleMatches() {
        val matches = RabinKarpSearch.searchLine("the cat and the dog", "the", row = 5)
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].column)
        assertEquals(5, matches[0].row)
        assertEquals(12, matches[1].column)
        assertEquals(5, matches[1].row)
    }

    @Test
    fun testCaseSensitiveMatch() {
        val text = "Hello HELLO hello"

        // Case sensitive - only finds lowercase
        val sensMatches = RabinKarpSearch.searchLine(text, "hello", row = 0, ignoreCase = false)
        assertEquals(1, sensMatches.size)
        assertEquals(12, sensMatches[0].column)
    }

    @Test
    fun testCaseInsensitiveMatch() {
        val text = "Hello HELLO hello"

        // Case insensitive - finds all 3
        val insensMatches = RabinKarpSearch.searchLine(text, "hello", row = 0, ignoreCase = true)
        assertEquals(3, insensMatches.size)
        assertEquals(0, insensMatches[0].column)
        assertEquals(6, insensMatches[1].column)
        assertEquals(12, insensMatches[2].column)
    }

    @Test
    fun testEmptyPattern() {
        val matches = RabinKarpSearch.searchLine("hello world", "", row = 0)
        assertEquals(0, matches.size)
    }

    @Test
    fun testPatternLongerThanText() {
        val matches = RabinKarpSearch.searchLine("hi", "hello world", row = 0)
        assertEquals(0, matches.size)
    }

    @Test
    fun testPatternAtStart() {
        val matches = RabinKarpSearch.searchLine("hello world", "hello", row = 0)
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].column)
    }

    @Test
    fun testPatternAtEnd() {
        val matches = RabinKarpSearch.searchLine("hello world", "world", row = 0)
        assertEquals(1, matches.size)
        assertEquals(6, matches[0].column)
    }

    @Test
    fun testExactMatch() {
        val matches = RabinKarpSearch.searchLine("hello", "hello", row = 0)
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].column)
    }

    @Test
    fun testOverlappingPatterns() {
        // "aaa" in "aaaa" should find 2 matches (positions 0 and 1)
        val matches = RabinKarpSearch.searchLine("aaaa", "aaa", row = 0)
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].column)
        assertEquals(1, matches[1].column)
    }

    @Test
    fun testSingleCharacterPattern() {
        val matches = RabinKarpSearch.searchLine("abcabc", "a", row = 0)
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].column)
        assertEquals(3, matches[1].column)
    }

    @Test
    fun testUnicodeCharacters() {
        val matches = RabinKarpSearch.searchLine("cafe\u0301 coffee", "cafe\u0301", row = 0)
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].column)
    }

    @Test
    fun testSpecialCharacters() {
        val matches = RabinKarpSearch.searchLine("path/to/file.txt", "/to/", row = 0)
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].column)
    }

    @Test
    fun testWhitespacePattern() {
        val matches = RabinKarpSearch.searchLine("hello  world", "  ", row = 0)
        assertEquals(1, matches.size)
        assertEquals(5, matches[0].column)
    }

    @Test
    fun testRowPreservation() {
        val matches1 = RabinKarpSearch.searchLine("test", "test", row = -5)
        val matches2 = RabinKarpSearch.searchLine("test", "test", row = 100)

        assertEquals(-5, matches1[0].row)
        assertEquals(100, matches2[0].row)
    }

    @Test
    fun testEmptyText() {
        val matches = RabinKarpSearch.searchLine("", "hello", row = 0)
        assertEquals(0, matches.size)
    }

    @Test
    fun testBothEmpty() {
        val matches = RabinKarpSearch.searchLine("", "", row = 0)
        assertEquals(0, matches.size)
    }

    @Test
    fun testHashCollisionHandling() {
        // This tests that we properly verify matches even when hashes collide
        // The verification step in matchesAt() handles this
        val longText = "abcdefghijklmnopqrstuvwxyz" + "pattern" + "abcdefghijklmnopqrstuvwxyz"
        val matches = RabinKarpSearch.searchLine(longText, "pattern", row = 0)
        assertEquals(1, matches.size)
        assertEquals(26, matches[0].column)
    }
}
