package com.jediterm.terminal.emulator

import com.jediterm.core.util.Ascii.BEL_CHAR
import com.jediterm.core.util.Ascii.ESC_CHAR
import com.jediterm.terminal.ArrayTerminalDataStream
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

class SystemCommandSequenceTest {

  @Test
  fun basic() {
    assertArgs("foo$BEL_CHAR", listOf("foo"))
  }

  @Test
  fun `terminated with two bytes`() {
    assertArgs("bar$TWO_BYTES_TERMINATOR", listOf("bar"))
  }

  @Test
  fun `parsed args`() {
    assertArgs("0;My title$BEL_CHAR", listOf("0", "My title"))
    assertArgs("0;My title;$BEL_CHAR", listOf("0", "My title", ""))
    assertArgs(";0;My title$BEL_CHAR", listOf("", "0", "My title"))
    assertArgs(";0;My title;$BEL_CHAR", listOf("", "0", "My title", ""))
  }

  @Test
  fun `format using same terminator`() {
    val seq1 = create("2;Test 1$BEL_CHAR")
    Assert.assertEquals("$ESC_CHAR]foo$BEL_CHAR", seq1.format(listOf("foo")))
    val seq2 = create("2;Test 1$TWO_BYTES_TERMINATOR")
    Assert.assertEquals("$ESC_CHAR]bar;baz$TWO_BYTES_TERMINATOR", seq2.format(listOf("bar", "baz")))
  }

  @Suppress("unused")
  fun `perf test`() {
    val args = 1000
    val text = MutableList(args) { "my arg" }.joinToString(";") + BEL_CHAR
    // warmup
    repeat(100000) {
      Assert.assertEquals(args, create(text).args.size)
    }
    val startNano = System.nanoTime()
    repeat(100000) {
      Assert.assertEquals(args, create(text).args.size)
    }
    val elapsedTimeNano: Long = System.nanoTime() - startNano
    println("Elapsed Time: ${TimeUnit.NANOSECONDS.toMillis(elapsedTimeNano)} ms")
  }

  private fun create(text: String): SystemCommandSequence {
    val dataStream = ArrayTerminalDataStream(text.toCharArray())
    return SystemCommandSequence(dataStream)
  }

  private fun assertArgs(text: String, expectedArgs: List<String>) {
    val command = create(text)
    Assert.assertEquals(expectedArgs, command.args)
  }

  companion object {
    private const val TWO_BYTES_TERMINATOR: String = "$ESC_CHAR\\"
  }
}
