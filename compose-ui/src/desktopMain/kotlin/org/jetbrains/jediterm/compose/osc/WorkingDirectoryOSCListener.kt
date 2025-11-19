package org.jetbrains.jediterm.compose.osc

import androidx.compose.runtime.MutableState
import com.jediterm.terminal.TerminalCustomCommandListener
import java.net.URI
import java.net.URISyntaxException

/**
 * A TerminalCustomCommandListener that listens for OSC 7 (Operating System Command)
 * sequences to update the current working directory.
 *
 * OSC 7 Format: ESC]7;file://hostname/path\a (or \007)
 * Example: ESC]7;file://macbook-pro.local/Users/username/Documents\a
 *
 * This enables:
 * - New tabs inheriting the working directory from the active tab
 * - Dynamic tab titles showing current directory
 * - Shell integration for directory awareness
 *
 * Implementation Strategy:
 * - Implements TerminalCustomCommandListener interface
 * - Processes OSC sequences parsed by JediEmulator
 * - OSC args format: ["7", "file://hostname/path"] for OSC 7
 * - Parses file:// URIs to extract the directory path
 * - Updates the Compose MutableState for reactive UI updates
 *
 * Thread Safety:
 * - Safe to call from any thread (MutableState is thread-safe)
 * - Called from Dispatchers.Default thread (emulator processing)
 * - State updates automatically trigger Compose recomposition
 *
 * Setup Instructions:
 * To enable OSC 7 tracking in your shell, add to ~/.zshrc or ~/.bashrc:
 * ```bash
 * # Bash:
 * PROMPT_COMMAND='echo -ne "\033]7;file://${HOSTNAME}${PWD}\007"'
 *
 * # Zsh:
 * precmd() { echo -ne "\033]7;file://${HOST}${PWD}\007" }
 * ```
 *
 * @param workingDirectoryState The Compose mutable state to update with the new directory path
 */
class WorkingDirectoryOSCListener(
    private val workingDirectoryState: MutableState<String?>
) : TerminalCustomCommandListener {

    override fun process(args: MutableList<String?>) {
        // OSC sequences are passed as args: [commandCode, arg1, arg2, ...]
        // OSC 7 format: ["7", "file://hostname/path"]
        if (args.size >= 2 && args[0] == "7") {
            val uriString = args[1] ?: return

            try {
                // Parse the file URI: "file://hostname/path/to/dir"
                val uri = URI(uriString)
                if (uri.scheme == "file" && uri.path != null) {
                    // Update the state with the parsed path
                    // This is safe to call from any thread as MutableState is thread-safe
                    workingDirectoryState.value = uri.path
                    println("INFO: Updated working directory via OSC 7: ${uri.path}")
                }
            } catch (e: URISyntaxException) {
                // Log malformed URI but don't crash - some shells may send invalid formats
                System.err.println("WARN: Received malformed OSC 7 URI: $uriString")
            } catch (e: IllegalArgumentException) {
                // Handle other URI parsing errors
                System.err.println("WARN: Failed to parse OSC 7 URI: $uriString")
            }
        }
    }
}
