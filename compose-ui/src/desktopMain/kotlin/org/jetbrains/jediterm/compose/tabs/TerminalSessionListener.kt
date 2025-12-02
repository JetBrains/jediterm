package org.jetbrains.jediterm.compose.tabs

import org.jetbrains.jediterm.compose.TerminalSession

/**
 * Listener interface for terminal session lifecycle events.
 *
 * This interface provides callbacks for tracking the lifecycle of terminal sessions
 * in a multi-tab terminal application. Implement this interface to:
 * - Auto-close the application window when all sessions end
 * - Log session creation/closure for analytics
 * - Manage application-level resources based on session count
 * - Implement custom cleanup logic on session closure
 *
 * Example usage:
 * ```kotlin
 * val listener = object : TerminalSessionListener {
 *     override fun onSessionCreated(session: TerminalSession) {
 *         println("New terminal session: ${session.id}")
 *     }
 *
 *     override fun onSessionClosed(session: TerminalSession) {
 *         println("Terminal session closed: ${session.id}")
 *     }
 *
 *     override fun onAllSessionsClosed() {
 *         println("All sessions closed - shutting down")
 *         exitProcess(0)
 *     }
 * }
 *
 * tabController.addSessionListener(listener)
 * ```
 *
 * Thread Safety:
 * - Callbacks are invoked on the main (UI) thread
 * - Listeners are stored in a thread-safe collection
 * - Adding/removing listeners during callback invocation is safe
 */
interface TerminalSessionListener {
    /**
     * Called when a new terminal session is created.
     *
     * This callback is invoked after the session is fully initialized and added
     * to the tab list, but before the PTY process is necessarily connected.
     *
     * @param session The newly created terminal session
     */
    fun onSessionCreated(session: TerminalSession) {}

    /**
     * Called when a terminal session is closed.
     *
     * This callback is invoked after the session is removed from the tab list
     * and its resources have been released (coroutines cancelled, process killed).
     *
     * Note: This is called before [onAllSessionsClosed] if this was the last session.
     *
     * @param session The terminal session that was closed
     */
    fun onSessionClosed(session: TerminalSession) {}

    /**
     * Called when all terminal sessions have been closed.
     *
     * This callback is invoked after the last session is closed and removed.
     * It's equivalent to the legacy `TerminalWidgetListener.allSessionsClosed()`.
     *
     * Common use cases:
     * - Close the application window
     * - Perform final cleanup
     * - Save application state
     *
     * Note: This is called after [onSessionClosed] for the last session.
     */
    fun onAllSessionsClosed() {}
}

/**
 * Adapter class for [TerminalSessionListener] that provides empty default implementations.
 *
 * Extend this class when you only need to override specific callbacks:
 * ```kotlin
 * val listener = object : TerminalSessionListenerAdapter() {
 *     override fun onAllSessionsClosed() {
 *         // Only handle this event
 *     }
 * }
 * ```
 */
open class TerminalSessionListenerAdapter : TerminalSessionListener
