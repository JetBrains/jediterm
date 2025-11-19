package com.jediterm.terminal.model.hyperlinks

/**
 * @author traff
 */
class LinkResultItem(val startOffset: Int, val endOffset: Int, linkInfo: LinkInfo) {
    val linkInfo: LinkInfo?

    init {
        this.linkInfo = linkInfo
    }
}
