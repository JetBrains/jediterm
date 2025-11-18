package com.jediterm.terminal.model.hyperlinks

import com.jediterm.terminal.HyperlinkStyle
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.*
import com.jediterm.terminal.model.TerminalLineUtil.getModificationCount
import com.jediterm.terminal.model.TerminalLineUtil.incModificationCount
import com.jediterm.terminal.util.CharUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.stream.Collectors
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * @author traff
 */
class TextProcessing(
    private val myHyperlinkColor: TextStyle,
    private val myHighlightMode: HyperlinkStyle.HighlightMode
) {
    private val myHyperlinkFilters: MutableList<AsyncHyperlinkFilter> = CopyOnWriteArrayList<AsyncHyperlinkFilter>()
    private var myTerminalTextBuffer: TerminalTextBuffer? = null
    private val myHyperlinkListeners: MutableList<TerminalHyperlinkListener> =
        CopyOnWriteArrayList<TerminalHyperlinkListener>()

    fun setTerminalTextBuffer(terminalTextBuffer: TerminalTextBuffer) {
        myTerminalTextBuffer = terminalTextBuffer
        terminalTextBuffer.addChangesListener(object : TextBufferChangesListener {
            override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
                for (line in lines) {
                    incModificationCount(line)
                }
            }
        })
    }

    fun processHyperlinks(linesStorage: LinesStorage, updatedLine: TerminalLine) {
        if (!myHyperlinkFilters.isEmpty()) {
            doProcessHyperlinks(linesStorage, updatedLine)
        }
    }

    private fun doProcessHyperlinks(linesStorage: LinesStorage, updatedLine: TerminalLine) {
        val lineInfo = buildLineInfo(linesStorage, updatedLine)
        if (lineInfo != null) {
            doProcessHyperlinks(linesStorage, lineInfo, 1)
        }
    }

    private fun doProcessHyperlinks(linesStorage: LinesStorage, lineInfo: LineInfoImpl, attemptNumber: Int) {
        for (filter in myHyperlinkFilters) {
            val resultFuture = filter.apply(lineInfo)
            resultFuture.whenComplete(BiConsumer { result: LinkResult?, error: Throwable? ->
                if (result != null) {
                    applyLinkResultsOrReschedule(linesStorage, lineInfo, result.items.filterNotNull().toMutableList(), attemptNumber)
                }
            })
        }
    }

    private fun applyLinkResultsOrReschedule(
        linesStorage: LinesStorage,
        lineInfo: LineInfoImpl,
        resultItems: MutableList<LinkResultItem>,
        attemptNumber: Int
    ) {
        if (resultItems.isEmpty()) return
        val buffer = myTerminalTextBuffer ?: return
        buffer.lock()
        try {
            val lineStr = lineInfo.line
            if (lineStr == null) return
            val terminalWidth = buffer.width
            if (lineInfo.myTerminalWidth == terminalWidth) {
                applyLinkResults(resultItems, lineInfo, lineStr)
            } else if (attemptNumber < MAX_RESCHEDULING_ATTEMPTS) {
                // All `TerminalLine` instances are re-created by `ChangeWidthOperation`.
                // Therefore, `TerminalLine` instances referenced by the `lineInfo` are not in the text buffer,
                // and we need to find new lines and reschedule hyperlinks highlighting.
                val matchedWrappedLines = TextProcessing.TerminalLineFinder(buffer, lineStr)
                    .findMatchedLines(200 /* a line might be pushed to the history buffer */)
                for (wrappedLine in matchedWrappedLines) {
                    val newLineInfo: LineInfoImpl = LineInfoImpl(wrappedLine.filterNotNull().toMutableList(), terminalWidth)
                    doProcessHyperlinks(linesStorage, newLineInfo, attemptNumber + 1)
                }
            }
        } finally {
            buffer.unlock()
        }
    }

    private fun buildLineInfo(linesStorage: LinesStorage, updatedLine: TerminalLine): LineInfoImpl? {
        var linesStorage = linesStorage
        val buffer = myTerminalTextBuffer ?: return null
        buffer.lock()
        try {
            var updatedLineInd = linesStorage.indexOf(updatedLine)
            if (updatedLineInd == -1) {
                // When lines arrive fast enough, the line might be pushed to the history buffer already.
                val historyLinesStorage = buffer.historyLinesStorage
                updatedLineInd = findHistoryLineInd(historyLinesStorage, updatedLine)
                if (updatedLineInd == -1) {
                    LOG.debug("Cannot find line for links processing")
                    return null
                }
                linesStorage = historyLinesStorage
            }
            val startLineInd = findStartLineInd(linesStorage, updatedLineInd)
            val linesToProcess = collectLines(linesStorage, startLineInd, updatedLineInd)
            return LineInfoImpl(linesToProcess, buffer.width)
        } finally {
            buffer.unlock()
        }
    }

    private fun collectLines(
        linesStorage: LinesStorage,
        startLineInd: Int,
        updatedLineInd: Int
    ): MutableList<TerminalLine> {
        if (startLineInd == updatedLineInd) {
            return mutableListOf(linesStorage.get(startLineInd))
        }
        val result: MutableList<TerminalLine> = ArrayList<TerminalLine>(updatedLineInd - startLineInd + 1)
        for (i in startLineInd..updatedLineInd) {
            result.add(linesStorage.get(i))
        }
        return result
    }

    private fun findStartLineInd(linesStorage: LinesStorage, lineInd: Int): Int {
        var startLineInd = lineInd
        while (startLineInd > 0 && linesStorage.get(startLineInd - 1).isWrapped) {
            startLineInd--
        }
        return startLineInd
    }

    private fun applyLinkResults(
        linkResultItems: MutableList<LinkResultItem>,
        lineInfo: LineInfoImpl,
        lineStr: String
    ) {
        var linkAdded = false
        val terminalWidth = lineInfo.myTerminalWidth
        val actualLineStr: String = joinLines(lineInfo.myLinesToProcess, terminalWidth)
        if (actualLineStr != lineStr) {
            LOG.warn("Outdated lines when applying hyperlinks")
            return
        }
        for (item in linkResultItems) {
            if (item.startOffset < 0 || item.endOffset > lineStr.length) continue
            val style: TextStyle = HyperlinkStyle(
                myHyperlinkColor.foreground, myHyperlinkColor.background,
                item.linkInfo ?: LinkInfo {}, myHighlightMode, null
            )
            var prevLinesLength = 0
            for (line in lineInfo.myLinesToProcess) {
                val startLineOffset = max(prevLinesLength, item.startOffset)
                val endLineOffset = min(prevLinesLength + lineInfo.myTerminalWidth, item.endOffset)
                if (startLineOffset < endLineOffset) {
                    line.writeString(
                        startLineOffset - prevLinesLength,
                        CharBuffer(lineStr.substring(startLineOffset, endLineOffset)),
                        style
                    )
                    linkAdded = true
                }
                prevLinesLength += terminalWidth
            }
        }
        if (linkAdded) {
            fireHyperlinksChanged()
        }
    }

    private fun fireHyperlinksChanged() {
        for (myHyperlinkListener in myHyperlinkListeners) {
            myHyperlinkListener.hyperlinksChanged()
        }
    }

    fun addHyperlinkListener(listener: TerminalHyperlinkListener) {
        myHyperlinkListeners.add(listener)
    }

    private fun findHistoryLineInd(historyLinesStorage: LinesStorage, line: TerminalLine): Int {
        val lastLineInd = max(0, historyLinesStorage.size - 200) // check only last lines in history buffer
        for (i in historyLinesStorage.size - 1 downTo lastLineInd) {
            if (historyLinesStorage.get(i) == line) {
                return i
            }
        }
        return -1
    }

    fun addHyperlinkFilter(filter: HyperlinkFilter) {
        addAsyncHyperlinkFilter(object : AsyncHyperlinkFilter {
            override fun apply(lineInfo: AsyncHyperlinkFilter.LineInfo): CompletableFuture<LinkResult?> {
                val lineStr = lineInfo.line
                if (lineStr == null) return CompletableFuture.completedFuture<LinkResult?>(null)
                val result = filter.apply(lineStr)
                return CompletableFuture.completedFuture<LinkResult?>(result)
            }
        })
    }

    fun addAsyncHyperlinkFilter(filter: AsyncHyperlinkFilter) {
        myHyperlinkFilters.add(filter)
    }

    fun applyFilter(lineStr: String): MutableList<LinkResultItem?> {
        return myHyperlinkFilters.stream().map<LinkResult?> { filter: AsyncHyperlinkFilter ->
            val resultFuture = filter.apply(object : AsyncHyperlinkFilter.LineInfo {
                override val line: String
                    get() = lineStr
            })
            try {
                return@map resultFuture.get(2, TimeUnit.SECONDS)
            } catch (e: Exception) {
                LOG.info("Failed to find links in {}", lineStr, e)
                return@map null
            }
        }.filter { obj: LinkResult? -> Objects.nonNull(obj) }
            .flatMap<LinkResultItem?> { result: LinkResult? -> result?.items?.stream() }
            .collect(Collectors.toList())
    }

    private inner class LineInfoImpl(val myLinesToProcess: MutableList<TerminalLine>, terminalWidth: Int) :
        AsyncHyperlinkFilter.LineInfo {
        private val initialModificationCounts: IntArray
        val myTerminalWidth: Int
        private var myCachedLineStr: String? = null

        @Volatile
        var isUpToDate: Boolean = true
            get() {
                var isUpToDate = field
                if (isUpToDate) {
                    isUpToDate = areLinesUpToDate()
                    field = isUpToDate
                }
                return isUpToDate
            }
            private set

        init {
            initialModificationCounts = IntArray(myLinesToProcess.size)
            var i = 0
            for (line in myLinesToProcess) {
                incModificationCount(line)
                initialModificationCounts[i++] = getModificationCount(line)
            }
            myTerminalWidth = terminalWidth
        }

        fun areLinesUpToDate(): Boolean {
            for (i in myLinesToProcess.indices) {
                val line = myLinesToProcess.get(i)
                if (getModificationCount(line) != initialModificationCounts[i]) {
                    return false
                }
            }
            return true
        }

        override val line: String?
            get() {
                if (!this.isUpToDate) return null
                if (myCachedLineStr == null) {
                    val buffer = myTerminalTextBuffer ?: return null
                    buffer.lock()
                    try {
                        myCachedLineStr = joinLines(
                            myLinesToProcess,
                            myTerminalWidth
                        )
                    } finally {
                        buffer.unlock()
                    }
                }
                return myCachedLineStr
            }
    }

    private class TerminalLineFinder(private val myTextBuffer: TerminalTextBuffer, private val myLineToFind: String) {
        private val myCurrentLine: MutableList<TerminalLine> = ArrayList<TerminalLine>()
        private val myMatchedLines: MutableList<MutableList<TerminalLine?>> = ArrayList<MutableList<TerminalLine?>>()

        fun findMatchedLines(topHistoryCount: Int): MutableList<MutableList<TerminalLine?>> {
            for (i in -min(topHistoryCount, myTextBuffer.historyLinesCount)..<myTextBuffer.screenLinesCount) {
                add(myTextBuffer.getLine(i))
            }
            return myMatchedLines
        }

        fun add(line: TerminalLine) {
            myCurrentLine.add(line)
            if (!line.isWrapped) {
                val lineStr: String = joinLines(myCurrentLine, myTextBuffer.width)
                if (lineStr == myLineToFind) {
                    myMatchedLines.add(ArrayList<TerminalLine?>(myCurrentLine))
                }
                myCurrentLine.clear()
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TextProcessing::class.java)
        private const val MAX_RESCHEDULING_ATTEMPTS = 5

        private fun joinLines(lines: MutableList<TerminalLine>, terminalWidth: Int): String {
            val result = StringBuilder()
            val size = lines.size
            for (i in 0..<size) {
                val text = lines.get(i).text
                result.append(text)
                if (i < size - 1 && text.length < terminalWidth) {
                    result.append(CharBuffer(CharUtils.EMPTY_CHAR, terminalWidth - text.length))
                }
            }
            return result.toString()
        }
    }
}
