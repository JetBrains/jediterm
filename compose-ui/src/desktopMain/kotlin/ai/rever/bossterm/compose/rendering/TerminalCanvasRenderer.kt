package ai.rever.bossterm.compose.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.rever.bossterm.compose.SelectionMode
import ai.rever.bossterm.compose.hyperlinks.Hyperlink
import ai.rever.bossterm.compose.hyperlinks.HyperlinkDetector
import ai.rever.bossterm.compose.settings.TerminalSettings
import ai.rever.bossterm.compose.util.ColorUtils
import ai.rever.bossterm.terminal.CursorShape
import ai.rever.bossterm.terminal.model.TerminalLine
import ai.rever.bossterm.terminal.model.pool.VersionedBufferSnapshot
import ai.rever.bossterm.terminal.util.CharUtils
import ai.rever.bossterm.terminal.TextStyle as BossTextStyle
import org.jetbrains.skia.FontMgr

/**
 * Holds all the state needed for terminal rendering.
 * Passed to the renderer to avoid long parameter lists.
 */
data class RenderingContext(
    // Buffer and dimensions
    val bufferSnapshot: VersionedBufferSnapshot,
    val cellWidth: Float,
    val cellHeight: Float,
    val baseCellHeight: Float,
    val cellBaseline: Float,

    // Scroll and visible area
    val scrollOffset: Int,
    val visibleCols: Int,
    val visibleRows: Int,

    // Font and text
    val textMeasurer: TextMeasurer,
    val measurementFontFamily: FontFamily,
    val fontSize: Float,

    // Settings
    val settings: TerminalSettings,
    val ambiguousCharsAreDoubleWidth: Boolean,

    // Selection state
    val selectionStart: Pair<Int, Int>?,
    val selectionEnd: Pair<Int, Int>?,
    val selectionMode: SelectionMode,

    // Search state
    val searchVisible: Boolean,
    val searchQuery: String,
    val searchMatches: List<Pair<Int, Int>>,
    val currentMatchIndex: Int,

    // Cursor state
    val cursorX: Int,
    val cursorY: Int,
    val cursorVisible: Boolean,
    val cursorBlinkVisible: Boolean,
    val cursorShape: CursorShape?,
    val cursorColor: Color?,
    val isFocused: Boolean,

    // Hyperlink state
    val hoveredHyperlink: Hyperlink?,
    val isModifierPressed: Boolean,

    // Blink state
    val slowBlinkVisible: Boolean,
    val rapidBlinkVisible: Boolean
)

/**
 * Terminal canvas renderer that handles all drawing operations.
 * Separates rendering logic from the composable for better maintainability.
 */
object TerminalCanvasRenderer {

    /**
     * Main rendering entry point. Renders the entire terminal buffer.
     * Uses a 3-pass system:
     * - Pass 1: Draw all backgrounds
     * - Pass 2: Draw all text
     * - Pass 3: Draw overlays (hyperlinks, search, selection, cursor)
     *
     * @return Map of row to detected hyperlinks for mouse hover detection
     */
    fun DrawScope.renderTerminal(ctx: RenderingContext): Map<Int, List<Hyperlink>> {
        val hyperlinksCache = mutableMapOf<Int, List<Hyperlink>>()

        // Pass 1: Draw backgrounds
        renderBackgrounds(ctx)

        // Pass 2: Draw text and collect hyperlinks
        val detectedHyperlinks = renderText(ctx)
        hyperlinksCache.putAll(detectedHyperlinks)

        // Pass 3: Draw overlays
        renderOverlays(ctx)

        return hyperlinksCache
    }

