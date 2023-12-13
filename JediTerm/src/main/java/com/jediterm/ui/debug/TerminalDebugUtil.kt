package com.jediterm.ui.debug

import com.jediterm.terminal.StyledTextConsumerAdapter
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalTextBuffer

object TerminalDebugUtil {

  @JvmStatic
  fun getStyleLines(textBuffer: TerminalTextBuffer): String {
    val style2IdMap: MutableMap<Int, Int> = HashMap()
    textBuffer.lock()
    try {
      val sb = StringBuilder()
      textBuffer.screenBuffer.processLines(0, textBuffer.height, object : StyledTextConsumerAdapter() {
        var count: Int = 0

        override fun consume(x: Int, y: Int, style: TextStyle, characters: CharBuffer, startRow: Int) {
          if (x == 0) {
            sb.append("\n")
          }
          val styleNum = style.hashCode()
          if (!style2IdMap.containsKey(styleNum)) {
            style2IdMap[styleNum] = count++
          }
          sb.append(String.format("%02d ", style2IdMap[styleNum]))
        }
      })
      return sb.toString()
    }
    finally {
      textBuffer.unlock()
    }
  }
}