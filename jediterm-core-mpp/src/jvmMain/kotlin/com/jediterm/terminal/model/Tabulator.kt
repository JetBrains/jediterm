package com.jediterm.terminal.model


/**
 * Provides a tabulator that keeps track of the tab stops of a terminal.
 */
interface Tabulator {
    /**
     * Clears the tab stop at the given position.
     *
     * @param position
     * the column position used to determine the next tab stop, > 0.
     */
    fun clearTabStop(position: Int)

    /**
     * Clears all tab stops.
     */
    fun clearAllTabStops()

    /**
     * Returns the width of the tab stop that is at or after the given position.
     *
     * @param position
     * the column position used to determine the next tab stop, >= 0.
     * @return the next tab stop width, >= 0.
     */
    fun getNextTabWidth(position: Int): Int

    /**
     * Returns the width of the tab stop that is before the given position.
     *
     * @param position
     * the column position used to determine the previous tab stop, >= 0.
     * @return the previous tab stop width, >= 0.
     */
    fun getPreviousTabWidth(position: Int): Int

    /**
     * Returns the next tab stop that is at or after the given position.
     *
     * @param position
     * the column position used to determine the next tab stop, >= 0.
     * @return the next tab stop, >= 0.
     */
    fun nextTab(position: Int): Int

    /**
     * Returns the previous tab stop that is before the given position.
     *
     * @param position
     * the column position used to determine the previous tab stop, >= 0.
     * @return the previous tab stop, >= 0.
     */
    fun previousTab(position: Int): Int

    /**
     * Sets the tab stop to the given position.
     *
     * @param position
     * the position of the (new) tab stop, > 0.
     */
    fun setTabStop(position: Int)

    fun resize(width: Int)
}
