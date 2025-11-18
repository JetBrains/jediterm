package com.jediterm.terminal

/**
 * Cursor shape as described by [DECSCUSR](https://vt100.net/docs/vt510-rm/DECSCUSR.html).
 */
enum class CursorShape {
    BLINK_BLOCK,
    STEADY_BLOCK,
    BLINK_UNDERLINE,
    STEADY_UNDERLINE,
    BLINK_VERTICAL_BAR,
    STEADY_VERTICAL_BAR;

    val isBlinking: Boolean
        get() = this == CursorShape.BLINK_BLOCK || this == CursorShape.BLINK_UNDERLINE || this == CursorShape.BLINK_VERTICAL_BAR
}
