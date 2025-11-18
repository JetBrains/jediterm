package com.jediterm.terminal

import com.jediterm.core.input.KeyEvent
import com.jediterm.core.typeahead.TerminalTypeAheadManager
import com.jediterm.core.typeahead.TerminalTypeAheadManager.TypeAheadEvent.Companion.fromByteArray
import com.jediterm.core.typeahead.TerminalTypeAheadManager.TypeAheadEvent.Companion.fromString
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.emulator.Emulator
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.model.JediTerminal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.concurrent.Volatile

/**
 * Runs terminal emulator. Manages threads to send response.
 *
 * @author traff
 */
class TerminalStarter(
    private val myTerminal: JediTerminal,
    val ttyConnector: TtyConnector,
    dataStream: TerminalDataStream,
    typeAheadManager: TerminalTypeAheadManager,
    executorServiceManager: TerminalExecutorServiceManager
) : TerminalOutputStream {
    private val myEmulator: Emulator

    private val myTypeAheadManager: TerminalTypeAheadManager
    private val mySingleThreadScheduledExecutor: ScheduledExecutorService

    @Volatile
    private var myStopped = false

    @Volatile
    private var myScheduledTtyConnectorResizeFuture: ScheduledFuture<*>? = null

    @Volatile
    var isLastSentByteEscape: Boolean = false
        private set

    init {
        myTerminal.setTerminalOutput(this)
        myEmulator = createEmulator(dataStream, myTerminal)
        myTypeAheadManager = typeAheadManager
        mySingleThreadScheduledExecutor = executorServiceManager.singleThreadScheduledExecutor
    }

    protected fun createEmulator(dataStream: TerminalDataStream?, terminal: Terminal?): JediEmulator {
        return JediEmulator(dataStream ?: throw IllegalArgumentException("dataStream cannot be null"), terminal)
    }

    private fun execute(runnable: Runnable) {
        if (!mySingleThreadScheduledExecutor.isShutdown()) {
            mySingleThreadScheduledExecutor.execute(runnable)
        }
    }

    val terminal: Terminal
        get() = myTerminal

    fun start() {
        runUnderThreadName("TerminalEmulator-" + ttyConnector.name) { this.doStartEmulator() }
    }

    private fun doStartEmulator() {
        try {
            while ((!Thread.currentThread().isInterrupted() && !myStopped) && myEmulator.hasNext()) {
                myEmulator.next()
            }
        } catch (e: InterruptedIOException) {
            LOG.debug("Terminal I/0 has been interrupted")
        } catch (e: Exception) {
            if (ttyConnector.isConnected) {
                throw RuntimeException("Uncaught exception in terminal emulator thread", e)
            }
        } finally {
            myTerminal.disconnected()
        }
    }

    fun requestEmulatorStop() {
        myStopped = true
    }

    @Deprecated("use {@link JediTerminal#getCodeForKey(int, int)} instead")
    fun getCode(key: Int, modifiers: Int): ByteArray? {
        return myTerminal.getCodeForKey(key, modifiers)
    }

    fun postResize(termSize: TermSize, origin: RequestOrigin) {
        execute(Runnable {
            myTerminal.resize(termSize, origin)
            scheduleTtyConnectorResize(termSize)
        })
    }

    /**
     * Schedule sending a resize to a process. When using primary screen buffer + scroll-back buffer,
     * resize shouldn't be sent to the process immediately to reduce probability of concurrent resizes.
     * Because sending resize to the process may lead to full screen buffer update,
     * e.g. it happens with ConPTY. The update should be applied to the screen buffer having
     * the exact same size as it had when resize was posted. Otherwise, some lines from the screen buffer
     * could escape to the scroll-back buffer and stuck there.
     */
    private fun scheduleTtyConnectorResize(termSize: TermSize) {
        val scheduledTtyConnectorResizeFuture = myScheduledTtyConnectorResizeFuture
        if (scheduledTtyConnectorResizeFuture != null) {
            scheduledTtyConnectorResizeFuture.cancel(false)
        }
        val mergeDelay =
            (if (myTerminal.terminalTextBuffer.isUsingAlternateBuffer) 100 /* not necessary, but let's avoid unnecessary work in case of a series of resize events */ else 500 /* hopefully, the process will send the screen buffer update within the delay */).toLong()
        myScheduledTtyConnectorResizeFuture = mySingleThreadScheduledExecutor.schedule(Runnable {
            ttyConnector.resize(termSize)
        }, mergeDelay, TimeUnit.MILLISECONDS)
    }

    override fun sendBytes(bytes: ByteArray, userInput: Boolean) {
        val length = bytes.size
        if (length > 0) {
            this.isLastSentByteEscape = bytes[length - 1].toInt() == KeyEvent.VK_ESCAPE
        }
        execute(Runnable {
            try {
                if (userInput) {
                    fromByteArray(bytes).filterNotNull().forEach(Consumer { keyEvent: TerminalTypeAheadManager.TypeAheadEvent ->
                        myTypeAheadManager.onKeyEvent(keyEvent)
                    })
                }
                ttyConnector.write(bytes)
            } catch (e: IOException) {
                logWriteError(e)
            }
        })
    }

    override fun sendString(string: String, userInput: Boolean) {
        val length = string.length
        if (length > 0) {
            this.isLastSentByteEscape = string.get(length - 1).code == KeyEvent.VK_ESCAPE
        }
        execute(Runnable {
            try {
                if (userInput) {
                    fromString(string).filterNotNull().forEach(Consumer { keyEvent: TerminalTypeAheadManager.TypeAheadEvent ->
                        myTypeAheadManager.onKeyEvent(keyEvent)
                    })
                }

                ttyConnector.write(string)
            } catch (e: IOException) {
                logWriteError(e)
            }
        })
    }

    private fun logWriteError(e: IOException) {
        LOG.info(
            "Cannot write to TtyConnector " + ttyConnector.javaClass.getName() + ", connected: " + ttyConnector.isConnected,
            e
        )
    }

    fun close() {
        execute(Runnable {
            try {
                ttyConnector.close()
            } catch (e: Exception) {
                LOG.error("Error closing terminal", e)
            }
        })
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TerminalStarter::class.java)

        private fun runUnderThreadName(threadName: String, runnable: Runnable) {
            val currentThread = Thread.currentThread()
            val oldThreadName = currentThread.getName()
            if (threadName == oldThreadName) {
                runnable.run()
            } else {
                currentThread.setName(threadName)
                try {
                    runnable.run()
                } finally {
                    currentThread.setName(oldThreadName)
                }
            }
        }

        @Suppress("unused")
        @Deprecated(
            """use {@link Terminal#resize(TermSize, RequestOrigin)} and {@link TtyConnector#resize(TermSize)} independently.
    Resizes terminal and tty connector, should be called on a pooled thread."""
        )
        fun resize(
            emulator: Emulator,
            terminal: Terminal,
            ttyConnector: TtyConnector,
            newTermSize: TermSize,
            origin: RequestOrigin,
            taskScheduler: BiConsumer<Long?, Runnable?>
        ) {
            terminal.resize(newTermSize, origin)
            ttyConnector.resize(newTermSize)
        }
    }
}
