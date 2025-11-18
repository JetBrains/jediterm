package com.jediterm.terminal.emulator.mouse

/**
 * @author traff
 */
object MouseButtonModifierFlags {
    // keyboard modifier flag
    //  4 - shift
    //  8 - meta
    //  16 - ctrl
    const val MOUSE_BUTTON_SHIFT_FLAG: Int = 4
    const val MOUSE_BUTTON_META_FLAG: Int = 8
    const val MOUSE_BUTTON_CTRL_FLAG: Int = 16

    // button motion flag
    //  32 - this is button motion event
    const val MOUSE_BUTTON_MOTION_FLAG: Int = 32

    // scroll flag
    //  64 - this is scroll event
    const val MOUSE_BUTTON_SCROLL_FLAG: Int = 64

    // for SGR 1006 style, internal use only 
    //  128 - mouse button is released
    const val MOUSE_BUTTON_SGR_RELEASE_FLAG: Int = 128
}
