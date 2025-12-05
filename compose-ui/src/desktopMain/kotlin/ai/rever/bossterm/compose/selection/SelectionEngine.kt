package ai.rever.bossterm.compose.selection

import ai.rever.bossterm.compose.SelectionMode
import ai.rever.bossterm.core.compatibility.Point
import ai.rever.bossterm.terminal.model.SelectionUtil.getNextSeparator
import ai.rever.bossterm.terminal.model.SelectionUtil.getPreviousSeparator
import ai.rever.bossterm.terminal.model.TerminalTextBuffer
import ai.rever.bossterm.terminal.util.CharUtils

/**
 * Engine for terminal text selection operations.
 * Handles word selection, line selection, and text extraction.
 */
object SelectionEngine {

    /**
     * Select word at the given character coordinates.
     * Uses SelectionUtil functions to find word boundaries at separator characters.
     *
     * Note: SelectionUtil functions expect TerminalTextBuffer and may handle locking internally.
     *
     * @param col Column position
     * @param row Row position (buffer-relative, can be negative for history)
     * @param textBuffer The terminal text buffer
     * @return Pair of start and end coordinates (col, row) for the word
     */
    fun selectWordAt(
        col: Int,
        row: Int,
        textBuffer: TerminalTextBuffer
    ): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        // Convert Pair<Int, Int> to Point for SelectionUtil
        val clickPoint = Point(col, row)

        // Get word boundaries using SelectionUtil
        // SelectionUtil may handle its own locking internally
        val startPoint = getPreviousSeparator(clickPoint, textBuffer)
        val endPoint = getNextSeparator(clickPoint, textBuffer)

        // Convert Point back to Pair<Int, Int>
        return Pair(Pair(startPoint.x, startPoint.y), Pair(endPoint.x, endPoint.y))
    }

    /**
     * Select entire logical line at the given character coordinates.
     * Handles wrapped lines by walking backwards and forwards through isWrapped property.
     *
     * @param col Column position (unused but kept for consistency)
     * @param row Row position (buffer-relative)
     * @param textBuffer The terminal text buffer
     * @return Pair of start and end coordinates for the logical line
     */
    fun selectLineAt(
        col: Int,
        row: Int,
        textBuffer: TerminalTextBuffer
    ): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        // Create immutable snapshot (fast, <1ms with lock, then lock released)
        // This allows PTY writers to continue during line selection calculation
        val snapshot = textBuffer.createSnapshot()

        var startLine = row
        var endLine = row

        // Walk backwards through wrapped lines to find logical line start
        while (startLine > -snapshot.historyLinesCount) {
            val prevLine = snapshot.getLine(startLine - 1)
            if (prevLine.isWrapped) {
                startLine--
            } else {
                break
            }
        }

        // Walk forwards through wrapped lines to find logical line end
        while (endLine < snapshot.height - 1) {
            val currentLine = snapshot.getLine(endLine)
            if (currentLine.isWrapped) {
                endLine++
            } else {
                break
            }
        }

        // Select from start of first line to end of last line
        return Pair(Pair(0, startLine), Pair(snapshot.width - 1, endLine))
    }

    /**
     * Extract selected text from the terminal text buffer.
     * Handles multi-line selection and normalizes coordinates.
     *
     * @param textBuffer The terminal text buffer
     * @param start Selection start position (col, row)
     * @param end Selection end position (col, row)
     * @param mode Selection mode (NORMAL for line-based, BLOCK for rectangular)
     * @return The extracted text as a string
     */
    fun extractSelectedText(
        textBuffer: TerminalTextBuffer,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        mode: SelectionMode = SelectionMode.NORMAL
    ): String {
        val (startCol, startRow) = start
        val (endCol, endRow) = end

        // Determine first (earlier row) and last (later row) points
        // This is direction-aware: first point is always the one with smaller row
        val (firstCol, firstRow, lastCol, lastRow) = if (startRow <= endRow) {
            listOf(startCol, startRow, endCol, endRow)
        } else {
            listOf(endCol, endRow, startCol, startRow)
        }

        // Use snapshot for lock-free text extraction
        val snapshot = textBuffer.createSnapshot()
        val result = StringBuilder()

        for (row in firstRow..lastRow) {
            val line = snapshot.getLine(row)

            // Calculate column bounds based on selection mode
            val (colStart, colEnd) = when (mode) {
                // BLOCK mode: rectangular selection - same columns for all rows
                SelectionMode.BLOCK -> {
                    minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                }
                // NORMAL mode: line-based selection
                SelectionMode.NORMAL -> {
                    if (firstRow == lastRow) {
                        // Single line: use min/max columns
                        minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                    } else {
                        // Multi-line: direction-aware columns
                        when (row) {
                            firstRow -> firstCol to (snapshot.width - 1)  // First row: from start col to end
                            lastRow -> 0 to lastCol                        // Last row: from 0 to end col
                            else -> 0 to (snapshot.width - 1)              // Middle rows: full line
                        }
                    }
                }
            }

            for (col in colStart..colEnd) {
                if (col < snapshot.width) {
                    val char = line.charAt(col)
                    // Skip DWC markers
                    if (char != CharUtils.DWC) {
                        result.append(char)
                    }
                }
            }

            // Add newline between rows (except after last row)
            if (row < lastRow) {
                result.append('\n')
            }
        }

        return result.toString()
    }
}
