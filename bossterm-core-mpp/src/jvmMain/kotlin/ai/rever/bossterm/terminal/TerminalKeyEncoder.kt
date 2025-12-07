package ai.rever.bossterm.terminal

import ai.rever.bossterm.core.Platform
import ai.rever.bossterm.core.Platform.Companion.current
import ai.rever.bossterm.core.Platform.Companion.isMacOS
import ai.rever.bossterm.core.input.InputEvent
import ai.rever.bossterm.core.util.Ascii
import ai.rever.bossterm.terminal.util.CharUtils
import java.awt.event.KeyEvent.*
import java.util.*

/**
 * @author traff
 */
class TerminalKeyEncoder @JvmOverloads constructor(private val myPlatform: Platform = current()) {
    private val myKeyCodes: MutableMap<KeyCodeAndModifier?, ByteArray?> = HashMap<KeyCodeAndModifier?, ByteArray?>()

    private var myAltSendsEscape = true
    private var myMetaSendsEscape = false

    init {
        setAutoNewLine(false)
        arrowKeysAnsiCursorSequences()
        configureLeftRight()
        keypadAnsiSequences()
        putCode(VK_BACK_SPACE, Ascii.DEL.code)
        putCode(VK_F1, ESC, 'O'.code, 'P'.code)
        putCode(VK_F2, ESC, 'O'.code, 'Q'.code)
        putCode(VK_F3, ESC, 'O'.code, 'R'.code)
        putCode(VK_F4, ESC, 'O'.code, 'S'.code)
        putCode(VK_F5, ESC, '['.code, '1'.code, '5'.code, '~'.code)
        putCode(VK_F6, ESC, '['.code, '1'.code, '7'.code, '~'.code)
        putCode(VK_F7, ESC, '['.code, '1'.code, '8'.code, '~'.code)
        putCode(VK_F8, ESC, '['.code, '1'.code, '9'.code, '~'.code)
        putCode(VK_F9, ESC, '['.code, '2'.code, '0'.code, '~'.code)
        putCode(VK_F10, ESC, '['.code, '2'.code, '1'.code, '~'.code)
        putCode(VK_F11, ESC, '['.code, '2'.code, '3'.code, '~'.code)
        putCode(VK_F12, ESC, '['.code, '2'.code, '4'.code, '~'.code)

        putCode(VK_INSERT, ESC, '['.code, '2'.code, '~'.code)
        putCode(VK_DELETE, ESC, '['.code, '3'.code, '~'.code)

        putCode(VK_PAGE_UP, ESC, '['.code, '5'.code, '~'.code)
        putCode(VK_PAGE_DOWN, ESC, '['.code, '6'.code, '~'.code)

        putCode(VK_HOME, ESC, '['.code, 'H'.code)
        putCode(VK_END, ESC, '['.code, 'F'.code)

        putCode(KeyCodeAndModifier(VK_TAB, InputEvent.SHIFT_MASK), ESC, '['.code, 'Z'.code)

        putCode(KeyCodeAndModifier(VK_BACK_SPACE, InputEvent.CTRL_MASK), VK_BACK_SPACE)
        if (isMacOS) {
            putCode(KeyCodeAndModifier(VK_LEFT, InputEvent.META_MASK), Ascii.SOH.code)
            putCode(KeyCodeAndModifier(VK_RIGHT, InputEvent.META_MASK), Ascii.ENQ.code)
        }
    }

    fun arrowKeysApplicationSequences() {
        putCode(VK_UP, ESC, 'O'.code, 'A'.code)
        putCode(VK_DOWN, ESC, 'O'.code, 'B'.code)
        putCode(VK_RIGHT, ESC, 'O'.code, 'C'.code)
        putCode(VK_LEFT, ESC, 'O'.code, 'D'.code)
    }

