package com.jediterm.terminal

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

interface TerminalExecutorServiceManager {
    /**
     * A single threaded executor service for fast tasks.
     */
    val singleThreadScheduledExecutor: ScheduledExecutorService

    /**
     * A general purpose executor service, e.g. to run emulator.
     */
    val unboundedExecutorService: ExecutorService

    fun shutdownWhenAllExecuted()
}
