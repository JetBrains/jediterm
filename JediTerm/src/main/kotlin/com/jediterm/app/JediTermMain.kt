package com.jediterm.app

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.LoggingTtyConnector
import com.jediterm.terminal.LoggingTtyConnector.TerminalState
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.jediterm.ui.AbstractTerminalFrame
import com.jediterm.ui.debug.TerminalDebugUtil
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import javax.swing.SwingUtilities
import kotlin.io.path.pathString

object JediTermMain {
  @JvmStatic
  fun main(arg: Array<String>) {
    configureJavaUtilLogging()

    SwingUtilities.invokeLater {
      JediTerm()
    }
  }

  private fun configureJavaUtilLogging() {
    val format = "[%1\$tF %1\$tT] [%4\\\$-7s] %5\$s %n"
    LogManager.getLogManager().readConfiguration("java.util.logging.SimpleFormatter.format=$format".byteInputStream())

    val rootLogger = Logger.getLogger("")
    rootLogger.addHandler(ConsoleHandler().also {
      it.level = Level.ALL
    })
    rootLogger.level = Level.INFO
  }
}

class JediTerm : AbstractTerminalFrame() {
  override fun createTtyConnector(): TtyConnector {
    try {
      val envs: Map<String, String> = configureEnvironmentVariables()
      val command: Array<String> = if (isWindows()) {
        arrayOf("powershell.exe")
      }
      else {
        val shell = envs["SHELL"] ?: "/bin/bash"
        if (isMacOS()) arrayOf(shell, "--login") else arrayOf(shell)
      }
      val workingDirectory = Path.of(".").toAbsolutePath().normalize().pathString

      LOG.info("Starting ${command.joinToString()} in $workingDirectory")
      val process = PtyProcessBuilder()
        .setDirectory(workingDirectory)
        .setInitialColumns(120)
        .setInitialRows(20)
        .setCommand(command)
        .setEnvironment(envs)
        .setConsole(false)
        .setUseWinConPty(true)
        .start()

      return LoggingPtyProcessTtyConnector(process, StandardCharsets.UTF_8, command.toList())
    }
    catch (e: Exception) {
      throw IllegalStateException(e)
    }

  }

  private fun configureEnvironmentVariables(): Map<String, String> {
    val envs = HashMap(System.getenv())
    if (isMacOS()) {
      envs["LC_CTYPE"] = Charsets.UTF_8.name()
    }
    if (!isWindows()) {
      envs["TERM"] = "xterm-256color"
    }
    return envs
  }

  override fun createTerminalWidget(settingsProvider: SettingsProvider): JediTermWidget {
    val widget = JediTermWidget(settingsProvider)
    widget.addHyperlinkFilter(UrlFilter())
    return widget
  }

  class LoggingPtyProcessTtyConnector(
    process: PtyProcess,
    charset: Charset,
    command: List<String>
  ) :
    PtyProcessTtyConnector(process, charset, command), LoggingTtyConnector {
    private val MAX_LOG_SIZE = 200

    private val myDataChunks = LinkedList<CharArray>()
    private val myStates = LinkedList<TerminalState>()
    private var myWidget: JediTermWidget? = null
    private var logStart = 0

    @Throws(IOException::class)
    override fun read(buf: CharArray, offset: Int, length: Int): Int {
      val len = super.read(buf, offset, length)
      if (len > 0) {
        val arr = buf.copyOfRange(offset, len)
        myDataChunks.add(arr)

        val terminalTextBuffer = myWidget!!.terminalTextBuffer
        val terminalState = TerminalState(
          terminalTextBuffer.screenLines,
          TerminalDebugUtil.getStyleLines(terminalTextBuffer),
          terminalTextBuffer.historyBuffer.lines
        )
        myStates.add(terminalState)

        if (myDataChunks.size > MAX_LOG_SIZE) {
          myDataChunks.removeFirst()
          myStates.removeFirst()
          logStart++
        }
      }
      return len
    }

    override fun getChunks(): List<CharArray> {
      return ArrayList(myDataChunks)
    }

    override fun getStates(): List<TerminalState> {
      return ArrayList(myStates)
    }

    override fun getLogStart() = logStart

    @Throws(IOException::class)
    override fun write(string: String) {
      LOG.debug("Writing in OutputStream : $string")
      super.write(string)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray) {
      LOG.debug("Writing in OutputStream : " + bytes.contentToString() + " " + String(bytes))
      super.write(bytes)
    }

    fun setWidget(widget: JediTermWidget) {
      myWidget = widget
    }
  }
}