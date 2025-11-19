package com.jediterm.terminal.model

import com.jediterm.core.typeahead.Debouncer
import com.jediterm.terminal.TerminalExecutorServiceManager
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

class JediTermDebouncerImpl(
    private val myRunnable: Runnable, private val myDelay: Long,
    executorServiceManager: TerminalExecutorServiceManager
) : Debouncer {
    private val myLock = Any()

    private val myScheduler: ScheduledExecutorService

    private var myTimerTask: TimerTask? = null

    init {
        myScheduler = executorServiceManager.singleThreadScheduledExecutor
    }

    override fun call() {
        synchronized(myLock) {
            myTimerTask?.cancel()
            myTimerTask = TimerTask()
            myScheduler.schedule(myTimerTask, myDelay, TimeUnit.NANOSECONDS)
        }
    }

    override fun terminateCall() {
        synchronized(myLock) {
            myTimerTask?.cancel()
            myTimerTask = null
        }
    }

    private inner class TimerTask : Runnable {
        private val myDueTime: Long

        @Volatile
        private var myIsActive = true

        init {
            myDueTime = System.nanoTime() + myDelay
        }

        fun cancel() {
            myIsActive = false
        }

        override fun run() {
            if (!myIsActive) {
                return
            }

            val remaining = myDueTime - System.nanoTime()
            if (remaining > 0) { // Re-schedule task
                myScheduler.schedule(this, remaining, TimeUnit.NANOSECONDS)
            } else { // Mark as terminated and invoke callback
                myIsActive = false
                myRunnable.run()
            }
        }
    }
}
