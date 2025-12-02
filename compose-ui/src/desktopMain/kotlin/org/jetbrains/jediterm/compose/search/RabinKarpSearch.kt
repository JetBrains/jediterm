package org.jetbrains.jediterm.compose.search

import com.jediterm.terminal.model.BufferSnapshot

/**
 * Rabin-Karp substring search implementation for terminal buffer.
 *
 * This algorithm provides O(n+m) average case complexity vs O(n*m) for naive indexOf(),
 * which is significant for large scrollback buffers (10,000+ lines).
 *
 * Based on the legacy SubstringFinder.java implementation from the ui module.
 */
object RabinKarpSearch {
    private const val HASH_BASE = 31

    /**
     * Search result containing column and row coordinates.
     */
    data class Match(val column: Int, val row: Int)

    /**
     * Search for all occurrences of pattern in a single line of text.
     *
     * @param text The text to search in
     * @param pattern The pattern to find
     * @param row The row number for result coordinates
     * @param ignoreCase If true, performs case-insensitive search
     * @return List of matches found in this line
     */
    fun searchLine(
        text: String,
        pattern: String,
        row: Int,
        ignoreCase: Boolean = false
    ): List<Match> {
        if (pattern.isEmpty() || text.length < pattern.length) {
            return emptyList()
        }

        val matches = mutableListOf<Match>()
        val patternLen = pattern.length
        val textLen = text.length

        // Compute pattern hash and initial window hash
        val patternHash = computeHash(pattern, ignoreCase)
        var windowHash = computeHash(text.substring(0, patternLen), ignoreCase)

        // Precompute the power of HASH_BASE for rolling
        val power = computePower(patternLen)

        // Check first window
        if (windowHash == patternHash && matchesAt(text, 0, pattern, ignoreCase)) {
            matches.add(Match(column = 0, row = row))
        }

        // Roll through the text
        for (i in 1..textLen - patternLen) {
            // Roll the hash: remove leftmost char, add rightmost char
            windowHash = rollHash(
                currentHash = windowHash,
                oldChar = text[i - 1],
                newChar = text[i + patternLen - 1],
                power = power,
                ignoreCase = ignoreCase
            )

            // Check for match on hash collision
            if (windowHash == patternHash && matchesAt(text, i, pattern, ignoreCase)) {
                matches.add(Match(column = i, row = row))
            }
        }

        return matches
    }

    /**
     * Search for all occurrences of pattern across the entire terminal buffer.
     *
     * @param snapshot Immutable buffer snapshot for lock-free searching
     * @param pattern The pattern to find
     * @param ignoreCase If true, performs case-insensitive search
     * @return List of all matches with coordinates
     */
    fun searchBuffer(
        snapshot: BufferSnapshot,
        pattern: String,
        ignoreCase: Boolean = false
    ): List<Match> {
        if (pattern.isEmpty()) {
            return emptyList()
        }

        val matches = mutableListOf<Match>()

        // Search from history through screen buffer
        // Negative rows are history, 0+ are screen buffer
        for (row in -snapshot.historyLinesCount until snapshot.height) {
            val line = snapshot.getLine(row)
            val text = line.text

            val lineMatches = searchLine(text, pattern, row, ignoreCase)
            matches.addAll(lineMatches)
        }

        return matches
    }

    /**
     * Compute the polynomial hash of a string.
     * hash = s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]*31^0
     */
    private fun computeHash(s: String, ignoreCase: Boolean): Int {
        var hash = 0
        for (c in s) {
            hash = HASH_BASE * hash + charHash(c, ignoreCase)
        }
        return hash
    }

    /**
     * Compute HASH_BASE^(length-1) for the rolling hash removal step.
     */
    private fun computePower(length: Int): Int {
        var power = 1
        repeat(length - 1) {
            power *= HASH_BASE
        }
        return power
    }

    /**
     * Roll the hash window by one character.
     * Removes the contribution of oldChar and adds newChar.
     */
    private fun rollHash(
        currentHash: Int,
        oldChar: Char,
        newChar: Char,
        power: Int,
        ignoreCase: Boolean
    ): Int {
        val oldContribution = power * charHash(oldChar, ignoreCase)
        return HASH_BASE * (currentHash - oldContribution) + charHash(newChar, ignoreCase)
    }

    /**
     * Get the hash value for a single character.
     */
    private fun charHash(c: Char, ignoreCase: Boolean): Int {
        return if (ignoreCase) c.lowercaseChar().code else c.code
    }

    /**
     * Verify actual match at position (handles hash collisions).
     */
    private fun matchesAt(text: String, index: Int, pattern: String, ignoreCase: Boolean): Boolean {
        for (i in pattern.indices) {
            val textChar = text[index + i]
            val patternChar = pattern[i]
            val matches = if (ignoreCase) {
                textChar.lowercaseChar() == patternChar.lowercaseChar()
            } else {
                textChar == patternChar
            }
            if (!matches) return false
        }
        return true
    }
}
