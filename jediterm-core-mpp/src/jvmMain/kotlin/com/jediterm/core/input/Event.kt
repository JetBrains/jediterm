package com.jediterm.core.input

internal object Event {
    const val SHIFT_MASK: Int = 1
    @JvmField
    val ALT_MASK: Int = 1 shl 3
    @JvmField
    val CTRL_MASK: Int = 1 shl 1
    @JvmField
    val META_MASK: Int = 1 shl 2
}