    fun arrowKeysAnsiCursorSequences() {
        putCode(VK_UP, ESC, '['.code, 'A'.code)
        putCode(VK_DOWN, ESC, '['.code, 'B'.code)
        putCode(VK_RIGHT, ESC, '['.code, 'C'.code)
        putCode(VK_LEFT, ESC, '['.code, 'D'.code)
    }

    private fun configureLeftRight() {
        if (myPlatform == Platform.macOS) {
            putCode(KeyCodeAndModifier(VK_RIGHT, InputEvent.ALT_MASK), ESC, 'f'.code) // ^[f
            putCode(KeyCodeAndModifier(VK_LEFT, InputEvent.ALT_MASK), ESC, 'b'.code) // ^[b
        } else {
            putCode(
                KeyCodeAndModifier(VK_LEFT, InputEvent.CTRL_MASK),
                ESC,
                '['.code,
                '1'.code,
                ';'.code,
                '5'.code,
                'D'.code
            ) // ^[[1;5D
            putCode(
                KeyCodeAndModifier(VK_RIGHT, InputEvent.CTRL_MASK),
                ESC,
                '['.code,
                '1'.code,
                ';'.code,
                '5'.code,
                'C'.code
            ) // ^[[1;5C
            putCode(
                KeyCodeAndModifier(VK_LEFT, InputEvent.ALT_MASK),
                ESC,
                '['.code,
                '1'.code,
                ';'.code,
                '3'.code,
                'D'.code
            ) // ^[[1;3D
            putCode(
                KeyCodeAndModifier(VK_RIGHT, InputEvent.ALT_MASK),
                ESC,
                '['.code,
                '1'.code,
                ';'.code,
                '3'.code,
                'C'.code
            ) // ^[[1;3C
        }
    }

    fun keypadApplicationSequences() {
        putCode(VK_KP_DOWN, ESC, 'O'.code, 'B'.code) //2
        putCode(VK_KP_LEFT, ESC, 'O'.code, 'D'.code) //4
        putCode(VK_KP_RIGHT, ESC, 'O'.code, 'C'.code) //6
        putCode(VK_KP_UP, ESC, 'O'.code, 'A'.code) //8

        putCode(VK_HOME, ESC, 'O'.code, 'H'.code)
        putCode(VK_END, ESC, 'O'.code, 'F'.code)
    }

    fun keypadAnsiSequences() {
        putCode(VK_KP_DOWN, ESC, '['.code, 'B'.code) //2
        putCode(VK_KP_LEFT, ESC, '['.code, 'D'.code) //4
        putCode(VK_KP_RIGHT, ESC, '['.code, 'C'.code) //6
        putCode(VK_KP_UP, ESC, '['.code, 'A'.code) //8

        putCode(VK_HOME, ESC, '['.code, 'H'.code)
        putCode(VK_END, ESC, '['.code, 'F'.code)
    }

    fun putCode(code: Int, vararg bytesAsInt: Int) {
        myKeyCodes.put(KeyCodeAndModifier(code, 0), CharUtils.makeCode(*bytesAsInt))
    }

    private fun putCode(key: KeyCodeAndModifier, vararg bytesAsInt: Int) {
        myKeyCodes.put(key, CharUtils.makeCode(*bytesAsInt))
    }

    fun getCode(key: Int, modifiers: Int): ByteArray? {
        var bytes = myKeyCodes.get(KeyCodeAndModifier(key, modifiers))
        if (bytes != null) {
            return bytes
        }
        bytes = myKeyCodes.get(KeyCodeAndModifier(key, 0))
        if (bytes == null) {
            return null
        }

        if ((myAltSendsEscape || alwaysSendEsc(key)) && (modifiers and InputEvent.ALT_MASK) != 0) {
            return insertCodeAt(bytes, CharUtils.makeCode(ESC), 0)
        }

        if ((myMetaSendsEscape || alwaysSendEsc(key)) && (modifiers and InputEvent.META_MASK) != 0) {
            return insertCodeAt(bytes, CharUtils.makeCode(ESC), 0)
        }

        if (isCursorKey(key) || isFunctionKey(key)) {
            return getCodeWithModifiers(bytes, modifiers)
        }

        return bytes
    }

