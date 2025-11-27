package com.jediterm.terminal.emulator

import com.jediterm.core.Color
import com.jediterm.core.util.Ascii
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.util.CharUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.Boolean
import java.util.List
import kotlin.Array
import kotlin.Char
import kotlin.CharArray
import kotlin.Exception
import kotlin.Int
import kotlin.NumberFormatException
import kotlin.String
import kotlin.Throws
import kotlin.code
import kotlin.plus

/**
 * The main terminal emulator class.
 *
 *
 * Obtains data from the  [TerminalDataStream], interprets terminal ANSI escape sequences as commands and directs them
 * as well as plain data characters to the  [Terminal]
 *
 * @author traff
 */
class JediEmulator(dataStream: TerminalDataStream, terminal: Terminal?) :
    DataStreamIteratingEmulator(dataStream, terminal) {
    @Throws(IOException::class)
    public override fun processChar(ch: Char, terminal: Terminal?) {
        when (ch) {
            Ascii.NUL -> {}
            Ascii.BEL -> terminal?.beep()
            Ascii.BS -> terminal?.backspace()
            Ascii.CR -> terminal?.carriageReturn()
            Ascii.ENQ -> unsupported("Terminal status:" + escapeSequenceToString(ch))
            Ascii.FF, Ascii.LF, Ascii.VT ->         // '\n'
                terminal?.newLine()

            Ascii.SI ->         //LS0 (locking shift 0)
                //Map G0 into GL
                terminal?.mapCharsetToGL(0)

            Ascii.SO ->         //LS1 (locking shift 1)
                //Map G1 into GL
                if (Boolean.getBoolean("jediterm.enable.shift_out.character.support")) {
                    terminal?.mapCharsetToGL(1)
                }

            Ascii.HT -> terminal?.horizontalTab()
            Ascii.ESC -> processEscapeSequence(myDataStream.char, myTerminal)
            SystemCommandSequence.OSC -> processOsc()
            else -> if (ch <= Ascii.US) {
                val sb = StringBuilder("Unhandled control character:")
                CharUtils.appendChar(sb, CharUtils.CharacterType.NONE, ch)
                unhandledLogThrottler(sb.toString())
            } else { // Plain characters
                myDataStream.pushChar(ch)
                val nonControlCharacters =
                    readNonControlCharacters(terminal?.distanceToLineEnd() ?: 0, terminal?.ambiguousCharsAreDoubleWidth() ?: false)

                terminal?.writeCharacters(nonControlCharacters)
            }
        }
    }

    @Throws(IOException::class)
    private fun readNonControlCharacters(maxChars: Int, ambiguousAreDWC: kotlin.Boolean): String {
        val result = myDataStream.readNonControlCharacters(maxChars) ?: return ""
        var visualLength = 0
        var end = 0
        for (i in 0..<result.length) {
            // TODO surrogate pair support missing, but it must be implemented in the entire library at once
            val c = result[i]
            val sourceLength = i + 1
            visualLength += if (CharUtils.isDoubleWidthCharacter(c.code, ambiguousAreDWC)) 2 else 1
            // Three cases:
            if (visualLength == maxChars) {
                end = sourceLength // 1) found exactly maxChars
                break
            } else if (visualLength < maxChars) {
                end = sourceLength // 2) found less, continue searching
            } else { // visualLength > maxChars
                break // 3) found less on the previous iteration, but now it's too many (1 char of space left, but a DWC is found)
            }
        }
        var nextIsDWC = false
        if (end < result.length) {
            val pushBack = CharArray(result.length - end)
            result.toCharArray(pushBack, 0, end, result.length)
            nextIsDWC = CharUtils.isDoubleWidthCharacter(pushBack[0].code, ambiguousAreDWC)
            myDataStream.pushBackBuffer(pushBack, pushBack.size)
        }
        // A special case: if the next char is DWC, but it doesn't fit on this line (case 3 above),
        // then we must fill the line with an additional space to trigger line wrapping.
        // Otherwise, it'll be an endless loop: read, realize it doesn't fit, push back, read again...
        if (end == maxChars - 1 && nextIsDWC) return result.substring(0, end) + " "
        return result.substring(0, end)
    }

    @Throws(IOException::class)
    private fun processEscapeSequence(ch: Char, terminal: Terminal?) {
        when (ch) {
            '[' -> {
                val args = ControlSequence(myDataStream)
                if (!args.pushBackReordered(myDataStream)) {
                    try {
                        val result = processControlSequence(args)
                        if (LOG.isDebugEnabled()) {
                            if (result) {
                                LOG.debug("Control Sequence ({})", args.debugInfo)
                            } else {
                                LOG.warn("Unhandled Control Sequence ({})", args.debugInfo)
                            }
                        }
                    } catch (e: Exception) {
                        LOG.error("Error processing Control Sequence ({})", args.debugInfo, e)
                    }
                }
            }

            'D' -> terminal?.index()
            'E' -> terminal?.nextLine()
            'H' -> terminal?.setTabStopAtCursor()
            'M' -> terminal?.reverseIndex()
            'N' -> terminal?.singleShiftSelect(2) //Single Shift Select of G2 Character Set (SS2). This affects next character only.
            'O' -> terminal?.singleShiftSelect(3) //Single Shift Select of G3 Character Set (SS3). This affects next character only.
            'P' -> {
                val command = SystemCommandSequence(myDataStream)

                if (!deviceControlString(command)) {
                    LOG.warn("Error processing DCS: ESCP" + command)
                }
            }

            ']' -> processOsc()
            '6' -> unsupported("Back Index (DECBI), VT420 and up")
            '7' -> terminal?.saveCursor()
            '8' -> terminal?.restoreCursor()
            '9' -> unsupported("Forward Index (DECFI), VT420 and up")
            '=' -> setModeEnabled(TerminalMode.Keypad, true)
            '>' -> setModeEnabled(TerminalMode.Keypad, false)
            'F' -> terminal?.cursorPosition(1, terminal.terminalHeight)
            'c' -> terminal?.reset(true)
            'n' -> myTerminal?.mapCharsetToGL(2)
            'o' -> myTerminal?.mapCharsetToGL(3)
            '|' -> myTerminal?.mapCharsetToGR(3)
            '}' -> myTerminal?.mapCharsetToGR(2)
            '~' -> myTerminal?.mapCharsetToGR(1)
            '#', '(', ')', '*', '+', '$', '@', '%', '.', '/', ' ' -> processTwoCharSequence(ch, terminal)
            else -> unsupported(ch)
        }
    }

    @Throws(IOException::class)
    private fun processOsc() {
        val osc = SystemCommandSequence(myDataStream)
        try {
            val processed = doProcessOsc(osc)
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processed OSC (" + osc + "): " + processed)
            }
        } catch (e: Exception) {
            LOG.error("Error processing OSC (" + osc + ")", e)
        }
    }

    private fun deviceControlString(args: SystemCommandSequence?): kotlin.Boolean {
        return false
    }

    private fun doProcessOsc(args: SystemCommandSequence): kotlin.Boolean {
        // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h3-Operating-System-Commands
        val ps = args.getIntAt(0, -1)
        when (ps) {
            0 -> {  // Set both window and icon title
                val name = args.getStringAt(1)
                if (name != null) {
                    myTerminal?.setWindowTitle(name)
                    myTerminal?.setIconTitle(name)
                    return true
                }
            }
            1 -> {  // Set icon title only
                val name = args.getStringAt(1)
                if (name != null) {
                    myTerminal?.setIconTitle(name)
                    return true
                }
            }
            2 -> {  // Set window title only
                val name = args.getStringAt(1)
                if (name != null) {
                    myTerminal?.setWindowTitle(name)
                    return true
                }
            }

            7 ->         // Support for OSC 7 is pending
                // "return true" to avoid logging errors about unhandled sequences;
                return true

            8 -> {
                val uri = args.getStringAt(2)
                if (uri != null) {
                    if (!uri.isEmpty()) {
                        myTerminal?.setLinkUriStarted(uri)
                    } else {
                        myTerminal?.setLinkUriFinished()
                    }
                    return true
                }
            }

            10, 11 -> return processColorQuery(args)
            12 -> return processCursorColor(args)
            104, 1341 -> {
                val argList: MutableList<String?> = args.args.toMutableList()
                myTerminal?.processCustomCommand(argList.subList(1, argList.size) as MutableList<String?>)
                return true
            }
        }
        // Log unhandled OSC sequences to help identify missing support
        if (LOG.isDebugEnabled()) {
            LOG.debug("UNHANDLED OSC sequence: ps=" + ps + ", args=" + args.args)
        }
        return false
    }


    /**
     * [
 * If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of
 * the same form which can be used to set the corresponding dynamic color.
](http://www.xfree86.org/4.8.0/ctlseqs.html) *
     */
    private fun processColorQuery(args: SystemCommandSequence): kotlin.Boolean {
        if ("?" != args.getStringAt(1)) {
            return false
        }
        val ps = args.getIntAt(0, -1)
        val color: Color?
        if (ps == 10) {
            color = myTerminal?.windowForeground
        } else if (ps == 11) {
            color = myTerminal?.windowBackground
        } else {
            return false
        }
        if (color != null) {
            val str = args.format(List.of<String>(ps.toString(), color.toXParseColor()))
            if (LOG.isDebugEnabled()) {
                LOG.debug("Responding to OSC " + ps + " query: " + str)
            }
            myTerminal?.deviceStatusReport(str)
        }
        return true
    }

    /**
     * Handles OSC 12 (cursor color) queries and updates.
     * Query: ESC]12;?\ESC\
     * Set: ESC]12;#RRGGBB\ESC\ or ESC]12;rgb:RR/GG/BB\ESC\
     */
    private fun processCursorColor(args: SystemCommandSequence): kotlin.Boolean {
        val arg = args.getStringAt(1)
        if (arg == null) {
            return false
        }

        if ("?" == arg) {
            // Query cursor color
            val color = myTerminal?.cursorColor
            if (color != null) {
                val str = args.format(List.of<String>("12", color.toXParseColor()))
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Responding to OSC 12 query: " + str)
                }
                myTerminal?.deviceStatusReport(str)
            }
            return true
        }

        // Set cursor color
        try {
            val color = parseOscColor(arg)
            if (color != null) {
                myTerminal?.cursorColor = color
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Set cursor color to: " + arg)
                }
                return true
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to parse cursor color: " + arg)
                }
                return false
            }
        } catch (e: Exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to parse cursor color: " + arg, e)
            }
            return false
        }
    }

    /**
     * Parses OSC color string. Supports two formats:
     * 1. Hex format: #RRGGBB (e.g., #FF0000 for red)
     * 2. XParseColor format: rgb:RRRR/GGGG/BBBB (16-bit components, e.g., rgb:FFFF/0000/0000)
     *
     * @param colorStr The color string to parse
     * @return Color object or null if parsing fails
     */
    private fun parseOscColor(colorStr: String): Color? {
        try {
            // Handle hex format: #RRGGBB
            if (colorStr.startsWith("#")) {
                val hex = colorStr.substring(1)
                if (hex.length == 6) {
                    val rgb = hex.toInt(16)
                    return Color(rgb)
                }
            } else if (colorStr.startsWith("rgb:")) {
                val parts: Array<String?> =
                    colorStr.substring(4).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size == 3 && parts[0] != null && parts[1] != null && parts[2] != null) {
                    // Convert 16-bit values to 8-bit (divide by 0x101)
                    val r = parts[0]?.toInt(16)?.div(0x101) ?: 0
                    val g = parts[1]?.toInt(16)?.div(0x101) ?: 0
                    val b = parts[2]?.toInt(16)?.div(0x101) ?: 0
                    return Color(r, g, b)
                }
            }
        } catch (e: NumberFormatException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Invalid color format: " + colorStr, e)
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun processTwoCharSequence(ch: Char, terminal: Terminal?) {
        val secondCh = myDataStream.char
        when (ch) {
            ' ' -> when (secondCh) {
                'F' -> unsupported("Switching to 7-bit")
                'G' -> unsupported("Switching to 8-bit")
                'L' -> terminal?.setAnsiConformanceLevel(1)
                'M' -> terminal?.setAnsiConformanceLevel(2)
                'N' -> terminal?.setAnsiConformanceLevel(3)
                else -> unsupported(ch, secondCh)
            }

            '#' -> if (secondCh == '8') {
                terminal?.fillScreen('E')
            } else {
                unsupported(ch, secondCh)
            }

            '%' -> when (secondCh) {
                '@', 'G' -> unsupported("Selecting charset is unsupported: " + escapeSequenceToString(ch, secondCh))
                else -> unsupported(ch, secondCh)
            }

            '(' -> terminal?.designateCharacterSet(0, secondCh) //Designate G0 Character set (VT100)
            ')' -> terminal?.designateCharacterSet(1, secondCh) //Designate G1 Character set (VT100)
            '*' -> terminal?.designateCharacterSet(2, secondCh) //Designate G2 Character set (VT220)
            '+' -> terminal?.designateCharacterSet(3, secondCh) //Designate G3 Character set (VT220)
            '-' -> terminal?.designateCharacterSet(1, secondCh) //Designate G1 Character set (VT300)
            '.' -> terminal?.designateCharacterSet(2, secondCh) //Designate G2 Character set (VT300)
            '/' -> terminal?.designateCharacterSet(3, secondCh) //Designate G3 Character set (VT300)
            '$', '@' -> unsupported(ch, secondCh)
        }
    }

    /**
     * This method is used to handle unknown sequences. Can be overridden.
     *
     * @param sequenceChars are the characters of the unhandled sequence following the ESC character
     * (first ESC is excluded from the sequenceChars)
     */
    protected fun unsupported(vararg sequenceChars: Char) {
        unsupported(escapeSequenceToString(*sequenceChars))
    }

    private fun processControlSequence(args: ControlSequence): kotlin.Boolean {
        when (args.finalChar) {
            '@' -> return insertBlankCharacters(args) //ICH
            'A' -> return cursorUp(args) //CUU
            'B' -> return cursorDown(args) //CUD
            'C' -> return cursorForward(args) //CUF
            'D' -> return cursorBackward(args) //CUB
            'E' -> return cursorNextLine(args) //CNL
            'F' -> return cursorPrecedingLine(args) //CPL
            'G', '`' -> return cursorHorizontalAbsolute(args) //CHA
            'f', 'H' -> return cursorPosition(args)
            'J' -> return eraseInDisplay(args)
            'K' -> return eraseInLine(args)
            'L' -> return insertLines(args)
            'M' -> return deleteLines(args)
            'X' -> return eraseCharacters(args)
            'P' -> return deleteCharacters(args)
            'S' -> return scrollUp(args)
            'T' -> return scrollDown(args)
            'c' -> {
                if (args.startsWithMoreMark()) { //Send Device Attributes (Secondary DA)
                    if (args.getArg(0, 0) == 0) { //apply on to VT220 but xterm extends this to VT100
                        sendDeviceAttributes()
                        return true
                    }
                    return false
                }
                return sendDeviceAttributes()
            }

            'd' -> return linePositionAbsolute(args)
            'g' -> return tabClear(args.getArg(0, 0))
            'h' -> return setModeOrPrivateMode(args, true)
            'l' -> return setModeOrPrivateMode(args, false)
            'm' -> {
                if (args.startsWithMoreMark()) { //Set or reset resource-values used by xterm
                    // to decide whether to construct escape sequences holding information about
                    // the modifiers pressed with a given key
                    return false
                }
                if (args.startsWithQuestionMark()) {
                    // `CSI ? Pp m` Query key modifier options (XTQMODKEYS), xterm.
                    return false
                }
                return characterAttributes(args) //Character Attributes (SGR)
            }

            'n' -> return deviceStatusReport(args) //DSR
            'p' -> {
                if (args.startsWithExclamationMark()) {
                    // DECSTR (Soft Terminal Reset) https://vt100.net/docs/vt510-rm/DECSTR.html
                    myTerminal?.reset(false)
                    return true
                }
                return false
            }

            'q' -> return cursorShape(args) //DECSCUSR
            'r' -> if (args.startsWithQuestionMark()) {
                return restoreDecPrivateModeValues(args) //
            } else {
                //Set Top and Bottom Margins
                return setScrollingRegion(args) //DECSTBM
            }

            't' -> return windowManipulation(args)
            else -> return false
        }
    }

    private fun windowManipulation(args: ControlSequence): kotlin.Boolean {
        // CSI Ps ; Ps ; Ps t
        // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Functions-using-CSI-_-ordered-by-the-final-character-lparen-s-rparen:CSI-Ps;Ps;Ps-t.1EB0
        when (args.getArg(0, -1)) {
            1 ->         // Do not process "De-iconify window", restoring/unminimizing IDE can be unexpected.
                return true

            2 ->         // Do no process "Iconify window", minimizing IDE can be unexpected.
                return true

            8 -> {
                //        Ps = 8  ;  height ;  width -> Resize the text area to given
//        height and width in characters.  Omitted parameters reuse the
//        current height or width.  Zero parameters use the display's
//        height or width.
                var width = args.getArg(2, 0)
                var height = args.getArg(1, 0)
                if (width == 0) {
                    width = myTerminal?.terminalWidth ?: 0
                }
                if (height == 0) {
                    height = myTerminal?.terminalHeight ?: 0
                }
                myTerminal?.resize(TermSize(width, height), RequestOrigin.Remote)
                return true
            }

            22 -> return csi22(args)
            23 -> return csi23(args)
            else -> return false
        }
    }

    private fun csi22(args: ControlSequence): kotlin.Boolean {
        when (args.getArg(1, -1)) {
            0 -> {  // Save both window and icon title
                myTerminal?.saveWindowTitleOnStack()
                myTerminal?.saveIconTitleOnStack()
                return true
            }
            1 -> {  // Save icon title only
                myTerminal?.saveIconTitleOnStack()
                return true
            }
            2 -> {  // Save window title only
                myTerminal?.saveWindowTitleOnStack()
                return true
            }
            else -> return false
        }
    }

    private fun csi23(args: ControlSequence): kotlin.Boolean {
        when (args.getArg(1, -1)) {
            0 -> {  // Restore both window and icon title
                myTerminal?.restoreWindowTitleFromStack()
                myTerminal?.restoreIconTitleFromStack()
                return true
            }
            1 -> {  // Restore icon title only
                myTerminal?.restoreIconTitleFromStack()
                return true
            }
            2 -> {  // Restore window title only
                myTerminal?.restoreWindowTitleFromStack()
                return true
            }
            else -> return false
        }
    }

    private fun tabClear(mode: Int): kotlin.Boolean {
        if (mode == 0) { //Clear Current Column (default)
            myTerminal?.clearTabStopAtCursor()
            return true
        } else if (mode == 3) {
            myTerminal?.clearAllTabStops()
            return true
        } else {
            return false
        }
    }

    private fun eraseCharacters(args: ControlSequence): kotlin.Boolean {
        myTerminal?.eraseCharacters(args.getArg(0, 1))
        return true
    }

    private fun setModeOrPrivateMode(args: ControlSequence, enabled: kotlin.Boolean): kotlin.Boolean {
        if (args.startsWithQuestionMark()) { // DEC Private Mode
            when (args.getArg(0, -1)) {
                1 -> {
                    setModeEnabled(TerminalMode.CursorKey, enabled)
                    return true
                }

                3 -> {
                    setModeEnabled(TerminalMode.WideColumn, enabled)
                    return true
                }

                4 -> {
                    setModeEnabled(TerminalMode.SmoothScroll, enabled)
                    return true
                }

                5 -> {
                    setModeEnabled(TerminalMode.ReverseVideo, enabled)
                    return true
                }

                6 -> {
                    setModeEnabled(TerminalMode.OriginMode, enabled)
                    return true
                }

                7 -> {
                    setModeEnabled(TerminalMode.AutoWrap, enabled)
                    return true
                }

                8 -> {
                    setModeEnabled(TerminalMode.AutoRepeatKeys, enabled)
                    return true
                }

                12 ->           //setModeEnabled(TerminalMode.CursorBlinking, enabled);
                    //We want to show blinking cursor always
                    return true

                25 -> {
                    setModeEnabled(TerminalMode.CursorVisible, enabled)
                    return true
                }

                40 -> {
                    setModeEnabled(TerminalMode.AllowWideColumn, enabled)
                    return true
                }

                45 -> {
                    setModeEnabled(TerminalMode.ReverseWrapAround, enabled)
                    return true
                }

                47, 1047 -> {
                    setModeEnabled(TerminalMode.AlternateBuffer, enabled)
                    return true
                }

                1048 -> {
                    setModeEnabled(TerminalMode.StoreCursor, enabled)
                    return true
                }

                1049 -> {
                    setModeEnabled(TerminalMode.StoreCursor, enabled)
                    setModeEnabled(TerminalMode.AlternateBuffer, enabled)
                    return true
                }

                1000 -> {
                    if (enabled) {
                        setMouseMode(MouseMode.MOUSE_REPORTING_NORMAL)
                    } else {
                        setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
                    }
                    return true
                }

                1001 -> {
                    if (enabled) {
                        setMouseMode(MouseMode.MOUSE_REPORTING_HILITE)
                    } else {
                        setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
                    }
                    return true
                }

                1002 -> {
                    if (enabled) {
                        setMouseMode(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)
                    } else {
                        setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
                    }
                    return true
                }

                1003 -> {
                    if (enabled) {
                        setMouseMode(MouseMode.MOUSE_REPORTING_ALL_MOTION)
                    } else {
                        setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
                    }
                    return true
                }

                1004 ->           // stub focus gained/lost events for now
                    // https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-Mouse-Tracking
                    return true

                1005 -> {
                    if (enabled) {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM_EXT)
                    } else {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM)
                    }
                    return true
                }

                1006 -> {
                    if (enabled) {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_SGR)
                    } else {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM)
                    }
                    return true
                }

                1015 -> {
                    if (enabled) {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_URXVT)
                    } else {
                        myTerminal?.setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM)
                    }
                    return true
                }

                1034 -> {
                    setModeEnabled(TerminalMode.EightBitInput, enabled)
                    return true
                }

                1039 -> {
                    setModeEnabled(TerminalMode.AltSendsEscape, enabled)
                    return true
                }

                2004 -> {
                    setModeEnabled(TerminalMode.BracketedPasteMode, enabled)
                    return true
                }

                9001 ->           // suppress warnings about `win32-input-mode`
                    // https://github.com/microsoft/terminal/blob/main/doc/specs/%234999%20-%20Improved%20keyboard%20handling%20in%20Conpty.md
                    return true

                else -> return false
            }
        } else {
            when (args.getArg(0, -1)) {
                2 -> {
                    setModeEnabled(TerminalMode.KeyboardAction, enabled)
                    return true
                }

                4 -> {
                    setModeEnabled(TerminalMode.InsertMode, enabled)
                    return true
                }

                12 -> {
                    setModeEnabled(TerminalMode.SendReceive, enabled)
                    return true
                }

                20 -> {
                    setModeEnabled(TerminalMode.AutoNewLine, enabled)
                    return true
                }

                25 -> return true
                else -> return false
            }
        }
    }

    private fun linePositionAbsolute(args: ControlSequence): kotlin.Boolean {
        val y = args.getArg(0, 1)
        myTerminal?.linePositionAbsolute(y)

        return true
    }

    private fun restoreDecPrivateModeValues(args: ControlSequence?): kotlin.Boolean {
        LOG.warn("Unsupported: " + args)

        return false
    }

    private fun deviceStatusReport(args: ControlSequence): kotlin.Boolean {
        if (args.startsWithQuestionMark()) {
            LOG.warn("Don't support DEC-specific Device Report Status")
            return false
        }
        val c = args.getArg(0, 0)
        if (c == 5) {
            val str = "\u001b[0n"
            LOG.debug("Sending Device Report Status : " + str)
            myTerminal?.deviceStatusReport(str)
            return true
        } else if (c == 6) {
            val row = myTerminal?.cursorY ?: 1
            val column = myTerminal?.cursorX ?: 1
            val str = "\u001b[" + row + ";" + column + "R"

            LOG.debug("Sending Device Report Status : " + str)
            myTerminal?.deviceStatusReport(str)
            return true
        } else {
            LOG.warn("Sending Device Report Status : unsupported parameter: " + args)
            return false
        }
    }

    private fun cursorShape(args: ControlSequence): kotlin.Boolean {
        when (args.getArg(0, 0)) {
            0, 1 -> {
                myTerminal?.cursorShape(CursorShape.BLINK_BLOCK)
                return true
            }

            2 -> {
                myTerminal?.cursorShape(CursorShape.STEADY_BLOCK)
                return true
            }

            3 -> {
                myTerminal?.cursorShape(CursorShape.BLINK_UNDERLINE)
                return true
            }

            4 -> {
                myTerminal?.cursorShape(CursorShape.STEADY_UNDERLINE)
                return true
            }

            5 -> {
                myTerminal?.cursorShape(CursorShape.BLINK_VERTICAL_BAR)
                return true
            }

            6 -> {
                myTerminal?.cursorShape(CursorShape.STEADY_VERTICAL_BAR)
                return true
            }

            else -> {
                LOG.warn("Setting cursor shape : unsupported parameter " + args)
                return false
            }
        }
    }

    private fun insertLines(args: ControlSequence): kotlin.Boolean {
        myTerminal?.insertLines(args.getArg(0, 1))
        return true
    }

    private fun sendDeviceAttributes(): kotlin.Boolean {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Identifying to remote system as VT220")
        }
        // Use VT220 instead of VT102 for better TUI app compatibility (Neovim, vim, less, etc.)
        myTerminal?.deviceAttributes(CharUtils.VT220_RESPONSE)

        return true
    }

    private fun cursorHorizontalAbsolute(args: ControlSequence): kotlin.Boolean {
        val x = args.getArg(0, 1)

        myTerminal?.cursorHorizontalAbsolute(x)

        return true
    }

    private fun cursorNextLine(args: ControlSequence): kotlin.Boolean {
        var dx = args.getArg(0, 1)
        dx = if (dx == 0) 1 else dx
        myTerminal?.cursorDown(dx)
        myTerminal?.cursorHorizontalAbsolute(1)

        return true
    }

    private fun cursorPrecedingLine(args: ControlSequence): kotlin.Boolean {
        var dx = args.getArg(0, 1)
        dx = if (dx == 0) 1 else dx
        myTerminal?.cursorUp(dx)

        myTerminal?.cursorHorizontalAbsolute(1)

        return true
    }

    private fun insertBlankCharacters(args: ControlSequence): kotlin.Boolean {
        val count = args.getArg(0, 1)

        myTerminal?.insertBlankCharacters(count)

        return true
    }

    private fun eraseInDisplay(args: ControlSequence): kotlin.Boolean {
        if (args.startsWithQuestionMark()) {
            // Selective Erase (DECSED) is not supported
            return false
        }
        myTerminal?.eraseInDisplay(args.getArg(0, 0))
        return true
    }

    private fun eraseInLine(args: ControlSequence): kotlin.Boolean {
        // ESC [ Ps K
        val arg = args.getArg(0, 0)

        if (args.startsWithQuestionMark()) {
            //TODO: support ESC [ ? Ps K - Selective Erase (DECSEL)
            return false
        }

        myTerminal?.eraseInLine(arg)

        return true
    }

    private fun deleteLines(args: ControlSequence): kotlin.Boolean {
        // ESC [ Ps M
        myTerminal?.deleteLines(args.getArg(0, 1))
        return true
    }

    private fun deleteCharacters(args: ControlSequence): kotlin.Boolean {
        // ESC [ Ps P
        val arg = args.getArg(0, 1)

        myTerminal?.deleteCharacters(arg)

        return true
    }

    private fun cursorBackward(args: ControlSequence): kotlin.Boolean {
        var dx = args.getArg(0, 1)
        dx = if (dx == 0) 1 else dx

        myTerminal?.cursorBackward(dx)

        return true
    }

    private fun setScrollingRegion(args: ControlSequence): kotlin.Boolean {
        val top = args.getArg(0, 1)
        val bottom = args.getArg(1, myTerminal?.terminalHeight ?: 24)

        myTerminal?.setScrollingRegion(top, bottom)

        return true
    }

    private fun scrollUp(args: ControlSequence): kotlin.Boolean {
        val count = args.getArg(0, 1)
        myTerminal?.scrollUp(count)
        return true
    }

    private fun scrollDown(args: ControlSequence): kotlin.Boolean {
        val count = args.getArg(0, 1)
        myTerminal?.scrollDown(count)
        return true
    }

    private fun cursorForward(args: ControlSequence): kotlin.Boolean {
        var countX = args.getArg(0, 1)
        countX = if (countX == 0) 1 else countX

        myTerminal?.cursorForward(countX)

        return true
    }

    private fun cursorDown(cs: ControlSequence): kotlin.Boolean {
        var countY = cs.getArg(0, 0)
        countY = if (countY == 0) 1 else countY
        myTerminal?.cursorDown(countY)
        return true
    }

    private fun cursorPosition(cs: ControlSequence): kotlin.Boolean {
        val argy = cs.getArg(0, 1)
        val argx = cs.getArg(1, 1)

        myTerminal?.cursorPosition(argx, argy)

        return true
    }

    private fun characterAttributes(args: ControlSequence): kotlin.Boolean {
        val styleState: TextStyle = createStyleState(myTerminal?.styleState?.current, args)

        myTerminal?.characterAttributes(styleState)

        return true
    }

    private fun cursorUp(cs: ControlSequence): kotlin.Boolean {
        var arg = cs.getArg(0, 0)
        arg = if (arg == 0) 1 else arg
        myTerminal?.cursorUp(arg)
        return true
    }

    private fun setModeEnabled(mode: TerminalMode?, enabled: kotlin.Boolean) {
        if (LOG.isDebugEnabled()) {
            LOG.info("Setting mode " + mode + " enabled = " + enabled)
        }
        myTerminal?.setModeEnabled(mode, enabled)
    }

    fun setMouseMode(mouseMode: MouseMode) {
        myTerminal?.setMouseMode(mouseMode)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(JediEmulator::class.java)

        private var logThrottlerCounter = 0
        private const val logThrottlerRatio = 100
        private var logThrottlerLimit: Int = logThrottlerRatio

        /**
         * This method is used to report about know unsupported sequences
         *
         * @param msg The message describing the sequence
         */
        private fun unsupported(msg: String?) {
            unhandledLogThrottler("Unsupported control characters: " + msg)
        }

        private fun unhandledLogThrottler(msg: String?) {
            var msg = msg
            logThrottlerCounter++
            if (logThrottlerCounter < logThrottlerLimit) {
                if (logThrottlerCounter % (logThrottlerLimit / logThrottlerRatio) == 0) {
                    if (logThrottlerLimit / logThrottlerRatio > 1) {
                        msg += " and " + (logThrottlerLimit / logThrottlerRatio) + " more..."
                    }
                    LOG.warn(msg)
                }
            } else {
                logThrottlerLimit *= 10
            }
        }

        private fun escapeSequenceToString(vararg b: Char): String {
            val sb = StringBuilder("ESC ")

            for (c in b) {
                sb.append(' ')
                sb.append(c)
            }
            return sb.toString()
        }

        private fun createStyleState(textStyle: TextStyle?, args: ControlSequence): TextStyle {
            var builder = textStyle?.toBuilder() ?: TextStyle.Builder()
            val argCount = args.count
            if (argCount == 0) {
                builder = TextStyle.Builder()
            }

            var i = 0
            while (i < argCount) {
                var step = 1

                val arg = args.getArg(i, -1)
                if (arg == -1) {
                    LOG.warn("Error in processing char attributes, arg " + i)
                    i++
                    continue
                }

                when (arg) {
                    0 -> builder = TextStyle.Builder()
                    1 -> builder.setOption(TextStyle.Option.BOLD, true)
                    2 -> builder.setOption(TextStyle.Option.DIM, true)
                    3 -> builder.setOption(TextStyle.Option.ITALIC, true)
                    4 -> builder.setOption(TextStyle.Option.UNDERLINED, true)
                    5 -> {
                        builder.setOption(TextStyle.Option.SLOW_BLINK, true)
                        builder.setOption(TextStyle.Option.RAPID_BLINK, false)
                    }

                    6 -> {
                        builder.setOption(TextStyle.Option.SLOW_BLINK, false)
                        builder.setOption(TextStyle.Option.RAPID_BLINK, true)
                    }

                    7 -> builder.setOption(TextStyle.Option.INVERSE, true)
                    8 -> builder.setOption(TextStyle.Option.HIDDEN, true)
                    22 -> {
                        builder.setOption(TextStyle.Option.BOLD, false)
                        builder.setOption(TextStyle.Option.DIM, false)
                    }

                    23 -> builder.setOption(TextStyle.Option.ITALIC, false)
                    24 -> builder.setOption(TextStyle.Option.UNDERLINED, false)
                    25 -> {
                        builder.setOption(TextStyle.Option.SLOW_BLINK, false)
                        builder.setOption(TextStyle.Option.RAPID_BLINK, false)
                    }

                    27 -> builder.setOption(TextStyle.Option.INVERSE, false)
                    28 -> builder.setOption(TextStyle.Option.HIDDEN, false)
                    30, 31, 32, 33, 34, 35, 36, 37 -> builder.setForeground(TerminalColor.Companion.index(arg - 30))
                    38 -> {
                        val color256: TerminalColor? = getColor256(args, i)
                        if (color256 != null) {
                            builder.setForeground(color256)
                            step = getColor256Step(args, i)
                        }
                    }

                    39 -> builder.setForeground(null)
                    40, 41, 42, 43, 44, 45, 46, 47 -> builder.setBackground(TerminalColor.Companion.index(arg - 40))
                    48 -> {
                        val bgColor256: TerminalColor? = getColor256(args, i)
                        if (bgColor256 != null) {
                            builder.setBackground(bgColor256)
                            step = getColor256Step(args, i)
                        }
                    }

                    49 -> builder.setBackground(null)
                    90, 91, 92, 93, 94, 95, 96, 97 ->           //Bright versions of the ISO colors for foreground
                        builder.setForeground(ColorPalette.Companion.getIndexedTerminalColor(arg - 82))

                    100, 101, 102, 103, 104, 105, 106, 107 ->           //Bright versions of the ISO colors for background
                        builder.setBackground(ColorPalette.Companion.getIndexedTerminalColor(arg - 92))

                    else -> if (LOG.isDebugEnabled()) {
                        LOG.debug("Unknown character attribute:{}", arg)
                    }
                }
                i = i + step
            }
            return builder.build()
        }

        private fun getColor256(args: ControlSequence, index: Int): TerminalColor? {
            val code = args.getArg(index + 1, 0)

            if (code == 2) {
                /* direct color in rgb space */
                val val0 = args.getArg(index + 2, -1)
                val val1 = args.getArg(index + 3, -1)
                val val2 = args.getArg(index + 4, -1)
                if ((val0 >= 0 && val0 < 256) &&
                    (val1 >= 0 && val1 < 256) &&
                    (val2 >= 0 && val2 < 256)
                ) {
                    return TerminalColor(val0, val1, val2)
                } else {
                    LOG.warn("Bogus color setting " + args)
                    return null
                }
            } else if (code == 5) {
                /* indexed color */
                return ColorPalette.Companion.getIndexedTerminalColor(args.getArg(index + 2, 0))
            } else {
                LOG.warn("Unsupported code for color attribute " + args)
                return null
            }
        }

        private fun getColor256Step(args: ControlSequence, i: Int): Int {
            val code = args.getArg(i + 1, 0)
            if (code == 2) {
                return 5
            } else if (code == 5) {
                return 3
            }
            return 1
        }
    }
}
