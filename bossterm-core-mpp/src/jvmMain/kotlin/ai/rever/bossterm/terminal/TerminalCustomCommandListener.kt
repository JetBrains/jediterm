package ai.rever.bossterm.terminal

interface TerminalCustomCommandListener {
    fun process(args: MutableList<String?>)
}