    /**
     * Pass 1: Render all cell backgrounds.
     */
    private fun DrawScope.renderBackgrounds(ctx: RenderingContext) {
        val snapshot = ctx.bufferSnapshot

        for (row in 0 until ctx.visibleRows) {
            val lineIndex = row - ctx.scrollOffset
            val line = snapshot.getLine(lineIndex)

            var col = 0
            while (col < ctx.visibleCols) {
                val char = line.charAt(col)
                val style = line.getStyleAt(col)

                // Skip DWC markers
                if (char == CharUtils.DWC) {
                    col++
                    continue
                }

                val x = col * ctx.cellWidth
                val y = row * ctx.cellHeight

                // Check if double-width
                val isWcwidthDoubleWidth = char != ' ' && char != '\u0000' &&
                    CharUtils.isDoubleWidthCharacter(char.code, ctx.ambiguousCharsAreDoubleWidth)

                // Get attributes
                val isInverse = style?.hasOption(BossTextStyle.Option.INVERSE) ?: false
                val isDim = style?.hasOption(BossTextStyle.Option.DIM) ?: false

                // Apply defaults FIRST, then swap if INVERSE
                val baseFg = style?.foreground?.let { ColorUtils.convertTerminalColor(it) }
                    ?: ctx.settings.defaultForegroundColor
                val baseBg = style?.background?.let { ColorUtils.convertTerminalColor(it) }
                    ?: ctx.settings.defaultBackgroundColor

                // THEN swap if INVERSE attribute is set
                val bgColor = if (isInverse) baseFg else baseBg

                // Draw background (single or double width)
                val bgWidth = if (isWcwidthDoubleWidth) ctx.cellWidth * 2 else ctx.cellWidth
                val bgHeight = if (ctx.settings.fillBackgroundInLineSpacing) ctx.cellHeight else ctx.baseCellHeight
                drawRect(
                    color = bgColor,
                    topLeft = Offset(x, y),
                    size = Size(bgWidth, bgHeight)
                )

                // Skip next column if double-width
                if (isWcwidthDoubleWidth) {
                    col++
                }

                col++
            }
        }
    }

