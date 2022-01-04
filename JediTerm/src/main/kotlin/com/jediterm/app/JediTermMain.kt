package com.jediterm.app

import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Pair
import com.intellij.util.EncodingEnvironmentUtil
import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.LoggingTtyConnector
import com.jediterm.terminal.LoggingTtyConnector.TerminalState
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.TerminalWidget
import com.jediterm.terminal.ui.UIUtil
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider
import com.jediterm.ui.AbstractTerminalFrame
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities

object JediTermMain {
    @JvmStatic
    fun main(arg: Array<String>) {
        Logger.getLogger("").level = Level.INFO

        SwingUtilities.invokeLater {
            JediTerm()
        }
    }
}

class JediTerm : AbstractTerminalFrame(), Disposable {
    override fun dispose() {
        // TODO
    }

    override fun createTabbedTerminalWidget(): JediTabbedTerminalWidget {
        return object : JediTabbedTerminalWidget(
            DefaultTabbedSettingsProvider(),
            Function<Pair<TerminalWidget, String>, JediTerminalWidget> { pair -> openSession(pair?.first) as JediTerminalWidget },
            this
        ) {
            override fun createInnerTerminalWidget(): JediTerminalWidget {
                return createTerminalWidget(settingsProvider)
            }
        }
    }

    override fun createTtyConnector(): TtyConnector {
        try {
            val charset = StandardCharsets.UTF_8
            val envs = HashMap(System.getenv())
            EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, charset)
            val command: Array<String> = if (UIUtil.isWindows) {
                arrayOf("powershell.exe")
            }
            else {
                envs["TERM"] = "xterm-256color"
                val shell = envs["SHELL"] ?: "/bin/bash"
                if (UIUtil.isMac) arrayOf(shell, "--login") else arrayOf(shell)
            }

            LOG.info("Starting ${command.joinToString()}")
            val process = PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(envs)
                .setConsole(false)
                .setUseWinConPty(true)
                .start()

            return LoggingPtyProcessTtyConnector(process, charset, command.toList())
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

    }

    override fun createTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTerminalWidget {
        val widget = JediTerminalWidget(settingsProvider, this)
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
                    terminalTextBuffer.styleLines,
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