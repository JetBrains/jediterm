package com.jediterm.terminal.model.hyperlinks

import java.util.concurrent.CompletableFuture

interface AsyncHyperlinkFilter {
    /**
     * Finds links inside the given line.
     *
     * @param lineInfo The information about a line of text to apply the filter to.
     *
     * @return A [CompletableFuture] instance resolved asynchronously when a link is found.
     *         If no link is found, it can be completed with `null` or exception.
     *         Also, it can never be completed.
     */
    fun apply(lineInfo: LineInfo): CompletableFuture<LinkResult?>

    interface LineInfo {
        /**
         * @return line string; if null, finding links should be skipped
         */
        val line: String?
    }
}
