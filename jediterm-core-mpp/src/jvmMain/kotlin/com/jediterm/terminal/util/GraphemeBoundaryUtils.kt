package com.jediterm.terminal.util

/**
 * Utilities for finding safe grapheme boundaries when splitting text.
 * Used to prevent splitting multi-byte characters (emoji, surrogate pairs, ZWJ sequences).
 */
object GraphemeBoundaryUtils {
    /**
     * Finds the index of the last complete grapheme boundary in text.
     *
     * Returns the largest index where text can be safely split without breaking
     * a grapheme cluster. Characters from this index to the end form an incomplete
     * grapheme that should be buffered.
     *
     * Examples:
     * - "abc" -> 3 (all complete)
     * - "ab🎨" (emoji at end) -> 2 (emoji incomplete if at chunk boundary)
     * - "ab\uD835" (high surrogate at end) -> 2 (incomplete surrogate)
     * - "ab👨\u200D" (ZWJ at end) -> 2 (incomplete ZWJ sequence)
     *
     * @param text The text to analyze
     * @param maxLength Maximum length to check (optimization for large text)
     * @return Index after the last complete grapheme
     */
    fun findLastCompleteGraphemeBoundary(text: String, maxLength: Int = text.length): Int {
        if (text.isEmpty()) return 0
        if (maxLength <= 0) return 0

        val effectiveLength = minOf(text.length, maxLength)

        // Fast path: if last char is ASCII and not a grapheme extender, it's complete
        val lastChar = text[effectiveLength - 1]
        if (lastChar.code < 128 && !needsGraphemeAnalysis(lastChar)) {
            return effectiveLength
        }

        // Check the last few characters for incomplete graphemes
        // Max grapheme is ~20 chars for ZWJ sequences, check up to 30 to be safe
        val checkLength = minOf(effectiveLength, 30)
        val startIndex = effectiveLength - checkLength
        val tail = text.substring(startIndex, effectiveLength)

        // Get all grapheme boundaries in the tail
        val boundaries = GraphemeUtils.findGraphemeBoundaries(tail)

        if (boundaries.isEmpty()) {
            // No boundaries found - entire tail is one incomplete grapheme
            return startIndex
        }

        // The boundaries list includes 0 as the first boundary
        // Find the last boundary that's not at the end
        val lastBoundary = boundaries.last()

        return if (lastBoundary == tail.length) {
            // Last boundary is at the end - all complete
            effectiveLength
        } else {
            // Last boundary is before the end - incomplete grapheme from lastBoundary to end
            startIndex + lastBoundary
        }
    }

    /**
     * Checks if a character needs grapheme analysis.
     *
     * Fast heuristic to avoid expensive grapheme segmentation for simple ASCII.
     * Returns true for:
     * - Surrogate pairs (high/low surrogates)
     * - Zero-Width Joiner (ZWJ)
     * - Variation selectors (U+FE0E, U+FE0F)
     * - Combining diacritics
     * - Other grapheme extenders
     *
     * @param c The character to check
     * @return True if this character might be part of a complex grapheme
     */
    private fun needsGraphemeAnalysis(c: Char): Boolean {
        return c.isHighSurrogate() ||
               c.isLowSurrogate() ||
               c.code == 0x200D || // ZWJ
               c.code == 0xFE0E || c.code == 0xFE0F || // Variation selectors
               c.code in 0x0300..0x036F || // Combining diacritics
               GraphemeUtils.isGraphemeExtender(c)
    }
}
