package ai.rever.bossterm.terminal.model

/**
 * Listener for shell integration command state changes (FinalTerm/OSC 133 protocol).
 *
 * OSC 133 sequence types:
 * - A: Prompt started (shell ready for input)
 * - B: Command started (user entered command, execution beginning)
 * - C: Command output ended (command finished producing output)
 * - D;exitcode: Command finished with exit code
 *
 * Shell integration setup is required for this to work.
 * See CLAUDE.md for shell configuration instructions.
 */
interface CommandStateListener {
    /**
     * Called when prompt is displayed (OSC 133;A).
     * Shell is ready for user input.
     */
    fun onPromptStarted() {}

    /**
     * Called when command execution begins (OSC 133;B).
     * User has entered a command and pressed Enter.
     */
    fun onCommandStarted() {}

    /**
     * Called when command output ends (OSC 133;C).
     * The command has finished producing output.
     */
    fun onCommandOutputEnded() {}

    /**
     * Called when command finishes with exit code (OSC 133;D;exitcode).
     *
     * @param exitCode The command's exit code (0 = success, non-zero = error)
     */
    fun onCommandFinished(exitCode: Int) {}
}
