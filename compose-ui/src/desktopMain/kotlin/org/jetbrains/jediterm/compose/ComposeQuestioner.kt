package org.jetbrains.jediterm.compose

import com.jediterm.terminal.Questioner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Compose-compatible implementation of the Questioner interface.
 *
 * Bridges the synchronous Questioner API with Compose's callback-based approach
 * using Kotlin coroutines. When input is required, sets ConnectionState.RequiresInput
 * and suspends until user provides input.
 *
 * Usage:
 * ```kotlin
 * val questioner = ComposeQuestioner { newState ->
 *     connectionState = newState
 * }
 *
 * // In coroutine scope:
 * val password = questioner.questionHidden("Enter password:")
 * ```
 *
 * @param onStateChange Callback to update connection state for UI rendering
 */
@Suppress("DEPRECATION")
class ComposeQuestioner(
    private val onStateChange: (ConnectionState) -> Unit
) : Questioner {

    /**
     * Request visible (non-masked) input from user.
     *
     * @param question The prompt to display
     * @param defValue Optional default value to pre-populate
     * @return User input, or null if cancelled
     */
    override fun questionVisible(question: String?, defValue: String?): String? {
        if (question == null) return null

        return kotlinx.coroutines.runBlocking {
            suspendCancellableCoroutine { continuation ->
                onStateChange(
                    ConnectionState.RequiresInput(
                        prompt = question,
                        isPassword = false,
                        defaultValue = defValue,
                        onSubmit = { input ->
                            continuation.resume(input)
                        },
                        onCancel = {
                            continuation.resume(null)
                        }
                    )
                )
            }
        }
    }

    /**
     * Request hidden (password) input from user.
     *
     * @param string The prompt to display
     * @return User input, or null if cancelled
     */
    override fun questionHidden(string: String?): String? {
        if (string == null) return null

        return kotlinx.coroutines.runBlocking {
            suspendCancellableCoroutine { continuation ->
                onStateChange(
                    ConnectionState.RequiresInput(
                        prompt = string,
                        isPassword = true,
                        defaultValue = null,
                        onSubmit = { input ->
                            continuation.resume(input)
                        },
                        onCancel = {
                            continuation.resume(null)
                        }
                    )
                )
            }
        }
    }

    /**
     * Display an informational message to the user.
     *
     * @param message The message to display
     */
    override fun showMessage(message: String?) {
        if (message != null) {
            onStateChange(ConnectionState.ShowMessage(message))
        }
    }

    /**
     * Request user to select from a list of options.
     *
     * @param prompt The prompt to display
     * @param options List of options (value to label pairs)
     * @param defaultIndex Index of default selection (0-based)
     * @return Selected value, or null if cancelled
     */
    fun questionSelection(
        prompt: String,
        options: List<ConnectionState.SelectOption>,
        defaultIndex: Int = 0
    ): String? {
        return kotlinx.coroutines.runBlocking {
            suspendCancellableCoroutine { continuation ->
                onStateChange(
                    ConnectionState.RequiresSelection(
                        prompt = prompt,
                        options = options,
                        defaultIndex = defaultIndex,
                        onSelect = { selected ->
                            continuation.resume(selected)
                        },
                        onCancel = {
                            continuation.resume(null)
                        }
                    )
                )
            }
        }
    }
}
