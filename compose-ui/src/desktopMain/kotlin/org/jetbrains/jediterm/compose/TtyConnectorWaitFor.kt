package org.jetbrains.jediterm.compose

import com.jediterm.terminal.TtyConnector
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coroutine-friendly utilities for waiting on TtyConnector operations.
 *
 * Provides suspend functions for:
 * - Waiting for process termination (with optional timeout)
 * - Waiting for specific output patterns
 * - Monitoring connection status
 *
 * All functions are designed to be used with Kotlin coroutines and support
 * structured concurrency with proper cancellation handling.
 */
object TtyConnectorWaitFor {

    /**
     * Result of waiting for a TTY connector.
     */
    sealed class WaitResult {
        /**
         * Process exited normally with the given exit code.
         */
        data class Exited(val exitCode: Int) : WaitResult()

        /**
         * Wait operation timed out.
         */
        data object TimedOut : WaitResult()

        /**
         * Wait was cancelled (e.g., coroutine cancelled).
         */
        data object Cancelled : WaitResult()

        /**
         * An error occurred during waiting.
         */
        data class Error(val exception: Throwable) : WaitResult()
    }

    /**
     * Result of waiting for output patterns.
     */
    sealed class OutputResult {
        /**
         * Pattern was found in the output.
         * @param matchedText The text that matched the pattern
         * @param fullOutput All output read until the match
         */
        data class Found(val matchedText: String, val fullOutput: String) : OutputResult()

        /**
         * Timed out waiting for the pattern.
         * @param outputSoFar Output read before timeout
         */
        data class TimedOut(val outputSoFar: String) : OutputResult()

        /**
         * Process exited before pattern was found.
         * @param exitCode The process exit code
         * @param outputSoFar Output read before exit
         */
        data class ProcessExited(val exitCode: Int, val outputSoFar: String) : OutputResult()

        /**
         * Wait was cancelled.
         * @param outputSoFar Output read before cancellation
         */
        data class Cancelled(val outputSoFar: String) : OutputResult()

        /**
         * An error occurred.
         */
        data class Error(val exception: Throwable, val outputSoFar: String) : OutputResult()
    }

    /**
     * Waits for the TTY connector's process to exit.
     *
     * This is a suspend function that blocks until the process terminates
     * or the coroutine is cancelled.
     *
     * @param connector The TTY connector to wait on
     * @return The exit code of the process
     * @throws CancellationException if the coroutine is cancelled
     */
    suspend fun waitForExit(connector: TtyConnector): Int = withContext(Dispatchers.IO) {
        connector.waitFor()
    }

