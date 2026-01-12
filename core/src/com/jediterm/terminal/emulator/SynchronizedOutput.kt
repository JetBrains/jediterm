package com.jediterm.terminal.emulator

import com.jediterm.terminal.ArrayTerminalDataStream
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.model.JediTerminal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * https://github.com/contour-terminal/vt-extensions/blob/master/synchronized-output.md
 */
internal class SynchronizedOutput(
  private val dataStream: TerminalDataStream,
  private val terminal: Terminal,
) {

  private val buffer: StringBuilder = StringBuilder(8192)
  private val startTime: Long = System.currentTimeMillis()
  private var ended: Boolean = false

  fun await() {
    while (!ended) {
      try {
        val ch = dataStream.getChar()
        addChar(ch)
      }
      catch (e: IOException) {
        LOG.info("Aborting synchronized output", e)
        end()
      }
    }
  }

  private fun addChar(ch: Char) {
    if (ended) {
      throw IllegalStateException("Synchronized output already ended")
    }
    buffer.append(ch)
    buffer.dropSuffix(BEGIN_SYNC_OUTPUT_CSI)
    if (buffer.dropSuffix(END_SYNC_OUTPUT_CSI)) {
      end()
    }
    else if (buffer.length > MAX_BUFFER_SIZE) {
      LOG.debug("Buffer size exceeded, aborting synchronized output")
      end()
    }
    else if (System.currentTimeMillis() - startTime > TIMEOUT_MILLIS) {
      LOG.debug("Timeout exceeded, aborting synchronized output")
      end()
    }
  }

  private fun end() {
    if (ended) {
      throw IllegalStateException("Synchronized output already ended")
    }
    ended = true
    val charArray = buffer.charArray
    if (terminal !is JediTerminal) {
      dataStream.pushBackBuffer(charArray, charArray.size)
    }
    else {
      // Ensure the text buffer is not accessed during applying the synchronized output.
      // Otherwise, the inconsistent state of the text buffer will be observed, leading to flickering.
      terminal.terminalTextBuffer.modify {
        applySyncOutput(charArray, terminal)
      }
    }
  }

  private fun applySyncOutput(output: CharArray, terminal: JediTerminal) {
    val bufferDataStream = ArrayTerminalDataStream(output, 0, output.size)
    val newEmulator = JediEmulator(bufferDataStream, terminal)
    try {
      while (true) {
        val ch = bufferDataStream.getChar()
        newEmulator.processChar(ch, terminal)
      }
    }
    catch (_: TerminalDataStream.EOF) {
      // Expected, reached the end of buffer
    }
    catch (e: Exception) {
      LOG.error("Error applying synchronized output", e)
    }
  }

  companion object {

    private const val BEGIN_SYNC_OUTPUT_CSI: String = "\u001b[?2026h"

    private const val END_SYNC_OUTPUT_CSI: String = "\u001b[?2026l"

    /** Maximum time before a synchronized update is aborted (0.5 sec) */
    private const val TIMEOUT_MILLIS: Int = 500

    /** Maximum number of characters buffered in a synchronized output (1 MB) */
    private const val MAX_BUFFER_SIZE: Int = 0x100_000

    private val LOG: Logger = LoggerFactory.getLogger(SynchronizedOutput::class.java)

    private val StringBuilder.charArray: CharArray
      get() {
        return CharArray(this.length).also {
          this.getChars(0, this.length, it, 0)
        }
      }

    private fun StringBuilder.dropSuffix(suffix: String): Boolean {
      if (this.endsWith(suffix)) {
        this.delete(this.length - suffix.length, this.length)
        return true
      }
      return false
    }
  }
}
