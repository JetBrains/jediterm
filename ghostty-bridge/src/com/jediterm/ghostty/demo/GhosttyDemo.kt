package com.jediterm.ghostty.demo

import com.jediterm.ghostty.GhosttyTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcessBuilder
import java.nio.charset.StandardCharsets
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Swing demo: a [GhosttyTermWidget] (the standard JediTerm widget, but with the ghostty VT engine)
 * running a real login shell over a pty.
 *
 * Run with:
 * ```
 *   ./gradlew :ghostty-bridge:runGhosttyDemo
 * ```
 * which launches it on the JDK 25 toolchain with `--enable-native-access=ALL-UNNAMED` and
 * `-Dghostty.vt.lib=<repo>/ghostty/zig-out/lib/libghostty-vt.dylib`.
 */
object GhosttyDemo {

  @JvmStatic
  fun main(args: Array<String>) {
    val columns = 100
    val rows = 30
    SwingUtilities.invokeLater {
      try {
        val widget = GhosttyTermWidget(columns, rows, DefaultSettingsProvider())

        val shell = System.getenv()["SHELL"] ?: "/bin/bash"
        val command = listOf(shell, "--login")
        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"
        val process = PtyProcessBuilder(command.toTypedArray())
          .setEnvironment(env)
          .setInitialColumns(columns)
          .setInitialRows(rows)
          .start()

        widget.createTerminalSession(DemoPtyConnector(process, StandardCharsets.UTF_8, command))
        widget.start()

        val frame = JFrame("JediTerm × ghostty")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane = widget
        frame.setSize(900, 600)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        widget.requestFocusInWindow()
      } catch (e: Exception) {
        e.printStackTrace()
        System.exit(1)
      }
    }
  }
}