    /**
     * Pass 2: Render all text with proper font handling.
     * Returns map of row to detected hyperlinks.
     */
    private fun DrawScope.renderText(ctx: RenderingContext): Map<Int, List<Hyperlink>> {
        val snapshot = ctx.bufferSnapshot
        val hyperlinksCache = mutableMapOf<Int, List<Hyperlink>>()

        for (row in 0 until ctx.visibleRows) {
            val lineIndex = row - ctx.scrollOffset
            val line = snapshot.getLine(lineIndex)

            // Detect hyperlinks in current line
            val hyperlinks = HyperlinkDetector.detectHyperlinks(line.text, row)
            if (hyperlinks.isNotEmpty()) {
                hyperlinksCache[row] = hyperlinks
            }

            // Text batching state
            val batchText = StringBuilder()
            var batchStartCol = 0
            var batchFgColor: Color? = null
            var batchIsBold = false
            var batchIsItalic = false
            var batchIsUnderline = false

            // Helper function to flush accumulated batch
            fun flushBatch() {
                if (batchText.isNotEmpty()) {
                    val x = batchStartCol * ctx.cellWidth
                    val y = row * ctx.cellHeight

                    val textStyle = TextStyle(
                        color = batchFgColor ?: ctx.settings.defaultForegroundColor,
                        fontFamily = ctx.measurementFontFamily,
                        fontSize = ctx.fontSize.sp,
                        fontWeight = if (batchIsBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (batchIsItalic) androidx.compose.ui.text.font.FontStyle.Italic
                            else androidx.compose.ui.text.font.FontStyle.Normal
                    )

                    drawText(
                        textMeasurer = ctx.textMeasurer,
                        text = batchText.toString(),
                        topLeft = Offset(x, y),
                        style = textStyle
                    )

                    // Draw underline for entire batch if needed
                    if (batchIsUnderline) {
                        val underlineY = y + ctx.cellHeight - 2f
                        val underlineWidth = batchText.length * ctx.cellWidth
                        drawLine(
                            color = batchFgColor ?: Color.White,
                            start = Offset(x, underlineY),
                            end = Offset(x + underlineWidth, underlineY),
                            strokeWidth = 1f
                        )
                    }

                    batchText.clear()
                }
            }

            var col = 0
            var visualCol = 0

            while (col < ctx.visibleCols) {
                val char = line.charAt(col)
                val style = line.getStyleAt(col)

                // Skip DWC markers
                if (char == CharUtils.DWC) {
                    col++
                    continue
                }

                // Check for ZWJ sequences
                val cleanText = buildString {
                    var i = col
                    var count = 0
                    while (i < snapshot.width && count < 20) {
                        val c = line.charAt(i)
                        if (c != CharUtils.DWC) {
                            append(c)
                            count++
                        }
                        i++
                    }
                }

                val hasZWJ = cleanText.contains('\u200D')
                val hasSkinTone = checkFollowingSkinTone(line, col, snapshot.width)

                if (hasZWJ || hasSkinTone) {
                    val graphemes = ai.rever.bossterm.terminal.util.GraphemeUtils.segmentIntoGraphemes(cleanText)
                    if (graphemes.isNotEmpty()) {
                        val grapheme = graphemes[0]
                        if (grapheme.hasZWJ || hasSkinTone) {
                            flushBatch()
                            val (colsSkipped, visualWidth) = renderZWJSequence(
                                ctx, row, visualCol, col, grapheme, line, snapshot.width, style
                            )
                            col += colsSkipped
                            visualCol += visualWidth
                            continue
                        }
                    }
                }

                val x = visualCol * ctx.cellWidth
                val y = row * ctx.cellHeight

                // Handle surrogate pairs
                val charAtCol1 = if (col + 1 < snapshot.width) line.charAt(col + 1) else null
                val charAtCol2 = if (col + 2 < snapshot.width) line.charAt(col + 2) else null

                val lowSurrogate = if (Character.isHighSurrogate(char)) {
                    when {
                        charAtCol1 != null && Character.isLowSurrogate(charAtCol1) -> charAtCol1
                        charAtCol1 == CharUtils.DWC && charAtCol2 != null && Character.isLowSurrogate(charAtCol2) -> charAtCol2
                        else -> null
                    }
                } else null

                val actualCodePoint = if (lowSurrogate != null && Character.isLowSurrogate(lowSurrogate)) {
                    Character.toCodePoint(char, lowSurrogate)
                } else char.code

                val wcwidthResult = char != ' ' && char != '\u0000' &&
                    CharUtils.isDoubleWidthCharacter(actualCodePoint, ctx.ambiguousCharsAreDoubleWidth)
                val isWcwidthDoubleWidth = charAtCol1 == CharUtils.DWC || wcwidthResult

                val charTextToRender = if (lowSurrogate != null && Character.isLowSurrogate(lowSurrogate)) {
                    "$char$lowSurrogate"
                } else {
                    char.toString()
                }

                // Character classification
                val isCursiveOrMath = actualCodePoint in 0x1D400..0x1D7FF
                val isTechnicalSymbol = actualCodePoint in 0x23E9..0x23FF
                val isEmojiOrWideSymbol = when (actualCodePoint) {
                    in 0x2600..0x26FF -> true
                    in 0x1F100..0x1F1FF -> true
                    in 0x1F300..0x1F9FF -> true
                    in 0x1F600..0x1F64F -> true
                    in 0x1F680..0x1F6FF -> true
                    else -> false
                }

                val isDoubleWidth = if (actualCodePoint >= 0x1F100) true else isWcwidthDoubleWidth

                // Check for variation selector
                val nextCharOffset = if (isWcwidthDoubleWidth) 2 else 1
                val nextChar = if (col + nextCharOffset < snapshot.width) line.charAt(col + nextCharOffset) else null
                val isEmojiWithVariationSelector = isEmojiOrWideSymbol &&
                    nextChar != null && (nextChar.code == 0xFE0F || nextChar.code == 0xFE0E)

                // Skip standalone variation selectors
                if ((char.code == 0xFE0F || char.code == 0xFE0E) && !isEmojiOrWideSymbol) {
                    col++
                    continue
                }

                // Get text attributes
                val isBold = style?.hasOption(BossTextStyle.Option.BOLD) ?: false
                val isItalic = style?.hasOption(BossTextStyle.Option.ITALIC) ?: false
                val isInverse = style?.hasOption(BossTextStyle.Option.INVERSE) ?: false
                val isDim = style?.hasOption(BossTextStyle.Option.DIM) ?: false
                val isUnderline = style?.hasOption(BossTextStyle.Option.UNDERLINED) ?: false
                val isHidden = style?.hasOption(BossTextStyle.Option.HIDDEN) ?: false
                val isSlowBlink = style?.hasOption(BossTextStyle.Option.SLOW_BLINK) ?: false
                val isRapidBlink = style?.hasOption(BossTextStyle.Option.RAPID_BLINK) ?: false

                // Calculate colors
                val baseFg = style?.foreground?.let { ColorUtils.convertTerminalColor(it) }
                    ?: ctx.settings.defaultForegroundColor
                val baseBg = style?.background?.let { ColorUtils.convertTerminalColor(it) }
                    ?: ctx.settings.defaultBackgroundColor
                var fgColor = if (isInverse) baseBg else baseFg
                if (isDim) fgColor = ColorUtils.applyDimColor(fgColor)

                val isBlinkVisible = when {
                    isSlowBlink -> ctx.slowBlinkVisible
                    isRapidBlink -> ctx.rapidBlinkVisible
                    else -> true
                }

                val canBatch = !isDoubleWidth && !isEmojiOrWideSymbol && !isCursiveOrMath && !isTechnicalSymbol &&
                    !isHidden && isBlinkVisible && char != ' ' && char != '\u0000'

                val styleMatches = batchText.isNotEmpty() &&
                    batchFgColor == fgColor &&
                    batchIsBold == isBold &&
                    batchIsItalic == isItalic &&
                    batchIsUnderline == isUnderline

                if (canBatch && (batchText.isEmpty() || styleMatches)) {
                    if (batchText.isEmpty()) {
                        batchStartCol = visualCol
                        batchFgColor = fgColor
                        batchIsBold = isBold
                        batchIsItalic = isItalic
                        batchIsUnderline = isUnderline
                    }
                    batchText.append(char)
                } else {
                    flushBatch()

                    if (char != ' ' && char != '\u0000' && !isHidden && isBlinkVisible) {
                        renderCharacter(
                            ctx, x, y, charTextToRender, actualCodePoint,
                            isDoubleWidth, isEmojiOrWideSymbol, isEmojiWithVariationSelector,
                            isCursiveOrMath, isTechnicalSymbol, nextChar,
                            fgColor, isBold, isItalic, isUnderline
                        )

                        if (isEmojiWithVariationSelector) {
                            col++
                        }
                    }
                }

                if (isWcwidthDoubleWidth) col++
                col++
                if (lowSurrogate != null) col++
                visualCol++
                if (isDoubleWidth) visualCol++
            }

            flushBatch()
        }

        return hyperlinksCache
    }

    /**
     * Pass 3: Render overlays (hyperlinks, search, selection, cursor).
     */
    private fun DrawScope.renderOverlays(ctx: RenderingContext) {
        val snapshot = ctx.bufferSnapshot

        // Hyperlink underline
        if (ctx.settings.hyperlinkUnderlineOnHover && ctx.hoveredHyperlink != null && ctx.isModifierPressed) {
            val link = ctx.hoveredHyperlink
            if (link.row in 0 until ctx.visibleRows) {
                val y = link.row * ctx.cellHeight
                val underlineY = y + ctx.cellHeight - 1f
                val startX = link.startCol * ctx.cellWidth
                val endX = link.endCol * ctx.cellWidth
                drawLine(
                    color = ctx.settings.hyperlinkColorValue,
                    start = Offset(startX, underlineY),
                    end = Offset(endX, underlineY),
                    strokeWidth = 1f
                )
            }
        }

        // Search match highlights
        if (ctx.searchVisible && ctx.searchMatches.isNotEmpty()) {
            val matchLength = ctx.searchQuery.length
            ctx.searchMatches.forEachIndexed { index, (matchCol, matchRow) ->
                val screenRow = matchRow + ctx.scrollOffset
                if (screenRow in 0 until ctx.visibleRows) {
                    val matchColor = if (index == ctx.currentMatchIndex) {
                        ctx.settings.currentSearchMarkerColorValue.copy(alpha = 0.6f)
                    } else {
                        ctx.settings.searchMarkerColorValue.copy(alpha = 0.4f)
                    }

                    for (charOffset in 0 until matchLength) {
                        val col = matchCol + charOffset
                        if (col in 0 until snapshot.width) {
                            val x = col * ctx.cellWidth
                            val y = screenRow * ctx.cellHeight
                            drawRect(
                                color = matchColor,
                                topLeft = Offset(x, y),
                                size = Size(ctx.cellWidth, ctx.cellHeight)
                            )
                        }
                    }
                }
            }
        }

        // Selection highlight
        if (ctx.selectionStart != null && ctx.selectionEnd != null &&
            !(ctx.searchVisible && ctx.searchMatches.isNotEmpty())) {
            renderSelectionHighlight(ctx)
        }

        // Cursor
        if (ctx.cursorVisible) {
            renderCursor(ctx)
        }

        // Focus indicator
        if (!ctx.isFocused) {
            drawText(
                textMeasurer = ctx.textMeasurer,
                text = "[Click to focus terminal]",
                topLeft = Offset(0f, size.height - 30f),
                style = TextStyle(
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            )
        }
    }

    /**
     * Render selection highlight rectangles.
     */
    private fun DrawScope.renderSelectionHighlight(ctx: RenderingContext) {
        val start = ctx.selectionStart ?: return
        val end = ctx.selectionEnd ?: return
        val snapshot = ctx.bufferSnapshot

        val (startCol, startRow) = start
        val (endCol, endRow) = end

        val (firstCol, firstRow, lastCol, lastRow) = if (startRow <= endRow) {
            listOf(startCol, startRow, endCol, endRow)
        } else {
            listOf(endCol, endRow, startCol, startRow)
        }

        val highlightColor = ctx.settings.selectionColorValue.copy(alpha = 0.3f)

        for (bufferRow in firstRow..lastRow) {
            val screenRow = bufferRow + ctx.scrollOffset
            if (screenRow in 0 until ctx.visibleRows) {
                val (colStart, colEnd) = when (ctx.selectionMode) {
                    SelectionMode.BLOCK -> {
                        minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                    }
                    SelectionMode.NORMAL -> {
                        if (firstRow == lastRow) {
                            minOf(firstCol, lastCol) to maxOf(firstCol, lastCol)
                        } else {
                            when (bufferRow) {
                                firstRow -> firstCol to (snapshot.width - 1)
                                lastRow -> 0 to lastCol
                                else -> 0 to (snapshot.width - 1)
                            }
                        }
                    }
                }

                for (col in colStart..colEnd) {
                    if (col in 0 until snapshot.width) {
                        val x = col * ctx.cellWidth
                        val y = screenRow * ctx.cellHeight
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(x, y),
                            size = Size(ctx.cellWidth, ctx.cellHeight)
                        )
                    }
                }
            }
        }
    }

    /**
     * Render the terminal cursor.
     */
    private fun DrawScope.renderCursor(ctx: RenderingContext) {
        val shouldShowCursor = when (ctx.cursorShape) {
            CursorShape.BLINK_BLOCK, CursorShape.BLINK_UNDERLINE, CursorShape.BLINK_VERTICAL_BAR -> ctx.cursorBlinkVisible
            else -> true
        }

        if (!shouldShowCursor) return

        val x = ctx.cursorX * ctx.cellWidth
        val adjustedCursorY = (ctx.cursorY - 1).coerceAtLeast(0)
        val y = adjustedCursorY * ctx.cellHeight
        val cursorAlpha = if (ctx.isFocused) 0.7f else 0.3f
        val cursorColor = (ctx.cursorColor ?: Color.White).copy(alpha = cursorAlpha)

        when (ctx.cursorShape) {
            CursorShape.BLINK_BLOCK, CursorShape.STEADY_BLOCK, null -> {
                drawRect(
                    color = cursorColor,
                    topLeft = Offset(x, y),
                    size = Size(ctx.cellWidth, ctx.cellHeight)
                )
            }
            CursorShape.BLINK_UNDERLINE, CursorShape.STEADY_UNDERLINE -> {
                val underlineHeight = ctx.cellHeight * 0.2f
                drawRect(
                    color = cursorColor,
                    topLeft = Offset(x, y + ctx.cellHeight - underlineHeight),
                    size = Size(ctx.cellWidth, underlineHeight)
                )
            }
            CursorShape.BLINK_VERTICAL_BAR, CursorShape.STEADY_VERTICAL_BAR -> {
                val barWidth = ctx.cellWidth * 0.15f
                drawRect(
                    color = cursorColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, ctx.cellHeight)
                )
            }
        }
    }

