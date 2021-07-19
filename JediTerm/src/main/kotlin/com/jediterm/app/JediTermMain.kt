package com.jediterm.app

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Pair
import com.intellij.util.EncodingEnvironmentUtil
import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.LoggingTtyConnector
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.TerminalWidget
import com.jediterm.terminal.ui.UIUtil
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider
import com.jediterm.ui.AbstractTerminalFrame
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.function.Function
import javax.swing.SwingUtilities

object JediTermMain {
    @JvmStatic
    fun main(arg: Array<String>) {
        BasicConfigurator.configure()
        Logger.getRootLogger().level = Level.INFO

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
            val envs = Maps.newHashMap(System.getenv())
            EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, charset)
            val command: Array<String> = if (UIUtil.isWindows) {
                arrayOf("powershell.exe")
            }
            else {
                envs["TERM"] = "xterm-256color"
                val shell = envs["SHELL"] ?: "/bin/bash"
                if (UIUtil.isMac) arrayOf(shell, "--login") else arrayOf(shell)
            }

            val process = PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(envs)
                .setConsole(false)
                .start()

            return LoggingPtyProcessTtyConnector(process, charset)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

    }

    override fun createTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTerminalWidget {
        val widget = JediTerminalWidget(settingsProvider, this)
        widget.addHyperlinkFilter(UrlFilter())
        return widget
    }

    class LoggingPtyProcessTtyConnector(process: PtyProcess, charset: Charset) :
        PtyProcessTtyConnector(process, charset), LoggingTtyConnector {
        private val myDataChunks = Lists.newArrayList<CharArray>()

        @Throws(IOException::class)
        override fun read(buf: CharArray, offset: Int, length: Int): Int {
            val len = super.read(buf, offset, length)
            if (len > 0) {
                val arr = buf.copyOfRange(offset, len)
                myDataChunks.add(arr)
            }
            return len
        }

        override fun getChunks(): List<CharArray> {
            return Lists.newArrayList(myDataChunks)
        }

        @Throws(IOException::class)
        override fun write(string: String) {
            LOG.debug("Writing in OutputStream : " + string)
            super.write(string)
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray) {
            LOG.debug("Writing in OutputStream : " + bytes.contentToString() + " " + String(bytes))
            super.write(bytes)
        }
    }

}