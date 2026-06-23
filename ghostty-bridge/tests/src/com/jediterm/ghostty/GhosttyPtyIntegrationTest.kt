package com.jediterm.ghostty

import com.jediterm.ghostty.demo.DemoPtyConnector
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.emulator.Emulator
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.pty4j.PtyProcessBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Full real-pty integration: spawn an actual process over a pty, drive its output through the same
 * [GhosttyEmulator] the widget uses (connector → data stream → ghostty engine), and assert the
 * JediTerm [TerminalTextBuffer] reflects the output including SGR color. This is the non-GUI proof
 * of the pipeline the Swing demo runs.
 */
class GhosttyPtyIntegrationTest {

  @Test(timeout = 20000L)
  fun realProcessOutputRendersWithColor() {
    // Deterministic output: "A", green "B", reset, "C" (no newline).
    val command = listOf("/bin/sh", "-c", "printf 'A\\033[32mB\\033[0mC'")
    val env = HashMap(System.getenv())
    env["TERM"] = "xterm-256color"
    val process = PtyProcessBuilder(command.toTypedArray())
      .setEnvironment(env)
      .setInitialColumns(20)
      .setInitialRows(5)
      .start()

    val connector = DemoPtyConnector(process, StandardCharsets.UTF_8, command)

    val style = StyleState()
    val buffer = TerminalTextBuffer(20, 5, style)
    val engine = GhosttyTerminalEngine(NoopTerminalDisplay(), buffer, style)

    val emulator: Emulator = GhosttyEmulator(TtyBasedArrayDataStream(connector), engine)
    // Drain the pty through ghostty until the process exits and the stream reaches EOF.
    while (emulator.hasNext()) {
      emulator.next()
    }

    assertEquals("ABC", buffer.screenLinesStorage.get(0).text)
    // The middle 'B' was printed with SGR 32 -> palette index 2 (named green).
    assertEquals(TerminalColor.index(2), buffer.getStyleAt(1, 0)!!.foreground)

    engine.disconnected()
    process.waitFor()
  }
}
