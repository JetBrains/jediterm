package com.jediterm.terminal.util

class WidecharwidthProvider : DoubleWidthProvider {
  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    if (codePoint < 0 || codePoint > 0x10FFFF) return false
    val of = WcWidth.Type.of(codePoint)
    return of.defaultWidth() == 2
  }

  override val name: String
    get() = "widecharwidth"
}