package ai.rever.bossterm.terminal

/**
 * Interface for prompting user input during terminal connection setup.
 *
 * Used for interactive authentication flows like:
 * - SSH password authentication
 * - Two-factor authentication (2FA/MFA)
 * - Certificate passphrases
 */
interface Questioner {
    fun questionVisible(question: String?, defValue: String?): String?

    fun questionHidden(string: String?): String?

    fun showMessage(message: String?)
}
