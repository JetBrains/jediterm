package com.jediterm.terminal.util

/**
 * Represents a single grapheme cluster - the smallest unit of text that should be treated
 * as indivisible from a user's perspective.
 *
 * Examples:
 * - Single BMP character: "a"
 * - Surrogate pair: "𝕳" (U+1D573, Mathematical Bold H)
 * - Emoji with variation selector: "☁️" (cloud + U+FE0F)
 * - ZWJ sequence: "👨‍👩‍👧‍👦" (family emoji)
 * - Emoji with skin tone: "👍🏽" (thumbs up + medium skin tone)
 * - Combining characters: "á" (a + combining acute accent)
 *
 * @property text The string representation of this grapheme cluster
 * @property visualWidth The number of terminal cells this grapheme occupies (0, 1, or 2)
 * @property codePoints Array of Unicode code points that make up this grapheme
 */
data class GraphemeCluster(
    val text: String,
    val visualWidth: Int,
    val codePoints: IntArray
) {
    /**
     * Checks if this grapheme is a combining character sequence (width 0).
     * Combining characters overlay on the base character.
     */
    val isCombining: Boolean
        get() = visualWidth == 0

    /**
     * Checks if this grapheme contains emoji characters.
     * This includes emoji in supplementary planes (U+1F000+) and emoji presentation variants.
     */
    val isEmoji: Boolean
        get() {
            if (codePoints.isEmpty()) return false
            val first = codePoints[0]
            return when {
                // Emoji & Pictographs (U+1F300-U+1F9FF)
                first in 0x1F300..0x1F9FF -> true
                // Emoticons (U+1F600-U+1F64F)
                first in 0x1F600..0x1F64F -> true
                // Transport & Map Symbols (U+1F680-U+1F6FF)
                first in 0x1F680..0x1F6FF -> true
                // Supplemental Symbols (U+1F900-U+1F9FF)
                first in 0x1F900..0x1F9FF -> true
                // Misc Symbols with emoji presentation
                hasVariationSelector(0xFE0F) -> true
                else -> false
            }
        }

    /**
     * Checks if this grapheme contains a variation selector (U+FE0E for text, U+FE0F for emoji).
     */
    fun hasVariationSelector(selector: Int? = null): Boolean {
        return if (selector != null) {
            codePoints.contains(selector)
        } else {
            codePoints.contains(0xFE0E) || codePoints.contains(0xFE0F)
        }
    }

    /**
     * Checks if this grapheme contains a skin tone modifier (U+1F3FB-U+1F3FF).
     */
    val hasSkinTone: Boolean
        get() = codePoints.any { it in 0x1F3FB..0x1F3FF }

    /**
     * Checks if this grapheme contains a Zero-Width Joiner (U+200D).
     * ZWJ is used to join multiple emoji into a single visual unit.
     */
    val hasZWJ: Boolean
        get() = codePoints.contains(0x200D)

    /**
     * Checks if this grapheme is a surrogate pair (outside BMP, U+10000+).
     */
    val isSurrogatePair: Boolean
        get() = codePoints.isNotEmpty() && codePoints[0] >= 0x10000

    /**
     * Checks if this grapheme is double-width (occupies 2 terminal cells).
     * Common for CJK characters, fullwidth forms, and emoji.
     */
    val isDoubleWidth: Boolean
        get() = visualWidth == 2

    /**
     * Returns a debug string representation showing code points.
     */
    fun toDebugString(): String {
        val codePointsStr = codePoints.joinToString(", ") { "U+%04X".format(it) }
        return "GraphemeCluster(text='$text', width=$visualWidth, codePoints=[$codePointsStr])"
    }

    // Custom equals/hashCode needed because IntArray doesn't have structural equality by default
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GraphemeCluster

        if (text != other.text) return false
        if (visualWidth != other.visualWidth) return false
        if (!codePoints.contentEquals(other.codePoints)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + visualWidth
        result = 31 * result + codePoints.contentHashCode()
        return result
    }

    companion object {
        /**
         * Creates a GraphemeCluster from a single character.
         * Fast path for simple ASCII characters.
         */
        fun fromChar(c: Char, width: Int): GraphemeCluster {
            return GraphemeCluster(
                text = c.toString(),
                visualWidth = width,
                codePoints = intArrayOf(c.code)
            )
        }

        /**
         * Creates a GraphemeCluster from a string and calculated width.
         * Extracts code points from the string.
         */
        fun fromString(text: String, width: Int): GraphemeCluster {
            val codePoints = mutableListOf<Int>()
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                codePoints.add(codePoint)
                offset += Character.charCount(codePoint)
            }
            return GraphemeCluster(
                text = text,
                visualWidth = width,
                codePoints = codePoints.toIntArray()
            )
        }
    }
}
