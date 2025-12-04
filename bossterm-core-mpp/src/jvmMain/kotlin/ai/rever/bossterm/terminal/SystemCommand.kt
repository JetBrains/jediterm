package ai.rever.bossterm.terminal.emulator

interface SystemCommand {
    fun process(code: Int, value: String)
}