    private fun alwaysSendEsc(key: Int): Boolean {
        return isCursorKey(key) || key == '\b'.code
    }

    private fun isCursorKey(key: Int): Boolean {
        return key == VK_DOWN || key == VK_UP || key == VK_LEFT || key == VK_RIGHT || key == VK_HOME || key == VK_END
    }

    private fun isFunctionKey(key: Int): Boolean {
        return key >= VK_F1 && key <= VK_F12 || key == VK_INSERT || key == VK_DELETE || key == VK_PAGE_UP || key == VK_PAGE_DOWN
    }

    /**
     * Refer to section PC-Style Function Keys in http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
     */
    private fun getCodeWithModifiers(bytes: ByteArray, modifiers: Int): ByteArray {
        val code: Int = modifiersToCode(modifiers)

        if (code > 0 && bytes.size > 2) {
            // SS3 needs to become CSI.
            if (bytes[0].toInt() == ESC && bytes[1] == 'O'.code.toByte()) bytes[1] = '['.code.toByte()
            // If the control sequence has no parameters, it needs a default parameter.
            // Either way it also needs a semicolon separator.
            val prefix = if (bytes.size == 3) "1;" else ";"
            return insertCodeAt(bytes, (prefix + code).toByteArray(), bytes.size - 1)
        }
        return bytes
    }

    fun setAutoNewLine(enabled: Boolean) {
        if (enabled) {
            putCode(VK_ENTER, Ascii.CR.code, Ascii.LF.code)
        } else {
            putCode(VK_ENTER, Ascii.CR.code)
        }
    }

    fun setAltSendsEscape(altSendsEscape: Boolean) {
        myAltSendsEscape = altSendsEscape
    }

    fun setMetaSendsEscape(metaSendsEscape: Boolean) {
        myMetaSendsEscape = metaSendsEscape
    }

    private class KeyCodeAndModifier(private val myCode: Int, private val myModifier: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as KeyCodeAndModifier
            return myCode == that.myCode && myModifier == that.myModifier
        }

        override fun hashCode(): Int {
            return Objects.hash(myCode, myModifier)
        }
    }

    companion object {
        private val ESC = Ascii.ESC.code

        private fun insertCodeAt(bytes: ByteArray, code: ByteArray, at: Int): ByteArray {
            val res = ByteArray(bytes.size + code.size)
            System.arraycopy(bytes, 0, res, 0, bytes.size)
            System.arraycopy(bytes, at, res, at + code.size, bytes.size - at)
            System.arraycopy(code, 0, res, at, code.size)
            return res
        }

        /**
         *
         * Code     Modifiers
         * ------+--------------------------
         * 2     | Shift
         * 3     | Alt
         * 4     | Shift + Alt
         * 5     | Control
         * 6     | Shift + Control
         * 7     | Alt + Control
         * 8     | Shift + Alt + Control
         * 9     | Meta
         * 10    | Meta + Shift
         * 11    | Meta + Alt
         * 12    | Meta + Alt + Shift
         * 13    | Meta + Ctrl
         * 14    | Meta + Ctrl + Shift
         * 15    | Meta + Ctrl + Alt
         * 16    | Meta + Ctrl + Alt + Shift
         * ------+--------------------------
         * @param modifiers
         * @return
         */
        private fun modifiersToCode(modifiers: Int): Int {
            var code = 0
            if ((modifiers and InputEvent.SHIFT_MASK) != 0) {
                code = code or 1
            }
            if ((modifiers and InputEvent.ALT_MASK) != 0) {
                code = code or 2
            }
            if ((modifiers and InputEvent.CTRL_MASK) != 0) {
                code = code or 4
            }
            if ((modifiers and InputEvent.META_MASK) != 0) {
                code = code or 8
            }
            return if (code != 0) code + 1 else code
        }
    }
}
