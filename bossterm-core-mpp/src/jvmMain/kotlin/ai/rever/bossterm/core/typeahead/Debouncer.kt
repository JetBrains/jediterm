package ai.rever.bossterm.core.typeahead

interface Debouncer {
    fun call()

    fun terminateCall()
}