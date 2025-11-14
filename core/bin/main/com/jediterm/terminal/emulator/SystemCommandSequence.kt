package com.jediterm.terminal.emulator

import com.jediterm.core.util.Ascii
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.util.CharUtils
import java.io.IOException
import java.lang.StringBuilder

internal class SystemCommandSequence
@Throws(IOException::class) constructor(stream: TerminalDataStream) {

  private val text: String
  val args: List<String>

  init {
    val textBuf = StringBuilder()
    do {
      textBuf.append(stream.char)
    } while (!isTerminated(textBuf))
    text = textBuf.toString()
    val body = text.dropLast(terminatorLength(text))
    args = java.util.List.copyOf(body.split(ARG_SEPARATOR))
  }

  fun getStringAt(index: Int): String? = args.getOrNull(index)

  fun getIntAt(index: Int, defaultValue: Int): Int = parseInt(args.getOrNull(index), defaultValue)

  private fun parseInt(value: String?, defaultValue: Int): Int {
    return value?.toIntOrNull() ?: defaultValue
  }

  fun format(args: List<String>): String {
    // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
    // XTerm accepts either BEL  or ST  for terminating OSC
    // sequences, and when returning information, uses the same
    // terminator used in a query.
    return Ascii.ESC_CHAR + "]" + args.joinToString(ARG_SEPARATOR.toString()) + text.takeLast(terminatorLength(text))
  }

  override fun toString(): String = CharUtils.toHumanReadableText(Ascii.ESC_CHAR + "]" + text)

  internal companion object {
    private const val ST: Char = 0x9c.toChar()
    private const val ARG_SEPARATOR: Char = ';'
    internal const val OSC: Char = 0x9d.toChar() // C1 control code

    private fun isTerminated(text: CharSequence): Boolean {
      val len = text.length
      if (len > 0) {
        val ch = text[len - 1]
        return ch == Ascii.BEL_CHAR || ch == ST || isTwoBytesTerminator(text, len, ch)
      }
      return false
    }

    private fun isTwoBytesTerminator(text: CharSequence, length: Int, lastChar: Char): Boolean {
      return lastChar == '\\' && length >= 2 && text[length - 2] == Ascii.ESC_CHAR
    }

    private fun terminatorLength(text: CharSequence): Int {
      val length = text.length
      return if (isTwoBytesTerminator(text, length, text[length - 1])) 2 else 1
    }
  }
}
