package com.jediterm.terminal.util

/**
 * Width source: the generated [WcWidth] table from
 * [widecharwidth](https://github.com/ridiculousfish/widecharwidth/) (Unicode 17.0.0).
 *
 * Bundled, dependency-free and identical on every OS, so the buffer model and the renderer always
 * agree. The table folds in `emoji-data.txt`, so emoji with default emoji presentation (e.g. ✅, ❌)
 * are reported as double-width, which is the convention modern terminals follow.
 */
internal class WidecharwidthProvider : DoubleWidthProvider {

  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    if (codePoint !in 0..0x10FFFF) return false
    val type = WcWidth.Type.of(codePoint)
    if (type == WcWidth.Type.AMBIGUOUS) {
      return areAmbiguousCharactersDoubleWidth
    }
    return type.defaultWidth() == 2
  }

  override val name: String
    get() = "widecharwidth"
}