    /**
     * Check if current character is followed by skin tone modifier.
     */
    private fun checkFollowingSkinTone(line: TerminalLine, col: Int, width: Int): Boolean {
        var checkCol = col
        val currentChar = line.charAt(checkCol)

        if (Character.isHighSurrogate(currentChar)) {
            checkCol++
        }
        checkCol++

        if (checkCol < width && line.charAt(checkCol) == CharUtils.DWC) {
            checkCol++
        }

        if (checkCol < width - 1) {
            val c1 = line.charAt(checkCol)
            if (c1 == '\uD83C' && checkCol + 1 < width) {
                val c2 = line.charAt(checkCol + 1)
                if (c2.code in 0xDFFB..0xDFFF) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Render a ZWJ sequence (emoji family, skin tones, etc.).
     * Returns (columns skipped in buffer, visual width consumed).
     */
    private fun DrawScope.renderZWJSequence(
        ctx: RenderingContext,
        row: Int,
        visualCol: Int,
        col: Int,
        grapheme: ai.rever.bossterm.terminal.util.GraphemeCluster,
        line: TerminalLine,
        width: Int,
        style: BossTextStyle?
    ): Pair<Int, Int> {
        val x = visualCol * ctx.cellWidth
        val y = row * ctx.cellHeight

        val isBold = style?.hasOption(BossTextStyle.Option.BOLD) ?: false
        val isItalic = style?.hasOption(BossTextStyle.Option.ITALIC) ?: false
        val isInverse = style?.hasOption(BossTextStyle.Option.INVERSE) ?: false
        val isDim = style?.hasOption(BossTextStyle.Option.DIM) ?: false

        val baseFg = style?.foreground?.let { ColorUtils.convertTerminalColor(it) }
            ?: ctx.settings.defaultForegroundColor
        val baseBg = style?.background?.let { ColorUtils.convertTerminalColor(it) }
            ?: ctx.settings.defaultBackgroundColor
        var fgColor = if (isInverse) baseBg else baseFg
        if (isDim) fgColor = ColorUtils.applyDimColor(fgColor)

        val textStyle = TextStyle(
            color = fgColor,
            fontFamily = FontFamily.Default,
            fontSize = ctx.fontSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal
        )

        val measurement = ctx.textMeasurer.measure(grapheme.text, textStyle)
        val glyphWidth = measurement.size.width.toFloat()
        val glyphHeight = measurement.size.height.toFloat()

        val allocatedWidth = ctx.cellWidth * grapheme.visualWidth.toFloat()
        val targetHeight = ctx.cellHeight * 1.0f

        val widthScale = if (glyphWidth > 0) allocatedWidth / glyphWidth else 1.0f
        val heightScale = if (glyphHeight > 0) targetHeight / glyphHeight else 1.0f
        val scaleValue = minOf(widthScale, heightScale).coerceIn(0.8f, 2.5f)

        val scaledWidth = glyphWidth * scaleValue
        val scaledHeight = glyphHeight * scaleValue
        val centerX = x + (allocatedWidth - scaledWidth) / 2f

        scale(scaleX = scaleValue, scaleY = scaleValue, pivot = Offset(x, y + ctx.cellHeight / 2f)) {
            drawText(
                textMeasurer = ctx.textMeasurer,
                text = grapheme.text,
                topLeft = Offset(x + (centerX - x) / scaleValue, y),
                style = textStyle
            )
        }

        // Count buffer cells consumed by this grapheme
        var charsToSkip = 0
        var i = col
        var graphemeCharIndex = 0
        val graphemeText = grapheme.text

        while (i < width && graphemeCharIndex < graphemeText.length) {
            val c = line.charAt(i)

            if (c == CharUtils.DWC) {
                charsToSkip++
                i++
                continue
            }

            val expectedChar = graphemeText[graphemeCharIndex]

            if (Character.isHighSurrogate(c) && i + 1 < width) {
                val next = line.charAt(i + 1)
                if (Character.isLowSurrogate(next)) {
                    if (graphemeCharIndex + 1 < graphemeText.length &&
                        graphemeText[graphemeCharIndex] == c &&
                        graphemeText[graphemeCharIndex + 1] == next) {
                        charsToSkip += 2
                        i += 2
                        graphemeCharIndex += 2
                        continue
                    }
                }
            }

            if (expectedChar == c) {
                charsToSkip++
                i++
                graphemeCharIndex++
            } else {
                break
            }
        }

        // Skip trailing DWC markers
        while (i < width && line.charAt(i) == CharUtils.DWC) {
            charsToSkip++
            i++
        }

        return Pair(charsToSkip, grapheme.visualWidth)
    }

    /**
     * Render a single character with appropriate font and styling.
     */
    private fun DrawScope.renderCharacter(
        ctx: RenderingContext,
        x: Float,
        y: Float,
        charTextToRender: String,
        actualCodePoint: Int,
        isDoubleWidth: Boolean,
        isEmojiOrWideSymbol: Boolean,
        isEmojiWithVariationSelector: Boolean,
        isCursiveOrMath: Boolean,
        isTechnicalSymbol: Boolean,
        nextChar: Char?,
        fgColor: Color,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean
    ) {
        val fontForChar = if (isEmojiOrWideSymbol || isEmojiWithVariationSelector || isCursiveOrMath) {
            FontFamily.Default
        } else if (isTechnicalSymbol) {
            val skiaTypeface = FontMgr.default.matchFamilyStyle("STIX Two Math", org.jetbrains.skia.FontStyle.NORMAL)
            if (skiaTypeface != null) {
                FontFamily(androidx.compose.ui.text.platform.Typeface(skiaTypeface))
            } else {
                ctx.measurementFontFamily
            }
        } else {
            ctx.measurementFontFamily
        }

        val textStyle = TextStyle(
            color = fgColor,
            fontFamily = fontForChar,
            fontSize = ctx.fontSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
                else androidx.compose.ui.text.font.FontStyle.Normal
        )

        if (isDoubleWidth) {
            val measurement = ctx.textMeasurer.measure(charTextToRender, textStyle)
            val glyphWidth = measurement.size.width.toFloat()
            val allocatedWidth = ctx.cellWidth * 2

            if (glyphWidth < ctx.cellWidth * 1.5f) {
                val scaleX = allocatedWidth / glyphWidth.coerceAtLeast(1f)
                scale(scaleX = scaleX, scaleY = 1f, pivot = Offset(x, y + ctx.cellWidth)) {
                    drawText(
                        textMeasurer = ctx.textMeasurer,
                        text = charTextToRender,
                        topLeft = Offset(x, y),
                        style = textStyle
                    )
                }
            } else {
                val emptySpace = (allocatedWidth - glyphWidth).coerceAtLeast(0f)
                val centeringOffset = emptySpace / 2f
                drawText(
                    textMeasurer = ctx.textMeasurer,
                    text = charTextToRender,
                    topLeft = Offset(x + centeringOffset, y),
                    style = textStyle
                )
            }
        } else if (isEmojiOrWideSymbol) {
            val textToRender = if (isEmojiWithVariationSelector && nextChar != null) {
                "$charTextToRender$nextChar"
            } else {
                charTextToRender
            }

            val measurement = ctx.textMeasurer.measure(textToRender, textStyle)
            val glyphWidth = measurement.size.width.toFloat()
            val glyphHeight = measurement.size.height.toFloat()

            val targetWidth = ctx.cellWidth * 1.0f
            val targetHeight = ctx.cellHeight * 1.0f

            val widthScale = if (glyphWidth > 0) targetWidth / glyphWidth else 1.0f
            val heightScale = if (glyphHeight > 0) targetHeight / glyphHeight else 1.0f
            val scaleValue = minOf(widthScale, heightScale).coerceIn(1.0f, 2.5f)

            val scaledWidth = glyphWidth * scaleValue
            val scaledHeight = glyphHeight * scaleValue
            val xOffset = (ctx.cellWidth - scaledWidth) / 2f
            val yOffset = (ctx.cellHeight - scaledHeight) / 2f

            scale(scaleX = scaleValue, scaleY = scaleValue, pivot = Offset(x + ctx.cellWidth/2, y + ctx.cellHeight/2)) {
                drawText(
                    textMeasurer = ctx.textMeasurer,
                    text = textToRender,
                    topLeft = Offset(x + xOffset, y + yOffset),
                    style = textStyle
                )
            }
        } else if (isCursiveOrMath) {
            val measurement = ctx.textMeasurer.measure(charTextToRender, textStyle)
            val glyphWidth = measurement.size.width.toFloat()
            val centeringOffset = ((ctx.cellWidth - glyphWidth) / 2f).coerceAtLeast(0f)
            drawText(
                textMeasurer = ctx.textMeasurer,
                text = charTextToRender,
                topLeft = Offset(x + centeringOffset, y),
                style = textStyle
            )
        } else if (isTechnicalSymbol) {
            val measurement = ctx.textMeasurer.measure(charTextToRender, textStyle)
            val glyphWidth = measurement.size.width.toFloat()
            val glyphBaseline = measurement.firstBaseline
            val baselineAlignmentOffset = ctx.cellBaseline - glyphBaseline
            val centeringOffset = ((ctx.cellWidth - glyphWidth) / 2f).coerceAtLeast(0f)

            drawText(
                textMeasurer = ctx.textMeasurer,
                text = charTextToRender,
                topLeft = Offset(x + centeringOffset, y + baselineAlignmentOffset),
                style = textStyle
            )
        } else {
            drawText(
                textMeasurer = ctx.textMeasurer,
                text = charTextToRender,
                topLeft = Offset(x, y),
                style = textStyle
            )
        }

        // Draw underline
        if (isUnderline) {
            val underlineY = y + ctx.cellHeight - 2f
            val underlineWidth = if (isDoubleWidth) ctx.cellWidth * 2 else ctx.cellWidth
            drawLine(
                color = fgColor,
                start = Offset(x, underlineY),
                end = Offset(x + underlineWidth, underlineY),
                strokeWidth = 1f
            )
        }
    }
}
