package com.jediterm.terminal

@Deprecated("Collect extra information when creating {@link TtyConnector}.")
interface Questioner {
    fun questionVisible(question: String?, defValue: String?): String?

    fun questionHidden(string: String?): String?

    fun showMessage(message: String?)
}
