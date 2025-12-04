package ai.rever.bossterm.terminal.model.hyperlinks

class LinkInfo(private val myNavigateCallback: Runnable) {
    fun navigate() {
        myNavigateCallback.run()
    }
}
