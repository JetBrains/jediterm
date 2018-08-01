package com.jediterm.app

import com.google.common.base.Predicate
import com.google.common.collect.ForwardingMap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Pair
import com.intellij.util.EncodingEnvironmentUtil
import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.pty.PtyTabChangeListener
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
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.awt.KeyboardFocusManager
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

//        initLoggingTracing()

        JediTerm()
    }
}

fun initLoggingTracing() {
    val mrfoField = KeyboardFocusManager::class.java!!.getDeclaredField("mostRecentFocusOwners")
    mrfoField.setAccessible(true)

    val delegate = mrfoField.get(null) as Map<Any, Any>

    val mrfo = object : ForwardingMap<Any, Any>() {
        override fun put(key: Any?, value: Any?): Any? {
            Throwable().printStackTrace()
            return super.put(key, value)
        }

        override fun delegate(): Map<Any, Any> {
            return delegate
        }
    }
    mrfoField.set(null, mrfo)
}

class JediTerm : AbstractTerminalFrame(), Disposable {
    override fun dispose() {
        // TODO
    }

    override fun createTabbedTerminalWidget(): TabbedTerminalWidget {
        val widget = object : JediTabbedTerminalWidget(DefaultTabbedSettingsProvider(), object : Predicate<Pair<TerminalWidget, String>> {
            override fun apply(pair: Pair<TerminalWidget, String>?): Boolean {
                openSession(pair?.first)
                return true
            }
        }, this) {
            override fun createInnerTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTermWidget {
                return createTerminalWidget(settingsProvider)
            }
        }
        widget.setTabChangeListener(PtyTabChangeListener())
        return widget
    }

    override fun createTtyConnector(): TtyConnector {
        try {

            val charset = Charset.forName("UTF-8")

            val envs = Maps.newHashMap(System.getenv())

            EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(envs, charset)

            val command: Array<String>

            if (UIUtil.isWindows) {
                command = arrayOf("cmd.exe")
            } else {
                command = arrayOf("/bin/bash", "--login")
                envs.put("TERM", "xterm")
            }

            val process = PtyProcess.exec(command, envs, null)


            return LoggingPtyProcessTtyConnector(process, charset)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

    }

    override fun createTerminalWidget(settingsProvider: TabbedSettingsProvider): JediTermWidget {
        val widget = JediTerminalWidget(settingsProvider, this)
        widget.addHyperlinkFilter(UrlFilter())
        return widget
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