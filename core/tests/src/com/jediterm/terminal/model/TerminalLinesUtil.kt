package com.jediterm.terminal.model

import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalLine.TextEntry
import com.jediterm.terminal.util.CharUtils
import com.jediterm.util.CharBufferUtil

@JvmOverloads
fun terminalLine(text: String, style: TextStyle = TextStyle()): TerminalLine {
  return TerminalLine(TextEntry(style, CharBufferUtil.create(text)))
}

fun createFillerEntry(width: Int): TextEntry {
  return TextEntry(TextStyle(), CharBuffer(CharUtils.NUL_CHAR, width))
}

fun LinesStorage.getLineTexts(): List<String> {
  val lines = ArrayList<String>(size)
  for (line in this) {
    lines.add(line.text)
  }
  return lines
}
