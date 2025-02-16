package com.jediterm.terminal.model.hyperlinks

import java.util.concurrent.CompletableFuture

interface AsyncHyperlinkFilter {
    /**
     * Finds links inside the given line.
     *
     * @param lineInfo The information about a line of text to apply the filter to.
     *
     * @return A CompletableFuture resolved asynchronously when a link is found.
     */
    fun apply(lineInfo: LineInfo): CompletableFuture<LinkResult?>?

    interface LineInfo {
        /**
         * @return line string; if null, finding links should be skipped
         */
        val line: String?
    }
}
