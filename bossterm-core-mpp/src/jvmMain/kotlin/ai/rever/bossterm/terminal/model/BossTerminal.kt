package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.core.Color
import ai.rever.bossterm.core.Platform.Companion.current
import ai.rever.bossterm.core.TerminalCoordinates
import ai.rever.bossterm.core.compatibility.Point
import ai.rever.bossterm.core.input.MouseEvent
import ai.rever.bossterm.core.input.MouseWheelEvent
import ai.rever.bossterm.core.util.CellPosition
import ai.rever.bossterm.core.util.TermSize
import ai.rever.bossterm.terminal.*
import ai.rever.bossterm.terminal.emulator.charset.CharacterSet
import ai.rever.bossterm.terminal.emulator.charset.GraphicSetState
import ai.rever.bossterm.terminal.emulator.mouse.*
import ai.rever.bossterm.terminal.model.hyperlinks.LinkInfo
import ai.rever.bossterm.terminal.model.hyperlinks.LinkResultItem
import ai.rever.bossterm.terminal.util.CharUtils
import ai.rever.bossterm.terminal.util.GraphemeUtils
import org.jetbrains.annotations.Nls
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * Terminal that reflects obtained commands and text at [TerminalDisplay](handles change of cursor position, screen size etc)
 * and  [TerminalTextBuffer](stores printed text)
 *
 * @author traff
 */
