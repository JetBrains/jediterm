package com.jediterm.terminal.emulator.charset

import com.jediterm.terminal.util.CharUtils

/**
 * Provides the (graphical) character sets.
 */
object CharacterSets {
    private const val C0_START = 0
    private const val C0_END = 31
    private const val C1_START = 128
    private const val C1_END = 159
    private const val GL_START = 32
    private const val GL_END = 127
    private const val GR_START = 160
    private const val GR_END = 255

    val ASCII_NAMES: Array<String?> = arrayOf<String?>(
        "<nul>", "<soh>", "<stx>", "<etx>", "<eot>", "<enq>", "<ack>", "<bell>",
        "\b", "\t", "\n", "<vt>", "<ff>", "\r", "<so>", "<si>", "<dle>", "<dc1>", "<dc2>", "<dc3>", "<dc4>", "<nak>",
        "<syn>", "<etb>", "<can>", "<em>", "<sub>", "<esc>", "<fs>", "<gs>", "<rs>", "<us>", " ", "!", "\"", "#", "$",
        "%", "&", "'", "(", ")", "*", "+", ",", "-", ".", "/", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":",
        ";", "<", "=", ">", "?", "@", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
        "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "[", "\\", "]", "^", "_", "`", "a", "b", "c", "d", "e", "f",
        "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "{", "|",
        "}", "~", "<del>"
    )

    /**
     * Denotes the mapping for C0 characters.
     */
    val C0_CHARS: Array<Array<Any?>?> = arrayOf<Array<Any?>?>(
        arrayOf<Any?>(0, "nul"),  //
        arrayOf<Any?>(0, "soh"),  //
        arrayOf<Any?>(0, "stx"),  //
        arrayOf<Any?>(0, "etx"),  //
        arrayOf<Any?>(0, "eot"),  //
        arrayOf<Any?>(0, "enq"),  //
        arrayOf<Any?>(0, "ack"),  //
        arrayOf<Any?>(0, "bel"),  //
        arrayOf<Any?>('\b'.code, "bs"),  //
        arrayOf<Any?>('\t'.code, "ht"),  //
        arrayOf<Any?>('\n'.code, "lf"),  //
        arrayOf<Any?>(0, "vt"),  //
        arrayOf<Any?>(0, "ff"),  //
        arrayOf<Any?>('\r'.code, "cr"),  //
        arrayOf<Any?>(0, "so"),  //
        arrayOf<Any?>(0, "si"),  //
        arrayOf<Any?>(0, "dle"),  //
        arrayOf<Any?>(0, "dc1"),  //
        arrayOf<Any?>(0, "dc2"),  //
        arrayOf<Any?>(0, "dc3"),  //
        arrayOf<Any?>(0, "dc4"),  //
        arrayOf<Any?>(0, "nak"),  //
        arrayOf<Any?>(0, "syn"),  //
        arrayOf<Any?>(0, "etb"),  //
        arrayOf<Any?>(0, "can"),  //
        arrayOf<Any?>(0, "em"),  //
        arrayOf<Any?>(0, "sub"),  //
        arrayOf<Any?>(0, "esq"),  //
        arrayOf<Any?>(0, "fs"),  //
        arrayOf<Any?>(0, "gs"),  //
        arrayOf<Any?>(0, "rs"),  //
        arrayOf<Any?>(0, "us")
    )

    /**
     * Denotes the mapping for C1 characters.
     */
    val C1_CHARS: Array<Array<Any?>?> = arrayOf<Array<Any?>?>(
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, "ind"),  //
        arrayOf<Any?>(0, "nel"),  //
        arrayOf<Any?>(0, "ssa"),  //
        arrayOf<Any?>(0, "esa"),  //
        arrayOf<Any?>(0, "hts"),  //
        arrayOf<Any?>(0, "htj"),  //
        arrayOf<Any?>(0, "vts"),  //
        arrayOf<Any?>(0, "pld"),  //
        arrayOf<Any?>(0, "plu"),  //
        arrayOf<Any?>(0, "ri"),  //
        arrayOf<Any?>(0, "ss2"),  //
        arrayOf<Any?>(0, "ss3"),  //
        arrayOf<Any?>(0, "dcs"),  //
        arrayOf<Any?>(0, "pu1"),  //
        arrayOf<Any?>(0, "pu2"),  //
        arrayOf<Any?>(0, "sts"),  //
        arrayOf<Any?>(0, "cch"),  //
        arrayOf<Any?>(0, "mw"),  //
        arrayOf<Any?>(0, "spa"),  //
        arrayOf<Any?>(0, "epa"),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, null),  //
        arrayOf<Any?>(0, "csi"),  //
        arrayOf<Any?>(0, "st"),  //
        arrayOf<Any?>(0, "osc"),  //
        arrayOf<Any?>(0, "pm"),  //
        arrayOf<Any?>(0, "apc")
    )

    /**
     * The DEC special characters (only the last 32 characters).
     * Contains [light][heavy] flavors for box drawing
     */
    val DEC_SPECIAL_CHARS: Array<Array<Any?>?> = arrayOf<Array<Any?>?>(
        arrayOf<Any?>('\u25c6', null),  // black_diamond
        arrayOf<Any?>('\u2592', null),  // Medium Shade
        arrayOf<Any?>('\u2409', null),  // Horizontal tab (HT)
        arrayOf<Any?>('\u240c', null),  // Form Feed (FF)
        arrayOf<Any?>('\u240d', null),  // Carriage Return (CR)
        arrayOf<Any?>('\u240a', null),  // Line Feed (LF)
        arrayOf<Any?>('\u00b0', null),  // Degree sign
        arrayOf<Any?>('\u00b1', null),  // Plus/minus sign
        arrayOf<Any?>('\u2424', null),  // New Line (NL)
        arrayOf<Any?>('\u240b', null),  // Vertical Tab (VT)
        arrayOf<Any?>('\u2518', '\u251b'),  // Forms up and left
        arrayOf<Any?>('\u2510', '\u2513'),  // Forms down and left
        arrayOf<Any?>('\u250c', '\u250f'),  // Forms down and right
        arrayOf<Any?>('\u2514', '\u2517'),  // Forms up and right
        arrayOf<Any?>('\u253c', '\u254b'),  // Forms vertical and horizontal
        arrayOf<Any?>('\u23ba', null),  // Scan 1
        arrayOf<Any?>('\u23bb', null),  // Scan 3
        arrayOf<Any?>('\u2500', '\u2501'),  // Scan 5 / Horizontal bar
        arrayOf<Any?>('\u23bc', null),  // Scan 7
        arrayOf<Any?>('\u23bd', null),  // Scan 9
        arrayOf<Any?>('\u251c', '\u2523'),  // Forms vertical and right
        arrayOf<Any?>('\u2524', '\u252b'),  // Forms vertical and left
        arrayOf<Any?>('\u2534', '\u253b'),  // Forms up and horizontal
        arrayOf<Any?>('\u252c', '\u2533'),  // Forms down and horizontal
        arrayOf<Any?>('\u2502', '\u2503'),  // vertical bar
        arrayOf<Any?>('\u2264', null),  // less than or equal sign
        arrayOf<Any?>('\u2265', null),  // greater than or equal sign
        arrayOf<Any?>('\u03c0', null),  // pi
        arrayOf<Any?>('\u2260', null),  // not equal sign
        arrayOf<Any?>('\u00a3', null),  // pound sign
        arrayOf<Any?>('\u00b7', null),  // middle dot
        arrayOf<Any?>(' ', null),  //
    )

    fun isDecBoxChar(c: Char): Boolean {
        if (c < '\u2500' || c >= '\u2580') { // fast path
            return false
        }
        for (o in DEC_SPECIAL_CHARS) {
            if (o != null && c == o[0] as Char?) {
                return true
            }
        }
        return false
    }

    fun getHeavyDecBoxChar(c: Char): Char {
        if (c < '\u2500' || c >= '\u2580') { // fast path
            return c
        }
        for (o in DEC_SPECIAL_CHARS) {
            if (o != null && c == o[0] as Char?) {
                return if (o[1] != null) o[1] as Char else c
            }
        }
        return c
    }

    // METHODS
    /**
     * Returns the character mapping for a given original value using the given
     * graphic sets GL and GR.
     *
     * @param original the original character to map;
     * @param gl       the GL graphic set, cannot be `null`;
     * @param gr       the GR graphic set, cannot be `null`.
     * @return the mapped character.
     */
    fun getChar(original: Char, gl: GraphicSet, gr: GraphicSet?): Char {
        return getChar(original, gl, gr, false)
    }

    /**
     * Returns the character mapping for a given original value using the given
     * graphic sets GL and GR.
     *
     * @param original the original character to map;
     * @param gl       the GL graphic set, cannot be `null`;
     * @param gr       the GR graphic set, cannot be `null`;
     * @param useGRMapping whether to map GR range (160-255) through character sets.
     *                     Use true for ISO-8859-1 mode, false for UTF-8 mode.
     * @return the mapped character.
     */
    fun getChar(original: Char, gl: GraphicSet, gr: GraphicSet?, useGRMapping: Boolean): Char {
        val ch = getMappedChar(original, gl, gr, useGRMapping)
        if (ch > 0) {
            return ch.toChar()
        }

        return CharUtils.NUL_CHAR
    }

    /**
     * Returns the name for the given character using the given graphic sets GL
     * and GR.
     *
     * @param original the original character to return the name for;
     * @param gl       the GL graphic set, cannot be `null`;
     * @param gr       the GR graphic set, cannot be `null`.
     * @return the character name.
     */
    fun getCharName(original: Char, gl: GraphicSet?, gr: GraphicSet?): String? {
        val cMapping = getCMapping(original)
        return if (cMapping != null) cMapping[1] as String? else String.format("<%d>", original.code)
    }

    /**
     * Returns the mapping for a given character using the given graphic sets GL
     * and GR.
     *
     * @param original the original character to map;
     * @param gl       the GL graphic set, cannot be `null`;
     * @param gr       the GR graphic set, cannot be `null`;
     * @param useGRMapping whether to map GR range (160-255) through character sets.
     * @return the mapped character.
     */
    private fun getMappedChar(original: Char, gl: GraphicSet, gr: GraphicSet?, useGRMapping: Boolean): Int {
        val cMapping = getCMapping(original)
        if (cMapping != null) {
            return cMapping[0] as Int
        } else if (original.code >= GL_START && original.code <= GL_END) {
            val idx = original.code - GL_START
            return gl.map(original, idx)
        }
        // GR range mapping (160-255): Enabled for ISO-8859-1 mode, disabled for UTF-8 mode
        // UTF-8 mode: Pass through GR range unchanged to preserve multi-byte sequences
        // ISO-8859-1 mode: Map GR range through character sets (e.g., ISO_LATIN_1)
        else if (useGRMapping && gr != null && original.code >= GR_START && original.code <= GR_END) {
            val idx = original.code - GR_START
            return gr.map(original, idx)
        } else {
            return original.code
        }
    }

    private fun getCMapping(original: Char): Array<Any?>? {
        if (original.code >= C0_START && original.code <= C0_END) {
            val idx = original.code - C0_START
            return C0_CHARS[idx]
        } else if (original.code >= C1_START && original.code <= C1_END) {
            val idx = original.code - C1_START
            return C1_CHARS[idx]
        }
        return null
    }
}

