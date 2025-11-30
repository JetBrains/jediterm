package com.jediterm.terminal.emulator.charset


class GraphicSetState {
    private val myGraphicSets: Array<GraphicSet>

    //in-use table graphic left (GL)
    private var myGL: GraphicSet? = null

    //in-use table graphic right (GR)
    private var myGR: GraphicSet? = null

    //Override for next char (used by shift-in and shift-out)
    private var myGlOverride: GraphicSet? = null

    // Whether to use GR mapping (160-255) through character sets
    // false = UTF-8 mode (preserve multi-byte sequences)
    // true = ISO-8859-1 mode (map through character sets)
    private var myUseGRMapping = false

    init {
        myGraphicSets = Array<GraphicSet>(4) { i -> GraphicSet(i) }

        resetState()
    }

    /**
     * Designates the given graphic set to the character set designator.
     *
     * @param graphicSet the graphic set to designate;
     * @param designator the designator of the character set.
     */
    fun designateGraphicSet(graphicSet: GraphicSet, designator: Char) {
        graphicSet.designation = CharacterSet.Companion.valueOf(designator)
    }


    fun designateGraphicSet(num: Int, characterSet: CharacterSet) {
        getGraphicSet(num).designation = characterSet
    }

    val gL: GraphicSet
        /**
         * Returns the (possibly overridden) GL graphic set.
         */
        get() {
            val override = myGlOverride
            if (override != null) {
                myGlOverride = null
                return override
            }
            return myGL ?: throw IllegalStateException("GL graphic set must be initialized")
        }

    val gR: GraphicSet
        /**
         * Returns the GR graphic set.
         */
        get() = myGR ?: throw IllegalStateException("GR graphic set must be initialized")

    /**
     * Returns the current graphic set (one of four).
     *
     * @param index the index of the graphic set, 0..3.
     */
    fun getGraphicSet(index: Int): GraphicSet {
        return myGraphicSets[index % 4]
    }

    /**
     * Returns the mapping for the given character.
     *
     * @param ch the character to map.
     * @return the mapped character.
     */
    fun map(ch: Char): Char {
        return CharacterSets.getChar(ch, this.gL, this.gR, myUseGRMapping)
    }

    /**
     * Sets whether to use GR mapping (160-255) through character sets.
     *
     * @param useGRMapping false for UTF-8 mode (preserve multi-byte sequences),
     *                     true for ISO-8859-1 mode (map through character sets)
     */
    fun setUseGRMapping(useGRMapping: Boolean) {
        myUseGRMapping = useGRMapping
    }

    /**
     * Returns whether GR mapping is enabled.
     *
     * @return true if GR mapping is enabled (ISO-8859-1 mode), false otherwise (UTF-8 mode)
     */
    fun isUseGRMapping(): Boolean {
        return myUseGRMapping
    }

    /**
     * Overrides the GL graphic set for the next written character.
     *
     * @param index the graphic set index, >= 0 && < 3.
     */
    fun overrideGL(index: Int) {
        myGlOverride = getGraphicSet(index)
    }

    /**
     * Resets the state to its initial values.
     */
    fun resetState() {
        for (i in myGraphicSets.indices) {
            myGraphicSets[i].designation = CharacterSet.Companion.valueOf(if (i == 1) '0' else 'B')
        }
        myGL = myGraphicSets[0]
        myGR = myGraphicSets[1]
        myGlOverride = null
    }

    /**
     * Selects the graphic set for GL.
     *
     * @param index the graphic set index, >= 0 && <= 3.
     */
    fun setGL(index: Int) {
        myGL = getGraphicSet(index)
    }

    /**
     * Selects the graphic set for GR.
     *
     * @param index the graphic set index, >= 0 && <= 3.
     */
    fun setGR(index: Int) {
        myGR = getGraphicSet(index)
    }


    val gLOverrideIndex: Int
        get() = myGlOverride?.index ?: -1
}