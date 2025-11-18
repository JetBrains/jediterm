package com.jediterm.terminal.model.hyperlinks

class LinkInfo(private val myNavigateCallback: Runnable) {
    fun navigate() {
        myNavigateCallback.run()
    }
}
