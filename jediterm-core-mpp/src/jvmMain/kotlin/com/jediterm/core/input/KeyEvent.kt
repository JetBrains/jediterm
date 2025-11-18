package com.jediterm.core.input

object KeyEvent {
    @JvmField
    val VK_ENTER: Int = '\n'.code
    @JvmField
    val VK_BACK_SPACE: Int = '\b'.code
    @JvmField
    val VK_TAB: Int = '\t'.code
    const val VK_ESCAPE: Int = 0x1B
    const val VK_F1: Int = 0x70
    const val VK_F2: Int = 0x71
    const val VK_F3: Int = 0x72
    const val VK_F4: Int = 0x73
    const val VK_F5: Int = 0x74
    const val VK_F6: Int = 0x75
    const val VK_F7: Int = 0x76
    const val VK_F8: Int = 0x77
    const val VK_F9: Int = 0x78
    const val VK_F10: Int = 0x79
    const val VK_F11: Int = 0x7A
    const val VK_F12: Int = 0x7B
    const val VK_INSERT: Int = 0x9B
    const val VK_DELETE: Int = 0x7F
    const val VK_PAGE_UP: Int = 0x21
    const val VK_PAGE_DOWN: Int = 0x22
    const val VK_END: Int = 0x23
    const val VK_HOME: Int = 0x24
    const val VK_LEFT: Int = 0x25
    const val VK_UP: Int = 0x26
    const val VK_RIGHT: Int = 0x27
    const val VK_DOWN: Int = 0x28
    const val VK_KP_UP: Int = 0xE0
    const val VK_KP_DOWN: Int = 0xE1
    const val VK_KP_LEFT: Int = 0xE2
    const val VK_KP_RIGHT: Int = 0xE3
}
