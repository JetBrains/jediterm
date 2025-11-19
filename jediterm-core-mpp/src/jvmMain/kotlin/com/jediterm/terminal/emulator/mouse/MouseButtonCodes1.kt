package com.jediterm.terminal.emulator.mouse

/**
 * @author traff
 */
object MouseButtonCodes {
    // X11 button number
    val NONE: Int = -1 // no button
    const val LEFT: Int = 0 // left button
    const val MIDDLE: Int = 1 // middle button
    const val RIGHT: Int = 2 // right button
    const val RELEASE: Int = 3 // release - for 1000/1005/1015 mode
    const val SCROLLDOWN: Int = 4 // scroll down
    const val SCROLLUP: Int = 5 // scroll up
}
