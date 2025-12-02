package org.jetbrains.jediterm.compose

import com.jediterm.terminal.Questioner
import com.jediterm.terminal.TtyConnector

/**
 * Factory interface for creating TtyConnector instances with optional pre-connection prompts.
 *
 * This enables interactive setup flows like:
 * - SSH password authentication
 * - Two-factor authentication (2FA/MFA)
 * - Custom host/port selection
 * - Certificate passphrases
 *
 * Usage:
 * ```kotlin
 * val sshConnectorFactory = object : TtyConnectorFactory {
 *     override suspend fun createConnector(questioner: Questioner): TtyConnector? {
 *         val host = questioner.questionVisible("Enter SSH host:", "localhost")
 *         val password = questioner.questionHidden("Enter password:")
 *         if (password == null) return null  // User cancelled
 *
 *         questioner.showMessage("Connecting to $host...")
 *         return SshTtyConnector(host, password)
 *     }
 * }
 *
 * tabController.createTabWithConnector(sshConnectorFactory)
 * ```
 */
interface TtyConnectorFactory {
    /**
     * Create a TtyConnector, optionally prompting user for input.
     *
     * The questioner parameter provides methods to:
     * - `questionVisible()`: Ask for visible input (hostname, username)
     * - `questionHidden()`: Ask for hidden input (passwords, secrets)
     * - `showMessage()`: Display informational messages
     *
     * @param questioner Interface for prompting user input
     * @return The created TtyConnector, or null if user cancelled
     */
    suspend fun createConnector(questioner: Questioner): TtyConnector?
}

/**
 * Simple result wrapper for connector factory operations.
 */
sealed class ConnectorResult {
    /**
     * Successfully created a connector.
     */
    data class Success(val connector: TtyConnector) : ConnectorResult()

    /**
     * User cancelled the connection setup.
     */
    object Cancelled : ConnectorResult()

    /**
     * Connection setup failed with an error.
     */
    data class Error(val message: String, val cause: Throwable? = null) : ConnectorResult()
}