    /**
     * Waits for the TTY connector's process to exit with a timeout.
     *
     * @param connector The TTY connector to wait on
     * @param timeout Maximum duration to wait
     * @return WaitResult indicating exit code, timeout, or error
     */
    suspend fun waitForExit(
        connector: TtyConnector,
        timeout: Duration
    ): WaitResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeout) {
                val exitCode = connector.waitFor()
                WaitResult.Exited(exitCode)
            }
        } catch (e: TimeoutCancellationException) {
            WaitResult.TimedOut
        } catch (e: CancellationException) {
            WaitResult.Cancelled
        } catch (e: Exception) {
            WaitResult.Error(e)
        }
    }

    /**
     * Waits for the TTY connector's process to exit with a callback.
     *
     * This launches a new coroutine that waits for process exit and invokes
     * the callback with the exit code. The returned Job can be used to
     * cancel the wait operation.
     *
     * @param connector The TTY connector to wait on
     * @param scope The coroutine scope to launch in
     * @param onExit Callback invoked with the exit code when process exits
     * @return Job that can be cancelled to stop waiting
     */
    fun waitForExitAsync(
        connector: TtyConnector,
        scope: CoroutineScope,
        onExit: (Int) -> Unit
    ): Job = scope.launch(Dispatchers.IO) {
        try {
            val exitCode = connector.waitFor()
            withContext(Dispatchers.Main) {
                onExit(exitCode)
            }
        } catch (e: CancellationException) {
            // Cancelled, don't call callback
        } catch (e: InterruptedException) {
            // Interrupted, don't call callback
        }
    }

    /**
     * Waits for a specific pattern to appear in the TTY output.
     *
     * Continuously reads from the connector and checks if the output
     * contains the specified pattern.
     *
     * @param connector The TTY connector to read from
     * @param pattern The regex pattern to match
     * @param timeout Maximum duration to wait for the pattern
     * @param bufferSize Size of the read buffer (default: 1024)
     * @param pollInterval How often to check for new output (default: 10ms)
     * @return OutputResult indicating match found, timeout, or error
     */
    suspend fun waitForOutput(
        connector: TtyConnector,
        pattern: Regex,
        timeout: Duration,
        bufferSize: Int = 1024,
        pollInterval: Duration = 10.milliseconds
    ): OutputResult = withContext(Dispatchers.IO) {
        val outputBuilder = StringBuilder()
        val buffer = CharArray(bufferSize)
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeout.inWholeMilliseconds

        try {
            while (isActive) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    return@withContext OutputResult.TimedOut(outputBuilder.toString())
                }

                // Check if connected
                if (!connector.isConnected) {
                    val exitCode = try {
                        connector.waitFor()
                    } catch (e: Exception) {
                        -1
                    }
                    return@withContext OutputResult.ProcessExited(exitCode, outputBuilder.toString())
                }

                // Try to read
                if (connector.ready()) {
                    val bytesRead = connector.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val newText = String(buffer, 0, bytesRead)
                        outputBuilder.append(newText)

                        // Check for pattern match
                        val match = pattern.find(outputBuilder.toString())
                        if (match != null) {
                            return@withContext OutputResult.Found(match.value, outputBuilder.toString())
                        }
                    }
                } else {
                    // No data ready, wait a bit
                    delay(pollInterval)
                }
            }

            // Coroutine was cancelled
            OutputResult.Cancelled(outputBuilder.toString())
        } catch (e: CancellationException) {
            OutputResult.Cancelled(outputBuilder.toString())
        } catch (e: Exception) {
            OutputResult.Error(e, outputBuilder.toString())
        }
    }

    /**
     * Waits for a specific string to appear in the TTY output.
     *
     * Convenience function that wraps the string in a literal regex pattern.
     *
     * @param connector The TTY connector to read from
     * @param text The literal text to search for
     * @param timeout Maximum duration to wait for the text
     * @param bufferSize Size of the read buffer (default: 1024)
     * @param pollInterval How often to check for new output (default: 10ms)
     * @return OutputResult indicating match found, timeout, or error
     */
    suspend fun waitForText(
        connector: TtyConnector,
        text: String,
        timeout: Duration,
        bufferSize: Int = 1024,
        pollInterval: Duration = 10.milliseconds
    ): OutputResult = waitForOutput(
        connector = connector,
        pattern = Regex.escape(text).toRegex(),
        timeout = timeout,
        bufferSize = bufferSize,
        pollInterval = pollInterval
    )

    /**
     * Waits until the TTY connector becomes disconnected.
     *
     * Useful for detecting when a remote connection has been closed
     * without necessarily waiting for the local process to exit.
     *
     * @param connector The TTY connector to monitor
     * @param timeout Maximum duration to wait
     * @param pollInterval How often to check connection status (default: 100ms)
     * @return true if disconnected, false if timed out
     */
    suspend fun waitForDisconnect(
        connector: TtyConnector,
        timeout: Duration,
        pollInterval: Duration = 100.milliseconds
    ): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeout.inWholeMilliseconds

        while (isActive) {
            if (!connector.isConnected) {
                return@withContext true
            }

            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                return@withContext false
            }

            delay(pollInterval)
        }

        false // Cancelled
    }
}
