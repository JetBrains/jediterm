package com.jediterm.terminal

import com.jediterm.terminal.model.CharBuffer

/**
 * General interface that obtains styled range of characters at coordinates (**x**, **y**) when the screen starts at **startRow**
 *
 * @author traff
 */
interface StyledTextConsumer {
    /**
     *
     * @param x indicates starting column of the characters
     * @param y indicates row of the characters
     * @param style style of characters
     * @param characters text characters
     * @param startRow number of the first row.
     * It can be different for different buffers, e.g. backBuffer starts from 0, textBuffer and scrollBuffer from -count
     */
    fun consume(x: Int, y: Int, style: TextStyle, characters: CharBuffer, startRow: Int)

    fun consumeNul(x: Int, y: Int, nulIndex: Int, style: TextStyle, characters: CharBuffer, startRow: Int)

    fun consumeQueue(x: Int, y: Int, nulIndex: Int, startRow: Int)
}
