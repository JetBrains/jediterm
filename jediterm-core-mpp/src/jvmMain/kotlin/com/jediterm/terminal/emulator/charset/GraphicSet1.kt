package com.jediterm.terminal.emulator.charset

/**
 * Denotes how a graphic set is designated.
 */
class GraphicSet
    (index: Int) {
    /**
     * @return the index of this graphics set.
     */
    val index: Int // 0..3
    /**
     * @return the designation of this graphic set.
     */
    /**
     * Sets the designation of this graphic set.
     */
    var designation: CharacterSet

    init {
        require(!(index < 0 || index > 3)) { "Invalid index!" }
        this.index = index
        // The default mapping, based on XTerm...
        this.designation = CharacterSet.Companion.valueOf(if (index == 1) '0' else 'B')
    }

    /**
     * Maps a given character index to a concrete character.
     *
     * @param original
     * the original character to map;
     * @param index
     * the index of the character to map.
     * @return the mapped character, or the given original if no mapping could
     * be made.
     */
    fun map(original: Char, index: Int): Int {
        var result = designation.map(index)
        if (result < 0) {
            // No mapping, simply return the given original one...
            result = original.code
        }
        return result
    }
}