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
}
