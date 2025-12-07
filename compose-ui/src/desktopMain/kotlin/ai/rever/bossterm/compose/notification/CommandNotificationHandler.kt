package ai.rever.bossterm.compose.notification

import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.terminal.model.CommandStateListener
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Handles command completion notifications based on shell integration (OSC 133).
 *
 * Shows a system notification when:
 * 1. A command completes (OSC 133;D)
 * 2. The window is not focused
 * 3. The command ran for at least [TerminalSettings.notifyMinDurationSeconds]
 *
 * Requires shell integration to be configured in the user's shell.
 */
class CommandNotificationHandler(
    private val settings: TerminalSettings,
    private val isWindowFocused: () -> Boolean,
    private val tabTitle: () -> String = { "BossTerm" }
) : CommandStateListener {

    private val LOG = LoggerFactory.getLogger(CommandNotificationHandler::class.java)

    // Track command state
    private val commandStartTime = AtomicLong(0)
    private val isCommandRunning = AtomicBoolean(false)
    private val lastCommand = AtomicReference<String?>(null)

    override fun onPromptStarted() {
        LOG.debug("Prompt started - shell ready for input")
        // Reset command state when new prompt appears
        isCommandRunning.set(false)
        commandStartTime.set(0)
    }

    override fun onCommandStarted() {
        LOG.debug("Command started")
        // Record when command execution began
        commandStartTime.set(System.currentTimeMillis())
        isCommandRunning.set(true)
    }

    override fun onCommandOutputEnded() {
        LOG.debug("Command output ended")
        // Command output finished, but we wait for D to get exit code
    }

    override fun onCommandFinished(exitCode: Int) {
        val startTime = commandStartTime.get()

        // Check for incomplete shell integration (D without B)
        if (startTime == 0L) {
            LOG.warn("Command finished without corresponding start event - shell integration may be incomplete")
            isCommandRunning.set(false)
            return
        }

        val duration = (System.currentTimeMillis() - startTime) / 1000

        LOG.debug("Command finished: exitCode=$exitCode, duration=${duration}s, focused=${isWindowFocused()}")

        // Reset state
        isCommandRunning.set(false)
        commandStartTime.set(0)

        // Check if notification should be shown
        if (!settings.notifyOnCommandComplete) {
            LOG.debug("Notifications disabled in settings")
            return
        }

        if (isWindowFocused()) {
            LOG.debug("Window is focused, skipping notification")
            return
        }

        if (duration < settings.notifyMinDurationSeconds) {
            LOG.debug("Command duration (${duration}s) below threshold (${settings.notifyMinDurationSeconds}s)")
            return
        }

        // Show notification
        val title = tabTitle()
        val message = buildNotificationMessage(exitCode, duration)
        val subtitle = if (exitCode != 0) "Exit code: $exitCode" else null

        NotificationService.showNotification(
            title = title,
            message = message,
            subtitle = if (settings.notifyShowExitCode) subtitle else null,
            withSound = settings.notifyWithSound
        )
    }

    private fun buildNotificationMessage(exitCode: Int, durationSeconds: Long): String {
        val durationStr = formatDuration(durationSeconds)
        val status = if (exitCode == 0) "completed" else "failed"

        return if (settings.notifyShowExitCode && exitCode != 0) {
            "Command $status after $durationStr (exit $exitCode)"
        } else {
            "Command $status after $durationStr"
        }
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
