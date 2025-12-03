package ai.rever.bossterm.compose.typeahead

import ai.rever.bossterm.core.typeahead.Debouncer
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Coroutine-based implementation of the Debouncer interface for type-ahead prediction clearing.
 *
 * Replaces Swing's BossTermDebouncerImpl which uses ScheduledExecutorService.
 * Thread-safe and cancellation-aware.
 *
 * @param action The action to execute after the debounce delay
 * @param delayNanos Delay in nanoseconds before executing the action
 * @param scope CoroutineScope for launching debounce jobs (should be tab's coroutine scope)
 */
class CoroutineDebouncer(
    private val action: () -> Unit,
    private val delayNanos: Long,
    private val scope: CoroutineScope
) : Debouncer {

    private val currentJob = AtomicReference<Job?>(null)

    /**
     * Start or restart the debounce timer.
     * Cancels any pending job and starts a new delayed job.
     */
    override fun call() {
        // Cancel any pending job
        currentJob.get()?.cancel()

        // Start new debounced job
        val job = scope.launch {
            delay(delayNanos / 1_000_000) // Convert nanos to millis
            action()
        }
        currentJob.set(job)
    }

    /**
     * Cancel any pending debounce job.
     */
    override fun terminateCall() {
        currentJob.getAndSet(null)?.cancel()
    }
}
