package org.jetbrains.jediterm.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Main Terminal composable function.
 * This is the public API entry point for embedding a terminal in Compose UI.
 *
 * Example usage:
 * ```
 * @Composable
 * fun MyApp() {
 *     val terminalState = rememberTerminalState()
 *     val controller = rememberTerminalController(terminalState)
 *
 *     LaunchedEffect(Unit) {
 *         controller.connect("/bin/bash", listOf("--login"))
 *     }
 *
 *     Terminal(
 *         state = terminalState,
 *         controller = controller,
 *         modifier = Modifier.fillMaxSize()
 *     )
 * }
 * ```
 *
 * @param state The terminal state holder
 * @param controller The terminal controller for operations
 * @param modifier Modifier to be applied to the terminal
 * @param onTitleChange Callback when terminal title changes
 * @param onBell Callback when bell (beep) is triggered
 */
@Composable
expect fun Terminal(
    state: TerminalState,
    controller: TerminalController,
    modifier: Modifier = Modifier,
    onTitleChange: ((String?) -> Unit)? = null,
    onBell: (() -> Unit)? = null
)

/**
 * Terminal composable with simplified API - creates state and controller internally
 *
 * @param command Command to execute
 * @param arguments Command arguments
 * @param environment Environment variables
 * @param config Terminal configuration
 * @param modifier Modifier to be applied to the terminal
 * @param onTitleChange Callback when terminal title changes
 * @param onBell Callback when bell (beep) is triggered
 * @param onProcessExit Callback when process exits
 */
@Composable
expect fun Terminal(
    command: String,
    arguments: List<String> = emptyList(),
    environment: Map<String, String> = emptyMap(),
    config: TerminalState.TerminalConfig = TerminalState.TerminalConfig(),
    modifier: Modifier = Modifier,
    onTitleChange: ((String?) -> Unit)? = null,
    onBell: (() -> Unit)? = null,
    onProcessExit: ((Int) -> Unit)? = null
)
