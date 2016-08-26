package com.jediterm.app

import com.google.common.base.Predicate
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.openapi.util.Pair
import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.LoggingTtyConnector
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.*
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider
import com.jediterm.terminal.ui.settings.SettingsProvider
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider
import com.pty4j.PtyProcess
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

/**
 * Created by traff on 22/08/16.
 */


object JediTermMain {
    @JvmStatic
    fun main(arg: Array<String>) {
        BasicConfigurator.configure()
        Logger.getRootLogger().level = Level.INFO
        JediTerm()
    }
}

class JediTerm : AbstractTerminalFrame() {
    override fun createTabbedTerminalWidget(): TabbedTerminalWidget {
        return object : JediTabbedTerminalWidget(DefaultTabbedSettingsProvider(), object : Predicate<Pair<TerminalWidget, String>> {
            override fun apply(pair: Pair<TerminalWidget, String>?): Boolean {
                openSession(pair?.first)
                return true
            }
        }, object : com.intellij.openapi.Disposable {
            override fun dispose() {
                //TODO
            }

        }) {
            override fun createInnerTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTermWidget {
                return createTerminalWidget(settingsProvider)
            }
        }
    }

    override fun createTtyConnector(): TtyConnector {
        try {
            val envs = Maps.newHashMap(System.getenv())
            val command: Array<String>

            if (UIUtil.isWindows) {
                command = arrayOf("cmd.exe")
            } else {
                command = arrayOf("/bin/bash", "--login")
                envs.put("TERM", "xterm")
            }

            val process = PtyProcess.exec(command, envs, null)

            return LoggingPtyProcessTtyConnector(process, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

    }

    override fun createTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTermWidget {
        return object : JediTermWidget(settingsProvider) {
            override fun createTerminalPanel(settingsProvider: SettingsProvider, styleState: StyleState, terminalTextBuffer: TerminalTextBuffer): TerminalPanel {
                return JediTerminalPanel(settingsProvider, styleState, terminalTextBuffer)
            }
        }
    }

    class LoggingPtyProcessTtyConnector(process: PtyProcess, charset: Charset) : PtyProcessTtyConnector(process, charset), LoggingTtyConnector {
        private val myDataChunks = Lists.newArrayList<CharArray>()

        @Throws(IOException::class)
        override fun read(buf: CharArray, offset: Int, length: Int): Int {
            val len = super.read(buf, offset, length)
            if (len > 0) {
                val arr = Arrays.copyOfRange(buf, offset, len)
                myDataChunks.add(arr)
            }
            return len
        }

        override fun getChunks(): List<CharArray> {
            return Lists.newArrayList(myDataChunks)
        }

        @Throws(IOException::class)
        override fun write(string: String) {
            AbstractTerminalFrame.LOG.debug("Writing in OutputStream : " + string)
            super.write(string)
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray) {
            AbstractTerminalFrame.LOG.debug("Writing in OutputStream : " + Arrays.toString(bytes) + " " + String(bytes))
            super.write(bytes)
        }
    }

}