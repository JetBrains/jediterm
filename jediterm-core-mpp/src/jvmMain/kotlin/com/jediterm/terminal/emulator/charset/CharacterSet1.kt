package com.jediterm.terminal.emulator.charset

/**
 * Provides an enum with names for the supported character sets.
 */
enum class CharacterSet

/**
 * Creates a new [CharacterSet] instance.
 *
 * @param myDesignations
 * the characters that designate this character set, cannot be
 * `null`.
 */(private vararg val myDesignations: Int) {
    ASCII('B'.code) {
        override fun map(index: Int): Int {
            return -1
        }
    },
    BRITISH('A'.code) {
        override fun map(index: Int): Int {
            if (index == 3) {
                // Pound sign...
                return '\u00a3'.code
            }
            return -1
        }
    },
    DANISH('E'.code, '6'.code) {
        override fun map(index: Int): Int {
            when (index) {
                32 -> return '\u00c4'.code
                59 -> return '\u00c6'.code
                60 -> return '\u00d8'.code
                61 -> return '\u00c5'.code
                62 -> return '\u00dc'.code
                64 -> return '\u00e4'.code
                91 -> return '\u00e6'.code
                92 -> return '\u00f8'.code
                93 -> return '\u00e5'.code
                94 -> return '\u00fc'.code
                else -> return -1
            }
        }
    },
    DEC_SPECIAL_GRAPHICS('0'.code, '2'.code) {
        override fun map(index: Int): Int {
            if (index >= 64 && index < 96) {
                return (CharacterSets.DEC_SPECIAL_CHARS?.get(index - 64)?.get(0) as? Char)?.code ?: -1
            }
            return -1
        }
    },
    // DEC Supplemental character set (partial ISO-8859-1, indices 0-63 only)
    // Note: For ANSI conformance levels 1/2, use ISO_LATIN_1 instead
    DEC_SUPPLEMENTAL('U'.code, '<'.code) {
        override fun map(index: Int): Int {
            if (index >= 0 && index < 64) {
                // Set the 8th bit...
                return index + 160
            }
            return -1
        }
    },
    ISO_LATIN_1('-'.code) {
        override fun map(index: Int): Int {
            // ISO Latin-1 supplemental character set (ISO-8859-1 upper half)
            // Maps indices 0-95 to Unicode 160-255 (GR range)
            // Note: Uses '-' designation to avoid conflict with BRITISH ('A')
            // In full escape sequences: ESC - A designates this to G1
            if (index >= 0 && index < 96) {
                return index + 160
            }
            return -1
        }
    },
    DUTCH('4'.code) {
        override fun map(index: Int): Int {
            when (index) {
                3 -> return '\u00a3'.code
                32 -> return '\u00be'.code
                59 -> return '\u0133'.code
                60 -> return '\u00bd'.code
                61 -> return '|'.code
                91 -> return '\u00a8'.code
                92 -> return '\u0192'.code
                93 -> return '\u00bc'.code
                94 -> return '\u00b4'.code
                else -> return -1
            }
        }
    },
    FINNISH('C'.code, '5'.code) {
        override fun map(index: Int): Int {
            when (index) {
                59 -> return '\u00c4'.code
                60 -> return '\u00d4'.code
                61 -> return '\u00c5'.code
                62 -> return '\u00dc'.code
                64 -> return '\u00e9'.code
                91 -> return '\u00e4'.code
                92 -> return '\u00f6'.code
                93 -> return '\u00e5'.code
                94 -> return '\u00fc'.code
                else -> return -1
            }
        }
    },
    FRENCH('R'.code) {
        override fun map(index: Int): Int {
            when (index) {
                3 -> return '\u00a3'.code
                32 -> return '\u00e0'.code
                59 -> return '\u00b0'.code
                60 -> return '\u00e7'.code
                61 -> return '\u00a6'.code
                91 -> return '\u00e9'.code
                92 -> return '\u00f9'.code
                93 -> return '\u00e8'.code
                94 -> return '\u00a8'.code
                else -> return -1
            }
        }
    },
    FRENCH_CANADIAN('Q'.code) {
        override fun map(index: Int): Int {
            when (index) {
                32 -> return '\u00e0'.code
                59 -> return '\u00e2'.code
                60 -> return '\u00e7'.code
                61 -> return '\u00ea'.code
                62 -> return '\u00ee'.code
                91 -> return '\u00e9'.code
                92 -> return '\u00f9'.code
                93 -> return '\u00e8'.code
                94 -> return '\u00fb'.code
                else -> return -1
            }
        }
    },
    GERMAN('K'.code) {
        override fun map(index: Int): Int {
            when (index) {
                32 -> return '\u00a7'.code
                59 -> return '\u00c4'.code
                60 -> return '\u00d6'.code
                61 -> return '\u00dc'.code
                91 -> return '\u00e4'.code
                92 -> return '\u00f6'.code
                93 -> return '\u00fc'.code
                94 -> return '\u00df'.code
                else -> return -1
            }
        }
    },
    ITALIAN('Y'.code) {
        override fun map(index: Int): Int {
            when (index) {
                3 -> return '\u00a3'.code
                32 -> return '\u00a7'.code
                59 -> return '\u00ba'.code
                60 -> return '\u00e7'.code
                61 -> return '\u00e9'.code
                91 -> return '\u00e0'.code
                92 -> return '\u00f2'.code
                93 -> return '\u00e8'.code
                94 -> return '\u00ec'.code
                else -> return -1
            }
        }
    },
    SPANISH('Z'.code) {
        override fun map(index: Int): Int {
            when (index) {
                3 -> return '\u00a3'.code
                32 -> return '\u00a7'.code
                59 -> return '\u00a1'.code
                60 -> return '\u00d1'.code
                61 -> return '\u00bf'.code
                91 -> return '\u00b0'.code
                92 -> return '\u00f1'.code
                93 -> return '\u00e7'.code
                else -> return -1
            }
        }
    },
    SWEDISH('H'.code, '7'.code) {
        override fun map(index: Int): Int {
            when (index) {
                32 -> return '\u00c9'.code
                59 -> return '\u00c4'.code
                60 -> return '\u00d6'.code
                61 -> return '\u00c5'.code
                62 -> return '\u00dc'.code
                64 -> return '\u00e9'.code
                91 -> return '\u00e4'.code
                92 -> return '\u00f6'.code
                93 -> return '\u00e5'.code
                94 -> return '\u00fc'.code
                else -> return -1
            }
        }
    },
    SWISS('='.code) {
        override fun map(index: Int): Int {
            when (index) {
                3 -> return '\u00f9'.code
                32 -> return '\u00e0'.code
                59 -> return '\u00e9'.code
                60 -> return '\u00e7'.code
                61 -> return '\u00ea'.code
                62 -> return '\u00ee'.code
                63 -> return '\u00e8'.code
                64 -> return '\u00f4'.code
                91 -> return '\u00e4'.code
                92 -> return '\u00f6'.code
                93 -> return '\u00fc'.code
                94 -> return '\u00fb'.code
                else -> return -1
            }
        }
    };

    /**
     * Maps the character with the given index to a character in this character
     * set.
     *
     * @param index
     * the index of the character set, >= 0 && < 128.
     * @return a mapped character, or -1 if no mapping could be made and the
     * ASCII value should be used.
     */
    abstract fun map(index: Int): Int

    /**
     * Returns whether or not the given designation character belongs to this
     * character set's set of designations.
     *
     * @param designation
     * the designation to test for.
     * @return `true` if the given designation character maps to this
     * character set, `false` otherwise.
     */
    private fun isDesignation(designation: Char): Boolean {
        for (myDesignation in myDesignations) {
            if (myDesignation == designation.code) {
                return true
            }
        }
        return false
    }

    companion object {
        // METHODS
        /**
         * Returns the [CharacterSet] for the given character.
         *
         * @param designation
         * the character to translate to a [CharacterSet].
         * @return a character set name corresponding to the given character,
         * defaulting to ASCII if no mapping could be made.
         */
        fun valueOf(designation: Char): CharacterSet {
            for (csn in entries) {
                if (csn.isDesignation(designation)) {
                    return csn
                }
            }
            return CharacterSet.ASCII
        }
    }
}
