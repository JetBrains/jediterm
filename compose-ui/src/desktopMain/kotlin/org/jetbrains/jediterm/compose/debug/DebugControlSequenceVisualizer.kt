package org.jetbrains.jediterm.compose.debug

/**
 * Visualizes control sequences and escape codes in a human-readable format.
 *
 * Converts raw terminal I/O data into annotated text showing:
 * - Escape sequences (ESC, CSI, OSC, etc.) with human-readable labels
 * - Invisible characters (spaces, tabs, newlines, etc.) as symbols
 * - Chunk boundaries and IDs
 * - Color coding for different sequence types
 *
 * Based on legacy JediTerm ControlSequenceVisualizer.
 */
object DebugControlSequenceVisualizer {

    /**
     * Visualize a list of chunks with the given settings.
     *
     * @param chunks List of data chunks to visualize
     * @param settings Visualization settings
     * @return Human-readable annotated text
     */
    fun visualize(
        chunks: List<DebugChunk>,
        settings: VisualizationSettings = VisualizationSettings()
    ): String {
        val output = StringBuilder()

        chunks.forEachIndexed { index, chunk ->
            if (settings.showChunkIds) {
                output.append("┌─ Chunk #${chunk.index} (${chunk.source}) [${chunk.data.size} chars] ─┐\n")
            }

            val visualized = visualizeChunk(chunk.data, settings)
            output.append(visualized)

            if (settings.showChunkIds) {
                output.append("\n└─────────────────────────────────────────────┘\n")
            }

            if (index < chunks.size - 1) {
                output.append("\n")
            }
        }

        return output.toString()
    }

    /**
     * Visualize a single chunk of data.
     */
    private fun visualizeChunk(
        data: CharArray,
        settings: VisualizationSettings
    ): String {
        val output = StringBuilder()
        var i = 0

        while (i < data.size) {
            val ch = data[i]

            when {
                // Escape sequence (ESC followed by other characters)
                ch == '\u001B' && i + 1 < data.size -> {
                    val (sequence, length) = parseEscapeSequence(data, i, settings)
                    output.append(sequence)
                    i += length
                }

                // Control characters (0x00 - 0x1F, 0x7F)
                ch < ' ' || ch == 0x7F.toChar() -> {
                    output.append(visualizeControlChar(ch, settings))
                    i++
                }

                // Regular printable character
                else -> {
                    if (settings.showInvisibleChars && ch == ' ') {
                        output.append('·')  // Middle dot for space
                    } else {
                        output.append(ch)
                    }
                    i++
                }
            }

            // Line wrapping
            if (settings.wrapLines && output.length % 80 == 0 && i < data.size) {
                output.append("↩\n")
            }
        }

        return output.toString()
    }

    /**
     * Parse an escape sequence starting at the given position.
     *
     * @return Pair of (visualized string, sequence length)
     */
    private fun parseEscapeSequence(
        data: CharArray,
        startIndex: Int,
        settings: VisualizationSettings
    ): Pair<String, Int> {
        if (startIndex >= data.size || data[startIndex] != '\u001B') {
            return Pair("", 0)
        }

        var i = startIndex + 1
        if (i >= data.size) {
            return Pair(colorCode("ESC", "red", settings), 1)
        }

        val secondChar = data[i]

        return when (secondChar) {
            '[' -> {
                // CSI sequence: ESC [ params letter
                val (params, endIndex) = extractCsiParams(data, i + 1)
                val command = if (endIndex < data.size) data[endIndex] else '?'
                val description = describeCsiCommand(command, params)
                val length = endIndex - startIndex + 1
                Pair(colorCode("CSI[$params$command]", "blue", settings) + " ⟨$description⟩", length)
            }

            ']' -> {
                // OSC sequence: ESC ] params ; text ST
                val (params, text, endIndex) = extractOscParams(data, i + 1)
                val description = describeOscCommand(params, text)
                val length = endIndex - startIndex + 1
                Pair(colorCode("OSC[$params;$text]", "green", settings) + " ⟨$description⟩", length)
            }

            '(' -> {
                // G0 character set selection: ESC ( X
                val charset = if (i + 1 < data.size) data[i + 1] else '?'
                Pair(colorCode("ESC($charset)", "yellow", settings) + " ⟨Set G0 charset⟩", 3)
            }

            ')' -> {
                // G1 character set selection: ESC ) X
                val charset = if (i + 1 < data.size) data[i + 1] else '?'
                Pair(colorCode("ESC)$charset)", "yellow", settings) + " ⟨Set G1 charset⟩", 3)
            }

            'D' -> {
                // Index (move cursor down, scroll if at bottom)
                Pair(colorCode("ESC D", "magenta", settings) + " ⟨Index/scroll down⟩", 2)
            }

            'M' -> {
                // Reverse Index (move cursor up, scroll if at top)
                Pair(colorCode("ESC M", "magenta", settings) + " ⟨Reverse index/scroll up⟩", 2)
            }

            '7' -> {
                // Save cursor position
                Pair(colorCode("ESC 7", "cyan", settings) + " ⟨Save cursor⟩", 2)
            }

            '8' -> {
                // Restore cursor position
                Pair(colorCode("ESC 8", "cyan", settings) + " ⟨Restore cursor⟩", 2)
            }

            'c' -> {
                // Reset terminal
                Pair(colorCode("ESC c", "red", settings) + " ⟨Reset terminal⟩", 2)
            }

            else -> {
                // Unknown escape sequence
                Pair(colorCode("ESC $secondChar", "red", settings) + " ⟨Unknown⟩", 2)
            }
        }
    }

