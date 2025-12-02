package org.jetbrains.jediterm.compose

import org.jetbrains.jediterm.compose.PlatformServices

/**
 * Represents the connection state of the terminal during PTY initialization.
 *
 * States:
 * - [Initializing]: Initial state before connection attempt
 * - [Connecting]: Active connection attempt in progress
 * - [Connected]: Successfully connected with processHandle
 * - [Error]: Connection failed with error message
 */
sealed class ConnectionState {
    /**
     * Initial state before any connection attempt.
     */
    object Initializing : ConnectionState()

    /**
     * Connection attempt in progress (spawning PTY).
     */
    object Connecting : ConnectionState()

    /**
     * Successfully connected to shell.
     * @param handle The process handle for the connected PTY
     */
    data class Connected(
        val handle: PlatformServices.ProcessService.ProcessHandle
    ) : ConnectionState()

    /**
     * Connection failed with error.
     * @param message Human-readable error message
     * @param cause Optional underlying exception
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionState() {
        override fun toString(): String = "ConnectionState.Error(message='$message', cause=${cause?.javaClass?.simpleName})"
    }

    /**
     * Connection requires user input before proceeding.
     * Used for interactive authentication (passwords, 2FA codes) or custom TtyConnector setup.
     *
     * @param prompt The question or prompt to display to the user
     * @param isPassword If true, input should be masked (not displayed)
     * @param defaultValue Optional default value to pre-populate
     * @param onSubmit Callback invoked when user submits input
     * @param onCancel Optional callback invoked when user cancels input
     */
    data class RequiresInput(
        val prompt: String,
        val isPassword: Boolean = false,
        val defaultValue: String? = null,
        val onSubmit: (String) -> Unit,
        val onCancel: (() -> Unit)? = null
    ) : ConnectionState()

    /**
     * Displaying an informational message during pre-connection phase.
     * @param message The message to display
     */
    data class ShowMessage(
        val message: String
    ) : ConnectionState()

    /**
     * Connection requires user to select from a list of options.
     * Used for connection type selection, database type, etc.
     *
     * @param prompt The question or prompt to display
     * @param options List of selectable options (value to display label)
     * @param defaultIndex Index of default selected option (0-based)
     * @param onSelect Callback invoked when user selects an option (receives the value)
     * @param onCancel Optional callback invoked when user cancels
     */
    data class RequiresSelection(
        val prompt: String,
        val options: List<SelectOption>,
        val defaultIndex: Int = 0,
        val onSelect: (String) -> Unit,
        val onCancel: (() -> Unit)? = null
    ) : ConnectionState()

    /**
     * Represents a selectable option in RequiresSelection.
     * @param value The value returned when selected
     * @param label The display label shown to user
     */
    data class SelectOption(
        val value: String,
        val label: String
    )
}
