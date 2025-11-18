package com.jediterm.terminal.model.hyperlinks

/**
 * @author traff
 */
interface HyperlinkFilter {
    fun apply(line: String?): LinkResult?
}
