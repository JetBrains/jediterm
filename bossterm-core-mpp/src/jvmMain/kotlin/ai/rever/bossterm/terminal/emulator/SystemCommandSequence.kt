package ai.rever.bossterm.terminal.emulator

import ai.rever.bossterm.core.util.Ascii
import ai.rever.bossterm.terminal.TerminalDataStream
import ai.rever.bossterm.terminal.util.CharUtils
import java.io.IOException
import java.lang.StringBuilder

internal class SystemCommandSequence
@Throws(IOException::class) constructor(stream: TerminalDataStream) {

  private val text: String
  val args: List<String>

  init {
    val textBuf = StringBuilder()
    // Large limit to support inline images (OSC 1337;File)
    // A 10MB image = ~13MB base64 = 13 million characters
    val maxLength = 50 * 1024 * 1024  // 50MB max for inline images

    do {
      if (textBuf.length >= maxLength) {
        throw IOException("OSC sequence exceeds maximum length of $maxLength characters")
      }

      val ch = stream.char
      if (ch == Ascii.NUL) {
        throw IOException("Unexpected end of stream in OSC sequence")
      }

      textBuf.append(ch)
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
    return Ascii.ESC + "]" + args.joinToString(ARG_SEPARATOR.toString()) + text.takeLast(terminatorLength(text))
  }

  override fun toString(): String = CharUtils.toHumanReadableText(Ascii.ESC.toString() + "]" + text)

  internal companion object {
    private const val ST: Char = 0x9c.toChar()
    private const val ARG_SEPARATOR: Char = ';'
    internal const val OSC: Char = 0x9d.toChar() // C1 control code

    private fun isTerminated(text: CharSequence): Boolean {
      val len = text.length
      if (len > 0) {
        val ch = text[len - 1]
        return ch == Ascii.BEL || ch == ST || isTwoBytesTerminator(text, len, ch)
      }
      return false
    }

    private fun isTwoBytesTerminator(text: CharSequence, length: Int, lastChar: Char): Boolean {
      return lastChar == '\\' && length >= 2 && text[length - 2] == Ascii.ESC
    }

    private fun terminatorLength(text: CharSequence): Int {
      val length = text.length
      return if (isTwoBytesTerminator(text, length, text[length - 1])) 2 else 1
    }
  }
}