    /**
     * Extract CSI parameters and find the command character.
     *
     * @return Pair of (parameters string, end index of command char)
     */
    private fun extractCsiParams(data: CharArray, startIndex: Int): Pair<String, Int> {
        val params = StringBuilder()
        var i = startIndex

        // Read parameter characters (0-9, ;, :, ?, etc.)
        while (i < data.size) {
            val ch = data[i]
            if (ch in '0'..'9' || ch == ';' || ch == ':' || ch == '?') {
                params.append(ch)
                i++
            } else {
                // Found the command character
                break
            }
        }

        return Pair(params.toString(), i)
    }

    /**
     * Extract OSC parameters (number and text).
     *
     * OSC format: ESC ] <number> ; <text> ST
     * ST can be: ESC \ or BEL (0x07)
     *
     * @return Triple of (number, text, end index)
     */
    private fun extractOscParams(data: CharArray, startIndex: Int): Triple<String, String, Int> {
        val number = StringBuilder()
        val text = StringBuilder()
        var i = startIndex

        // Read number until semicolon
        while (i < data.size && data[i] != ';') {
            number.append(data[i])
            i++
        }

        if (i < data.size) i++  // Skip semicolon

        // Read text until ST (ESC \ or BEL)
        while (i < data.size) {
            val ch = data[i]
            if (ch == '\u001B' && i + 1 < data.size && data[i + 1] == '\\') {
                i++  // Skip to backslash
                break
            } else if (ch == 0x07.toChar()) {  // BEL
                break
            } else {
                text.append(ch)
            }
            i++
        }

        return Triple(number.toString(), text.toString(), i)
    }

    /**
     * Describe a CSI command in human-readable format.
     */
    private fun describeCsiCommand(command: Char, params: String): String {
        return when (command) {
            'A' -> "Cursor up ${params.ifEmpty { "1" }} lines"
            'B' -> "Cursor down ${params.ifEmpty { "1" }} lines"
            'C' -> "Cursor forward ${params.ifEmpty { "1" }} columns"
            'D' -> "Cursor back ${params.ifEmpty { "1" }} columns"
            'H', 'f' -> "Cursor position ${params.ifEmpty { "1;1" }}"
            'J' -> {
                when (params) {
                    "0", "" -> "Clear from cursor to end of screen"
                    "1" -> "Clear from cursor to start of screen"
                    "2" -> "Clear entire screen"
                    "3" -> "Clear entire screen + scrollback"
                    else -> "Clear screen mode $params"
                }
            }
            'K' -> {
                when (params) {
                    "0", "" -> "Clear from cursor to end of line"
                    "1" -> "Clear from cursor to start of line"
                    "2" -> "Clear entire line"
                    else -> "Clear line mode $params"
                }
            }
            'm' -> "Set graphics mode: $params"
            'h' -> "Set mode: $params"
            'l' -> "Reset mode: $params"
            'r' -> "Set scroll region: $params"
            'n' -> "Device status report: $params"
            's' -> "Save cursor position"
            'u' -> "Restore cursor position"
            'd' -> "Move cursor to row $params"
            'G' -> "Move cursor to column $params"
            'X' -> "Erase $params characters"
            'P' -> "Delete $params characters"
            'L' -> "Insert $params lines"
            'M' -> "Delete $params lines"
            '@' -> "Insert $params blank characters"
            else -> "Command: $command"
        }
    }

    /**
     * Describe an OSC command in human-readable format.
     */
    private fun describeOscCommand(number: String, text: String): String {
        return when (number) {
            "0" -> "Set window title: $text"
            "1" -> "Set icon name: $text"
            "2" -> "Set window title: $text"
            "7" -> "Set working directory: $text"
            "8" -> "Set hyperlink: $text"
            "10" -> "Set foreground color: $text"
            "11" -> "Set background color: $text"
            "52" -> "Clipboard operation: $text"
            else -> "OSC command $number: $text"
        }
    }

    /**
     * Visualize a control character.
     */
    private fun visualizeControlChar(ch: Char, settings: VisualizationSettings): String {
        if (!settings.showInvisibleChars) {
            return ch.toString()
        }

        return when (ch.code) {
            0x00 -> "␀"  // NUL
            0x07 -> "␇"  // BEL
            0x08 -> "␈"  // BS
            0x09 -> "␉"  // TAB
            0x0A -> "␊\n"  // LF (show symbol + actual newline)
            0x0B -> "␋"  // VT
            0x0C -> "␌"  // FF
            0x0D -> "␍"  // CR
            0x0E -> "␎"  // SO
            0x0F -> "␏"  // SI
            0x7F -> "␡"  // DEL
            else -> "^${(ch.code + 0x40).toChar()}"  // ^A, ^B, etc.
        }
    }

    /**
     * Apply color coding to a string (for future UI rendering).
     *
     * For now, returns the text with ANSI color codes. In the UI layer,
     * we'll use Compose color spans instead.
     */
    private fun colorCode(text: String, color: String, settings: VisualizationSettings): String {
        if (!settings.colorCodeSequences) {
            return text
        }

        // For now, just wrap in brackets with color indicator
        // The UI will parse this and apply actual Compose colors
        return "[$color:$text]"
    }
}