class BossTerminal(
    private val myDisplay: TerminalDisplay,
    val terminalTextBuffer: TerminalTextBuffer,
    private val myStyleState: StyleState
) : Terminal, TerminalMouseListener, TerminalCoordinates {
    private var myScrollRegionTop = 1
    private var myScrollRegionBottom: Int

    @Volatile
    private var myCursorX = 0

    @Volatile
    private var myCursorY = 1

    private var myTerminalWidth: Int
    private var myTerminalHeight: Int

    private var myStoredCursor: StoredCursor? = null

    private val myModes: EnumSet<TerminalMode> = EnumSet.noneOf(TerminalMode::class.java)

    private val myTerminalKeyEncoder: TerminalKeyEncoder = TerminalKeyEncoder(current())

  private val myWindowTitlesStack = Stack<String>()
    private val myIconTitlesStack = Stack<String>()

    private val myTabulator: Tabulator

    private val myGraphicSetState: GraphicSetState

    private var myMouseFormat = MouseFormat.MOUSE_FORMAT_XTERM

    private var myTerminalOutput: TerminalOutputStream? = null

    private var myMouseMode = MouseMode.MOUSE_REPORTING_NONE
    private var myLastMotionReport: Point? = null
    private var myCursorYChanged = false

    private var myCursorColor: Color? = null

    private val myApplicationTitleListeners: MutableList<TerminalApplicationTitleListener> =
        CopyOnWriteArrayList<TerminalApplicationTitleListener>()
    private val myTerminalResizeListeners: MutableList<TerminalResizeListener> =
        CopyOnWriteArrayList<TerminalResizeListener>()

    override fun setModeEnabled(mode: TerminalMode?, enabled: Boolean) {
        mode?.let {
            if (enabled) {
                myModes.add(it)
            } else {
                myModes.remove(it)
            }

            it.setEnabled(this, enabled)
        }
    }

    override fun disconnected() {
        myDisplay.setCursorVisible(false)
    }

    private fun wrapLines() {
        if (myCursorX >= myTerminalWidth) {
            myCursorX = 0
            // clear the end of the line in the text buffer
            val line = terminalTextBuffer.getLine(myCursorY - 1)
            terminalTextBuffer.deleteCharacters(myTerminalWidth, myCursorY - 1, line.length() - myTerminalWidth)
            terminalTextBuffer.setLineWrapped(myCursorY - 1, false)
            if (this.isAutoWrap) {
                terminalTextBuffer.setLineWrapped(myCursorY - 1, true)
                myCursorY += 1
            }
        }
    }

    private fun finishText() {
        myDisplay.setCursor(myCursorX, myCursorY)
        scrollY()
    }

    override fun writeCharacters(string: String?) {
        string?.let {
            val normalized = Normalizer.normalize(it, Normalizer.Form.NFC)
            writeDecodedCharacters(decodeUsingGraphicalState(normalized))
        }
    }

    private fun writeDecodedCharacters(string: CharArray) {
        terminalTextBuffer.lock()
        try {
            if (myCursorYChanged && string.size > 0) {
                myCursorYChanged = false
                if (myCursorY > 1) {
                    terminalTextBuffer.setLineWrapped(myCursorY - 2, false)
                }
            }
            wrapLines()
            scrollY()

            if (string.size != 0) {
                val characters = newCharBuf(string)
                terminalTextBuffer.writeString(myCursorX, myCursorY, characters)
                myCursorX += characters.length
            }

            finishText()
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    @Throws(UnsupportedEncodingException::class)
    override fun writeDoubleByte(bytes: CharArray?) {
        bytes?.let {
            writeCharacters(String(it, 0, 2))
        }
    }


    private fun decodeUsingGraphicalState(string: String): CharArray {
        val chars = string.toCharArray()
        for (i in chars.indices) {
            chars[i] = myGraphicSetState.map(chars[i])
        }
        return chars
    }

    override fun writeUnwrappedString(string: String?) {
        if (string == null) return
        val length = string.length
        var off = 0
        while (off < length) {
            val amountInLine = min(distanceToLineEnd(), length - off)
            writeCharacters(string.substring(off, off + amountInLine))
            wrapLines()
            scrollY()
            off += amountInLine
        }
    }


    fun scrollY() {
        terminalTextBuffer.lock()
        try {
            if (myCursorY > myScrollRegionBottom) {
                val dy = myScrollRegionBottom - myCursorY
                myCursorY = myScrollRegionBottom
                scrollArea(myScrollRegionTop, scrollingRegionSize(), dy)
                myDisplay.setCursor(myCursorX, myCursorY)
            }
            if (myCursorY < myScrollRegionTop) {
                myCursorY = myScrollRegionTop
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    fun crnl() {
        carriageReturn()
        newLine()
    }

    override fun newLine() {
        myCursorYChanged = true
        myCursorY += 1

        scrollY()

        if (isAutoNewLine()) {
            carriageReturn()
        }

        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun mapCharsetToGL(num: Int) {
        myGraphicSetState.setGL(num)
    }

    override fun mapCharsetToGR(num: Int) {
        myGraphicSetState.setGR(num)
    }

    override fun designateCharacterSet(tableNumber: Int, ch: Char) {
        val gs = myGraphicSetState.getGraphicSet(tableNumber)
        myGraphicSetState.designateGraphicSet(gs, ch)
    }

    override fun singleShiftSelect(num: Int) {
        myGraphicSetState.overrideGL(num)
    }

    override fun setAnsiConformanceLevel(level: Int) {
        if (level == 1 || level == 2) {
            myGraphicSetState.designateGraphicSet(0, CharacterSet.ASCII) //ASCII designated as G0
            myGraphicSetState
                .designateGraphicSet(
                    1,
                    CharacterSet.ISO_LATIN_1
                ) // ISO Latin-1 supplemental designated as G1
            mapCharsetToGL(0)
            mapCharsetToGR(1)
        } else if (level == 3) {
            designateCharacterSet(0, 'B') //ASCII designated as G0
            mapCharsetToGL(0)
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * Sets the character encoding mode, which controls GR range (160-255) mapping behavior.
     *
     * @param encoding "UTF-8" to disable GR mapping (preserve multi-byte sequences),
     *                 "ISO-8859-1" to enable GR mapping through character sets
     */
    fun setCharacterEncoding(encoding: String) {
        val useGRMapping = encoding.equals("ISO-8859-1", ignoreCase = true)
        myGraphicSetState.setUseGRMapping(useGRMapping)
    }

    override fun setWindowTitle(name: String) {
        changeApplicationTitle(name)
    }

    override fun addApplicationTitleListener(listener: TerminalApplicationTitleListener) {
        myApplicationTitleListeners.add(listener)
    }

    override fun removeApplicationTitleListener(listener: TerminalApplicationTitleListener) {
        myApplicationTitleListeners.remove(listener)
    }

    private fun changeApplicationTitle(newApplicationTitle: @Nls String) {
        for (applicationTitleListener in myApplicationTitleListeners) {
            applicationTitleListener.onApplicationTitleChanged(newApplicationTitle)
        }
        myDisplay.windowTitle = newApplicationTitle
    }

    override fun saveWindowTitleOnStack() {
        val title = myDisplay.windowTitle
        myWindowTitlesStack.push(title)
    }

    override fun restoreWindowTitleFromStack() {
        if (!myWindowTitlesStack.empty()) {
            val title = myWindowTitlesStack.pop()
            changeApplicationTitle(title)
        }
    }

    override fun setIconTitle(name: String) {
        changeApplicationIconTitle(name)
    }

    override fun saveIconTitleOnStack() {
        val title = myDisplay.iconTitle
        myIconTitlesStack.push(title)
    }

    override fun restoreIconTitleFromStack() {
        if (!myIconTitlesStack.empty()) {
            val title = myIconTitlesStack.pop()
            changeApplicationIconTitle(title)
        }
    }

    private fun changeApplicationIconTitle(newIconTitle: String) {
        for (listener in myApplicationTitleListeners) {
            listener.onApplicationIconTitleChanged(newIconTitle)
        }
        myDisplay.iconTitle = newIconTitle
    }

    override fun addResizeListener(listener: TerminalResizeListener) {
        myTerminalResizeListeners.add(listener)
    }

    override fun removeResizeListener(listener: TerminalResizeListener) {
        myTerminalResizeListeners.remove(listener)
    }

    override val windowForeground: Color?
        get() = myDisplay.windowForeground

    override val windowBackground: Color?
        get() = myDisplay.windowBackground

    override var cursorColor: Color?
        get() = myCursorColor
        set(color) {
            myCursorColor = color
            myDisplay.cursorColor = color
        }

    private val myCustomCommandListeners: MutableList<TerminalCustomCommandListener> =
        CopyOnWriteArrayList<TerminalCustomCommandListener>()

    init {

      myTerminalWidth = terminalTextBuffer.width
        myTerminalHeight = terminalTextBuffer.height

        myScrollRegionBottom = myTerminalHeight

        myTabulator = DefaultTabulator(myTerminalWidth)

        myGraphicSetState = GraphicSetState()

        reset(true)
    }


    override fun addCustomCommandListener(listener: TerminalCustomCommandListener) {
        myCustomCommandListeners.add(listener)
    }

    override fun processCustomCommand(args: MutableList<String?>) {
        for (listener in myCustomCommandListeners) {
            listener.process(args)
        }
    }

    override fun backspace() {
        myCursorX -= 1
        if (myCursorX < 0) {
            myCursorY -= 1
            myCursorX = myTerminalWidth - 1
        }
        adjustXY(-1)
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun carriageReturn() {
        myCursorX = 0
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun horizontalTab() {
        if (myCursorX >= myTerminalWidth) {
            return
        }
        val length = terminalTextBuffer.getLine(myCursorY - 1).text.length
        val stop = myTabulator.nextTab(myCursorX)
        myCursorX = max(myCursorX, length)
        if (myCursorX < stop) {
            val chars = CharArray(stop - myCursorX)
            Arrays.fill(chars, CharUtils.EMPTY_CHAR)
            writeDecodedCharacters(chars)
        } else {
            myCursorX = stop
        }
        adjustXY(+1)
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun eraseInDisplay(arg: Int) {
        // ED (Erase in Display) https://vt100.net/docs/vt510-rm/ED.html
        terminalTextBuffer.lock()
        try {
            val beginY: Int
            val endY: Int

            when (arg) {
                0 -> {
                    // Initial line
                    if (myCursorX < myTerminalWidth) {
                        terminalTextBuffer.eraseCharacters(myCursorX, -1, myCursorY - 1)
                    }
                    // Rest
                    beginY = myCursorY
                    endY = myTerminalHeight - 1
                }

                1 -> {
                    // initial line
                    terminalTextBuffer.eraseCharacters(0, myCursorX + 1, myCursorY - 1)

                    beginY = 0
                    endY = myCursorY - 1
                }

                2 -> {
                    beginY = 0
                    endY = myTerminalHeight - 1
                    terminalTextBuffer.moveScreenLinesToHistory()
                }

                3 -> {
                    // Clear entire screen and delete all lines saved in the scrollback buffer (xterm).
                    // https://en.wikipedia.org/wiki/ANSI_escape_code#CSIsection
                    // `clear` command emits it, and the scroll buffer is expected to be cleared as a result.
                    beginY = 0
                    endY = myTerminalHeight - 1
                    terminalTextBuffer.clearHistory()
                }

                else -> {
                    LOG.warn("Unsupported erase in display mode:" + arg)
                    beginY = 1
                    endY = 1
                }
            }
            // Rest of lines
            clearLines(beginY, endY)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun selectiveEraseInDisplay(arg: Int) {
        terminalTextBuffer.lock()
        try {
            var beginY = 0
            var endY = 0

            when (arg) {
                0 -> {
                    // Cursor to end of display (selective)
                    if (myCursorX < myTerminalWidth) {
                        terminalTextBuffer.selectiveEraseCharacters(myCursorX, -1, myCursorY - 1)
                    }
                    beginY = myCursorY
                    endY = myTerminalHeight - 1
                }

                1 -> {
                    // Beginning to cursor (selective)
                    terminalTextBuffer.selectiveEraseCharacters(0, myCursorX + 1, myCursorY - 1)
                    beginY = 0
                    endY = myCursorY - 1
                }

                2 -> {
                    // Entire display (selective)
                    beginY = 0
                    endY = myTerminalHeight - 1
                }

                else -> {
                    LOG.warn("Unsupported selective erase in display mode: $arg")
                    return
                }
            }

            // Selectively clear lines in range
            for (y in beginY..endY) {
                if (y != myCursorY - 1) {
                    terminalTextBuffer.selectiveEraseCharacters(0, -1, y)
                }
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    fun clearLines(beginY: Int, endY: Int) {
        terminalTextBuffer.lock()
        try {
            terminalTextBuffer.clearLines(beginY, endY)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun clearScreen() {
        clearLines(0, myTerminalHeight - 1)
    }

    override fun setCursorVisible(visible: Boolean) {
        myDisplay.setCursorVisible(visible)
    }

    override fun useAlternateBuffer(enabled: Boolean) {
        terminalTextBuffer.useAlternateBuffer(enabled)
        myDisplay.useAlternateScreenBuffer(enabled)

        // Clear protection when switching buffers
        myStyleState.current = myStyleState.current.toBuilder()
            .setOption(TextStyle.Option.PROTECTED, false)
            .build()
    }

    override fun getCodeForKey(key: Int, modifiers: Int): ByteArray? {
        return myTerminalKeyEncoder.getCode(key, modifiers)
    }

    override fun setApplicationArrowKeys(enabled: Boolean) {
        if (enabled) {
            myTerminalKeyEncoder.arrowKeysApplicationSequences()
        } else {
            myTerminalKeyEncoder.arrowKeysAnsiCursorSequences()
        }
    }

    override fun setApplicationKeypad(enabled: Boolean) {
        if (enabled) {
            myTerminalKeyEncoder.keypadApplicationSequences()
        } else {
            myTerminalKeyEncoder.keypadAnsiSequences()
        }
    }

    override fun setAutoNewLine(enabled: Boolean) {
        myTerminalKeyEncoder.setAutoNewLine(enabled)
    }

    override fun eraseInLine(arg: Int) {
        terminalTextBuffer.lock()
        try {
            when (arg) {
                0 -> {
                    if (myCursorX < myTerminalWidth) {
                        terminalTextBuffer.eraseCharacters(myCursorX, -1, myCursorY - 1)
                    }
                    // delete to the end of line : line is no more wrapped
                    terminalTextBuffer.setLineWrapped(myCursorY - 1, false)
                }

                1 -> {
                    val extent = min(myCursorX + 1, myTerminalWidth)
                    terminalTextBuffer.eraseCharacters(0, extent, myCursorY - 1)
                }

                2 -> terminalTextBuffer.eraseCharacters(0, -1, myCursorY - 1)
                else -> LOG.warn("Unsupported erase in line mode:" + arg)
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun selectiveEraseInLine(arg: Int) {
        terminalTextBuffer.lock()
        try {
            when (arg) {
                0 -> {
                    // Erase from cursor to end of line (selective)
                    if (myCursorX < myTerminalWidth) {
                        terminalTextBuffer.selectiveEraseCharacters(myCursorX, -1, myCursorY - 1)
                    }
                    terminalTextBuffer.setLineWrapped(myCursorY - 1, false)
                }

                1 -> {
                    // Erase from start of line to cursor (selective)
                    val extent = min(myCursorX + 1, myTerminalWidth)
                    terminalTextBuffer.selectiveEraseCharacters(0, extent, myCursorY - 1)
                }

                2 -> {
                    // Erase entire line (selective)
                    terminalTextBuffer.selectiveEraseCharacters(0, -1, myCursorY - 1)
                }

                else -> LOG.warn("Unsupported selective erase in line mode: $arg")
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun deleteCharacters(count: Int) {
        terminalTextBuffer.lock()
        try {
            terminalTextBuffer.deleteCharacters(myCursorX, myCursorY - 1, count)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun insertBlankCharacters(count: Int) {
        terminalTextBuffer.lock()
        try {
            val extent = min(count, myTerminalWidth - myCursorX)
            terminalTextBuffer.insertBlankCharacters(myCursorX, myCursorY - 1, extent)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun eraseCharacters(count: Int) {
        //Clear the next n characters on the cursor's line, including the cursor's
        //position.
        terminalTextBuffer.lock()
        try {
            terminalTextBuffer.eraseCharacters(myCursorX, myCursorX + count, myCursorY - 1)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun clearTabStopAtCursor() {
        myTabulator.clearTabStop(myCursorX)
    }

    override fun clearAllTabStops() {
        myTabulator.clearAllTabStops()
    }

    override fun setTabStopAtCursor() {
        myTabulator.setTabStop(myCursorX)
    }

    override fun insertLines(count: Int) {
        terminalTextBuffer.lock()
        try {
            terminalTextBuffer.insertLines(myCursorY - 1, count, myScrollRegionBottom)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun deleteLines(count: Int) {
        terminalTextBuffer.lock()
        try {
            terminalTextBuffer.deleteLines(myCursorY - 1, count, myScrollRegionBottom)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun cursorUp(countY: Int) {
        terminalTextBuffer.lock()
        try {
            myCursorYChanged = true
            myCursorY -= countY
            myCursorY = max(myCursorY, scrollingRegionTop())
            adjustXY(-1)
            myDisplay.setCursor(myCursorX, myCursorY)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun cursorDown(dY: Int) {
        terminalTextBuffer.lock()
        try {
            myCursorYChanged = true
            myCursorY += dY
            myCursorY = min(myCursorY, scrollingRegionBottom())
            adjustXY(-1)
            myDisplay.setCursor(myCursorX, myCursorY)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun index() {
        //Moves the cursor down one line in the
        //same column. If the cursor is at the
        //bottom margin, the page scrolls up
        terminalTextBuffer.lock()
        try {
            if (myCursorY == myScrollRegionBottom) {
                scrollArea(myScrollRegionTop, scrollingRegionSize(), -1)
            } else {
                myCursorY += 1
                adjustXY(-1)
                myDisplay.setCursor(myCursorX, myCursorY)
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    private fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) {
        myDisplay.scrollArea(scrollRegionTop, scrollRegionSize, dy)
        terminalTextBuffer.scrollArea(scrollRegionTop, dy, scrollRegionTop + scrollRegionSize - 1)
    }

    override fun nextLine() {
        terminalTextBuffer.lock()
        try {
            myCursorX = 0
            if (myCursorY == myScrollRegionBottom) {
                scrollArea(myScrollRegionTop, scrollingRegionSize(), -1)
            } else {
                myCursorY += 1
            }
            myDisplay.setCursor(myCursorX, myCursorY)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    private fun scrollingRegionSize(): Int {
        return myScrollRegionBottom - myScrollRegionTop + 1
    }

    override fun reverseIndex() {
        //Moves the cursor up one line in the same
        //column. If the cursor is at the top margin,
        //the page scrolls down.
        terminalTextBuffer.lock()
        try {
            if (myCursorY == myScrollRegionTop) {
                scrollArea(myScrollRegionTop, scrollingRegionSize(), 1)
            } else {
                myCursorY -= 1
                myDisplay.setCursor(myCursorX, myCursorY)
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    private fun scrollingRegionTop(): Int {
        return if (this.isOriginMode) myScrollRegionTop else 1
    }

    private fun scrollingRegionBottom(): Int {
        return if (this.isOriginMode) myScrollRegionBottom else myTerminalHeight
    }

    override fun cursorForward(dX: Int) {
        myCursorX += dX
        myCursorX = min(myCursorX, myTerminalWidth - 1)
        adjustXY(+1)
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun cursorBackward(dX: Int) {
        myCursorX -= dX
        myCursorX = max(myCursorX, 0)
        adjustXY(-1)
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun cursorShape(shape: CursorShape) {
        myDisplay.setCursorShape(shape)
    }

    // Helper function for reset that accepts null
    private fun cursorShapeNullable(shape: CursorShape?) {
        shape?.let { myDisplay.setCursorShape(it) } ?: myDisplay.setCursorShape(CursorShape.STEADY_BLOCK)
    }

    override fun cursorHorizontalAbsolute(x: Int) {
        cursorPosition(x, myCursorY)
    }

    override fun linePositionAbsolute(y: Int) {
        myCursorY = y
        adjustXY(-1)
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    override fun cursorPosition(x: Int, y: Int) {
        terminalTextBuffer.modify(Runnable {
            if (this.isOriginMode) {
                myCursorY = y + scrollingRegionTop() - 1
            } else {
                myCursorY = y
            }
            if (myCursorY > scrollingRegionBottom()) {
                myCursorY = scrollingRegionBottom()
            }

            // avoid issue due to malformed sequence
            myCursorX = max(0, x - 1)
            myCursorX = min(myCursorX, myTerminalWidth - 1)

            myCursorY = max(0, myCursorY)

            adjustXY(-1)
            myDisplay.setCursor(myCursorX, myCursorY)
        })
    }

    override fun setScrollingRegion(top: Int, bottom: Int) {
        if (top > bottom) {
            LOG.error("Top margin of scroll region can't be greater then bottom: " + top + ">" + bottom)
        }
        myScrollRegionTop = max(1, top)
        myScrollRegionBottom = min(myTerminalHeight, bottom)

        //DECSTBM moves the cursor to column 1, line 1 of the page
        cursorPosition(1, 1)
    }

    override fun scrollUp(count: Int) {
        scrollDown(-count)
    }

    override fun scrollDown(count: Int) {
        terminalTextBuffer.lock()
        try {
            scrollArea(myScrollRegionTop, scrollingRegionSize(), count)
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    override fun resetScrollRegions() {
        setScrollingRegion(1, myTerminalHeight)
    }

    override fun characterAttributes(textStyle: TextStyle?) {
        textStyle?.let {
            myStyleState.current = it
        }
    }

    override fun setCharacterProtection(enabled: Boolean) {
        myStyleState.current = if (enabled) {
            myStyleState.current.toBuilder()
                .setOption(TextStyle.Option.PROTECTED, true)
                .build()
        } else {
            myStyleState.current.toBuilder()
                .setOption(TextStyle.Option.PROTECTED, false)
                .build()
        }
    }

    override fun beep() {
        myDisplay.beep()
    }

    override fun distanceToLineEnd(): Int {
        return myTerminalWidth - myCursorX
    }

    override fun saveCursor() {
        myStoredCursor = createCursorState()
    }

    private fun createCursorState(): StoredCursor {
        return StoredCursor(
            myCursorX, myCursorY, myStyleState.current,
            this.isAutoWrap, this.isOriginMode, myGraphicSetState
        )
    }

    override fun restoreCursor() {
        val stored = myStoredCursor
        if (stored != null) {
            restoreCursor(stored)
        } else { //If nothing was saved by DECSC
            setModeEnabled(TerminalMode.OriginMode, false) //Resets origin mode (DECOM)
            cursorPosition(1, 1) //Moves the cursor to the home position (upper left of screen).
            myStyleState.reset() //Turns all character attributes off (normal setting).

            myGraphicSetState.resetState()
            //myGraphicSetState.designateGraphicSet(0, CharacterSet.ASCII);//Maps the ASCII character set into GL
            //mapCharsetToGL(0);
            //myGraphicSetState.designateGraphicSet(1, CharacterSet.DEC_SUPPLEMENTAL);
            //mapCharsetToGR(1); //and the DEC Supplemental Graphic set into GR
        }
        myDisplay.setCursor(myCursorX, myCursorY)
    }

    fun restoreCursor(storedCursor: StoredCursor) {
        myCursorX = storedCursor.cursorX
        myCursorY = storedCursor.cursorY

        adjustXY(-1)

        myStyleState.current = storedCursor.textStyle

        setModeEnabled(TerminalMode.AutoWrap, storedCursor.isAutoWrap)
        setModeEnabled(TerminalMode.OriginMode, storedCursor.isOriginMode)

        val designations = storedCursor.designations
        for (i in designations.indices) {
            designations[i]?.let { myGraphicSetState.designateGraphicSet(i, it) }
        }
        myGraphicSetState.setGL(storedCursor.gLMapping)
        myGraphicSetState.setGR(storedCursor.gRMapping)

        if (storedCursor.gLOverride >= 0) {
            myGraphicSetState.overrideGL(storedCursor.gLOverride)
        }
    }

    override fun reset(clearScrollBackBuffer: Boolean) {
        myGraphicSetState.resetState()

        myStyleState.reset()

        resetScrollRegions()

        useAlternateBuffer(false)
        if (clearScrollBackBuffer) {
            terminalTextBuffer.clearScreenAndHistoryBuffers()
        } else {
            terminalTextBuffer.clearScreenBuffer()
        }

        initModes()

        initMouseModes()

        // Clear character protection on full reset
        if (clearScrollBackBuffer) {
            myStyleState.current = myStyleState.current.toBuilder()
                .setOption(TextStyle.Option.PROTECTED, false)
                .build()
        }

        cursorPosition(1, 1)
        cursorShapeNullable(null)
    }

    private fun initMouseModes() {
        setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
        setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM)
    }

    private fun initModes() {
        myModes.clear()
        setModeEnabled(TerminalMode.AutoWrap, true)
        setModeEnabled(TerminalMode.AutoNewLine, false)
        setModeEnabled(TerminalMode.CursorVisible, true)
    }

    fun isModelEnabled(terminalMode: TerminalMode): Boolean {
        return myModes.contains(terminalMode)
    }

    fun isAutoNewLine(): Boolean {
        return myModes.contains(TerminalMode.AutoNewLine)
    }

    val isOriginMode: Boolean
        get() = myModes.contains(TerminalMode.OriginMode)

    val isAutoWrap: Boolean
        get() = myModes.contains(TerminalMode.AutoWrap)

    private fun mouseReport(button: Int, x: Int, y: Int): ByteArray {
        val sb = StringBuilder()
        var charset = "UTF-8" // extended mode requires UTF-8 encoding
        when (myMouseFormat) {
            MouseFormat.MOUSE_FORMAT_XTERM_EXT -> sb.append(
                String.format(
                    "\u001b[M%c%c%c",
                    (32 + button).toChar(),
                    (32 + x).toChar(),
                    (32 + y).toChar()
                )
            )

            MouseFormat.MOUSE_FORMAT_URXVT -> sb.append(String.format("\u001b[%d;%d;%dM", 32 + button, x, y))
            MouseFormat.MOUSE_FORMAT_SGR -> if ((button and MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG) != 0) {
                // for mouse release event
                sb.append(
                    String.format(
                        "\u001b[<%d;%d;%dm",
                        button xor MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG,
                        x,
                        y
                    )
                )
            } else {
                // for mouse press/motion event
                sb.append(String.format("\u001b[<%d;%d;%dM", button, x, y))
            }

            MouseFormat.MOUSE_FORMAT_XTERM -> {
                // X10 compatibility mode requires ASCII
                // US-ASCII is only 7 bits, so we use ISO-8859-1 (8 bits with ASCII transparency)
                // to handle positions greater than 95 (= 127-32)
                charset = "ISO-8859-1"
                sb.append(String.format("\u001b[M%c%c%c", (32 + button).toChar(), (32 + x).toChar(), (32 + y).toChar()))
            }
        }
        LOG.debug(myMouseFormat.toString() + " (" + charset + ") report : " + button + ", " + x + "x" + y + " = " + sb)
        return sb.toString().toByteArray(Charset.forName(charset))
    }

    private fun shouldSendMouseData(vararg eligibleModes: MouseMode?): Boolean {
        if (myMouseMode == MouseMode.MOUSE_REPORTING_NONE || myTerminalOutput == null) {
            return false
        }
        if (myMouseMode == MouseMode.MOUSE_REPORTING_ALL_MOTION) {
            return true
        }
        for (m in eligibleModes) {
            if (myMouseMode == m) {
                return true
            }
        }
        return false
    }

    override fun mousePressed(x: Int, y: Int, event: MouseEvent) {
        if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
            var cb = event.buttonCode

            if (cb != MouseButtonCodes.NONE) {
                if (cb == MouseButtonCodes.SCROLLDOWN || cb == MouseButtonCodes.SCROLLUP) {
                    // convert x11 scroll button number to terminal button code
                    val offset = MouseButtonCodes.SCROLLDOWN
                    cb -= offset
                    cb = cb or MouseButtonModifierFlags.MOUSE_BUTTON_SCROLL_FLAG
                }

                cb = cb or event.modifierKeys

                myTerminalOutput?.sendBytes(mouseReport(cb, x + 1, y + 1), true)
            }
        }
    }

    override fun mouseReleased(x: Int, y: Int, event: MouseEvent) {
        if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
            var cb = event.buttonCode

            if (cb != MouseButtonCodes.NONE) {
                if (myMouseFormat == MouseFormat.MOUSE_FORMAT_SGR) {
                    // for SGR 1006 mode
                    cb = cb or MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG
                } else {
                    // for 1000/1005/1015 mode
                    cb = MouseButtonCodes.RELEASE
                }

                cb = cb or event.modifierKeys

                myTerminalOutput?.sendBytes(mouseReport(cb, x + 1, y + 1), true)
            }
        }
        myLastMotionReport = null
    }

    override fun mouseMoved(x: Int, y: Int, event: MouseEvent) {
        val lastReport = myLastMotionReport
        if (lastReport != null && lastReport.equals(Point(x, y))) {
            return
        }
        if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
            myTerminalOutput?.sendBytes(mouseReport(MouseButtonCodes.RELEASE, x + 1, y + 1), true)
        }
        myLastMotionReport = Point(x, y)
    }

    override fun mouseDragged(x: Int, y: Int, event: MouseEvent) {
        val lastReport = myLastMotionReport
        if (lastReport != null && lastReport.equals(Point(x, y))) {
            return
        }
        if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
            //when dragging, button is not in "button", but in "modifier"
            var cb = event.buttonCode

            if (cb != MouseButtonCodes.NONE) {
                cb = cb or MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG
                cb = cb or event.modifierKeys
                myTerminalOutput?.sendBytes(mouseReport(cb, x + 1, y + 1), true)
            }
        }
        myLastMotionReport = Point(x, y)
    }

    override fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {
        // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons 
        mousePressed(x, y, event)
    }

    override fun setTerminalOutput(terminalOutput: TerminalOutputStream?) {
        myTerminalOutput = terminalOutput
    }

    override fun setMouseMode(mode: MouseMode) {
        myMouseMode = mode
        myDisplay.terminalMouseModeSet(mode)
    }

    override fun setAltSendsEscape(enabled: Boolean) {
        myTerminalKeyEncoder.setAltSendsEscape(enabled)
    }

    override fun deviceStatusReport(str: String?) {
        str?.let {
            myTerminalOutput?.sendString(it, false)
        }
    }

    override fun deviceAttributes(response: ByteArray?) {
        response?.let {
            myTerminalOutput?.sendBytes(it, false)
        }
    }

    override fun setLinkUriStarted(uri: String) {
        val textProcessing = terminalTextBuffer.getTextProcessing()
        if (textProcessing != null) {
            val style = myStyleState.current
            val linkResultItems = textProcessing.applyFilter(uri)
            linkResultItems.stream()
                .filter { item: LinkResultItem? -> item != null && item.startOffset == 0 && item.endOffset == uri.length }
                .findFirst().ifPresent(Consumer { linkItem: LinkResultItem? ->
                    myStyleState.current =
                        HyperlinkStyle(
                            style,
                            linkItem?.linkInfo ?: LinkInfo(Runnable {})
                        )
                })
        }
    }

    override fun setLinkUriFinished() {
        val current = myStyleState.current
        if (current is HyperlinkStyle) {
            val prevTextStyle = current.prevTextStyle
            if (prevTextStyle != null) {
                myStyleState.current = prevTextStyle
            }
        }
    }

    override fun setBracketedPasteMode(enabled: Boolean) {
        myDisplay.setBracketedPasteMode(enabled)
    }

    override fun setMouseFormat(mouseFormat: MouseFormat?) {
        mouseFormat?.let {
            myMouseFormat = it
            myDisplay.setMouseFormat(it)
        }
    }

    private fun adjustXY(dirX: Int) {
        if (myCursorY > -terminalTextBuffer.historyLinesCount &&
            Character.isLowSurrogate(terminalTextBuffer.getCharAt(myCursorX, myCursorY - 1))
        ) {
            // we don't want to place cursor on the second part of surrogate pair
            if (dirX > 0) { // so we move it into the predefined direction
                if (myCursorX == myTerminalWidth) { //if it is the last in the line we return where we were
                    myCursorX -= 1
                } else {
                    myCursorX += 1
                }
            } else {
                myCursorX = max(0, myCursorX - 1)
            }
        }
    }

    override var x: Int
        get() = myCursorX
        set(x) {
            myCursorX = x
            adjustXY(-1)
        }

    override var y: Int
        get() = myCursorY
        set(y) {
            myCursorY = y
            adjustXY(-1)
        }

    fun writeString(s: String) {
        writeCharacters(s)
    }

    override fun resize(newTermSize: TermSize, origin: RequestOrigin) {
        resizeInternal(ensureTermMinimumSize(newTermSize), origin)
    }

    private fun resizeInternal(newTermSize: TermSize, origin: RequestOrigin) {
        val oldHeight = myTerminalHeight
        if (newTermSize.columns == myTerminalWidth && newTermSize.rows == myTerminalHeight) {
            return
        }
        doResize(newTermSize, origin, oldHeight)
    }

    private fun doResize(newTermSize: TermSize, origin: RequestOrigin, oldHeight: Int) {
        val oldTermSize = TermSize(myTerminalWidth, myTerminalHeight)
        terminalTextBuffer.modify(Runnable {
            val result = terminalTextBuffer.resize(newTermSize, cursorPosition, myDisplay.selection)
            myTerminalWidth = newTermSize.columns
            myTerminalHeight = newTermSize.rows
            myCursorX = result.newCursor.x - 1
            myCursorY = result.newCursor.y
            myTabulator.resize(myTerminalWidth)
            myScrollRegionBottom += myTerminalHeight - oldHeight

            myDisplay.setCursor(myCursorX, myCursorY)
            myDisplay.onResize(newTermSize, origin)
            for (resizeListener in myTerminalResizeListeners) {
                resizeListener.onResize(oldTermSize, newTermSize)
            }
        })
    }

    override fun fillScreen(c: Char) {
        terminalTextBuffer.lock()
        try {
            val chars = CharArray(myTerminalWidth)
            Arrays.fill(chars, c)

            for (row in 1..myTerminalHeight) {
                terminalTextBuffer.writeString(0, row, newCharBuf(chars))
            }
        } finally {
            terminalTextBuffer.unlock()
        }
    }

    private fun newCharBuf(str: CharArray): CharBuffer {
        // Convert CharArray to String for grapheme segmentation
        val inputString = String(str)

        // Segment into grapheme clusters to handle surrogate pairs, emoji, etc.
        val graphemes = GraphemeUtils.segmentIntoGraphemes(inputString)

        // Count double-width graphemes to allocate buffer
        val dwcCount = graphemes.count { it.isDoubleWidth }

        if (dwcCount > 0) {
            // Leave gaps for the private use "DWC" character, which tells rendering to advance one cell
            val result = StringBuilder(inputString.length + dwcCount)

            for (grapheme in graphemes) {
                result.append(grapheme.text)
                if (grapheme.isDoubleWidth) {
                    result.append(CharUtils.DWC)
                }
            }

            val buf = result.toString().toCharArray()
            return CharBuffer(buf, 0, buf.size)
        } else {
            // No double-width graphemes, return as-is
            return CharBuffer(str, 0, str.size)
        }
    }

    override fun ambiguousCharsAreDoubleWidth(): Boolean {
        return myDisplay.ambiguousCharsAreDoubleWidth()
    }

    override val terminalWidth: Int
        get() = myTerminalWidth

    override val terminalHeight: Int
        get() = myTerminalHeight

    override val size: TermSize
        get() = TermSize(myTerminalWidth, myTerminalHeight)

    override val cursorX: Int
        get() = myCursorX + 1

    override val cursorY: Int
        get() = myCursorY

    override val cursorPosition: CellPosition
        get() = CellPosition(cursorX, cursorY)

    override val styleState: StyleState
        get() = myStyleState

    private class DefaultTabulator @JvmOverloads constructor(
        private var myWidth: Int,
        private val myTabLength: Int = TAB_LENGTH
    ) : Tabulator {
        private val myTabStops: SortedSet<Int?>

        init {
            myTabStops = TreeSet<Int?>()

            initTabStops(myWidth, myTabLength)
        }

        fun initTabStops(columns: Int, tabLength: Int) {
            var i = tabLength
            while (i < columns) {
                myTabStops.add(i)
                i += tabLength
            }
        }

        override fun resize(width: Int) {
            if (width > myWidth) {
                var i = myTabLength * (myWidth / myTabLength)
                while (i < width) {
                    if (i >= myWidth) {
                        myTabStops.add(i)
                    }
                    i += myTabLength
                }
            } else {
                val it = myTabStops.iterator()
                while (it.hasNext()) {
                    val i = it.next() ?: continue
                    if (i > width) {
                        it.remove()
                    }
                }
            }

            myWidth = width
        }

        override fun clearTabStop(position: Int) {
            myTabStops.remove(position)
        }

        override fun clearAllTabStops() {
            myTabStops.clear()
        }

        override fun getNextTabWidth(position: Int): Int {
            return nextTab(position) - position
        }

        override fun getPreviousTabWidth(position: Int): Int {
            return position - previousTab(position)
        }

        override fun nextTab(position: Int): Int {
            var tabStop = Int.Companion.MAX_VALUE

            // Search for the first tab stop after the given position...
            val tailSet = myTabStops.tailSet(position + 1)
            if (!tailSet.isEmpty()) {
                tabStop = tailSet.first() ?: Int.Companion.MAX_VALUE
            }

            // Don't go beyond the end of the line...
            return min(tabStop, (myWidth - 1))
        }

        override fun previousTab(position: Int): Int {
            var tabStop = 0

            // Search for the first tab stop before the given position...
            val headSet = myTabStops.headSet(position)
            if (!headSet.isEmpty()) {
                tabStop = headSet.last() ?: 0
            }

            // Don't go beyond the start of the line...
            return max(0, tabStop)
        }

        override fun setTabStop(position: Int) {
            myTabStops.add(position)
        }

        companion object {
            private const val TAB_LENGTH = 8
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BossTerminal::class.java.getName())

        private const val MIN_COLUMNS = 5
        private const val MIN_ROWS = 2

        fun ensureTermMinimumSize(termSize: TermSize): TermSize {
            return TermSize(max(MIN_COLUMNS, termSize.columns), max(MIN_ROWS, termSize.rows))
        }
    }
}
