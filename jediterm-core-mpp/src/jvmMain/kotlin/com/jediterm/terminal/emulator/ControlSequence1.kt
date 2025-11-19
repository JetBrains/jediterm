package com.jediterm.terminal.emulator

import com.jediterm.core.util.Ascii
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.util.CharUtils
import java.io.IOException

class ControlSequence internal constructor(channel: TerminalDataStream) {
    var count: Int = 0
        private set

    private var myArgv: IntArray

    var finalChar: Char = 0.toChar()
        private set

    private var myUnhandledChars: ArrayList<Char?>? = null

    private var myIntermediateChars: StringBuilder? = null

    private var myStartsWithExclamationMark = false // true when CSI !
    private var myStartsWithQuestionMark = false // true when CSI ?
    private var myStartsWithMoreMark = false // true when CSI >

    @Suppress("unused")
    private var myStartsWithEqualsMark = false // true when CSI =

    private val mySequenceString = StringBuilder()


    init {
        myArgv = IntArray(5)

        readControlSequence(channel)
    }

    @Throws(IOException::class)
    private fun readControlSequence(channel: TerminalDataStream) {
        this.count = 0
        // Read integer arguments
        var digit = 0
        var seenDigit = 0
        var pos = -1

        while (true) {
            val b = channel.char
            mySequenceString.append(b)
            pos++
            if (b == '!' && pos == 0) {
                myStartsWithExclamationMark = true
            } else if (b == '?' && pos == 0) {
                myStartsWithQuestionMark = true
            } else if (b == '>' && pos == 0) {
                myStartsWithMoreMark = true
            } else if (b == '=' && pos == 0) {
                myStartsWithEqualsMark = true
            } else if (b == ';' || b == ':') {
                // Treat both semicolon and colon as parameter separators for compatibility with modern terminals
                // Semicolon ';' separates parameters, colon ':' separates sub-parameters (ISO-8613-6 / ITU-T Rec. T.416)
                // Many TUI apps (Neovim, vim) use colon format for 256-color: CSI 38:5:n m instead of CSI 38;5;n m
                // Always finish the current parameter when we see a separator (even if it's empty)
                if (digit > 0 || seenDigit > 0) {
                    // We've been parsing parameters, so finish this one
                    this.count++
                }
                // Expand array if needed
                if (this.count >= myArgv.size) {
                    val replacement = IntArray(myArgv.size * 2)
                    System.arraycopy(myArgv, 0, replacement, 0, myArgv.size)
                    myArgv = replacement
                }
                // Initialize next parameter slot to 0 (default for empty parameters)
                myArgv[this.count] = 0
                digit = 0
            } else if ('0' <= b && b <= '9') {
                myArgv[this.count] = myArgv[this.count] * 10 + b.code - '0'.code
                digit++
                seenDigit = 1
            } else if (0x20 <= b.code && b.code <= 0x2F) {
                // Intermediate bytes - valid inside CSI but not parameters.
                addIntermediate(b)
            } else if (';' < b && b <= '?') {
                // Unhandled characters between ';' (0x3B) and '?' (0x3F), excluding ':' (0x3A) which is now handled
                addUnhandled(b)
            } else if (0x40 <= b.code && b.code <= 0x7E) {
                this.finalChar = b
                break
            } else {
                addUnhandled(b)
            }
        }
        this.count += seenDigit
    }

    private fun addUnhandled(b: Char) {
        if (myUnhandledChars == null) {
            myUnhandledChars = ArrayList<Char?>()
        }
        myUnhandledChars?.add(b)
    }

    private fun addIntermediate(b: Char) {
        if (myIntermediateChars == null) {
            myIntermediateChars = StringBuilder()
        }
        myIntermediateChars?.append(b)
    }

    @Throws(IOException::class)
    fun pushBackReordered(channel: TerminalDataStream): Boolean {
        val unhandledChars = myUnhandledChars ?: return false
        val bytes = CharArray(1024) // can't be more than the whole buffer...
        var i = 0
        for (b in unhandledChars) {
            if (b != null) {
                bytes[i++] = b
            }
        }
        bytes[i++] = Char(Ascii.ESC.code.toByte().toUShort())
        bytes[i++] = Char('['.code.toByte().toUShort())

        if (myStartsWithExclamationMark) {
            bytes[i++] = Char('!'.code.toByte().toUShort())
        }
        if (myStartsWithQuestionMark) {
            bytes[i++] = Char('?'.code.toByte().toUShort())
        }

        if (myStartsWithMoreMark) {
            bytes[i++] = Char('>'.code.toByte().toUShort())
        }

        for (argi in 0..<this.count) {
            if (argi != 0) bytes[i++] = ';'
            val s = myArgv[argi].toString()
            for (j in 0..<s.length) {
                bytes[i++] = s.get(j)
            }
        }
        bytes[i++] = this.finalChar
        channel.pushBackBuffer(bytes, i)
        return true
    }

    fun getArg(index: Int, defaultValue: Int): Int {
        if (index >= this.count) {
            return defaultValue
        }
        return myArgv[index]
    }

    private fun appendToBuffer(sb: StringBuilder) {
        sb.append("ESC[")

        if (myStartsWithExclamationMark) {
            sb.append("!")
        }
        if (myStartsWithQuestionMark) {
            sb.append("?")
        }
        if (myStartsWithMoreMark) {
            sb.append(">")
        }

        var sep = ""
        for (i in 0..<this.count) {
            sb.append(sep)
            sb.append(myArgv[i])
            sep = ";"
        }
        if (myIntermediateChars != null) {
            sb.append(myIntermediateChars)
        }
        sb.append(this.finalChar)

        myUnhandledChars?.let { unhandled ->
            sb.append(" Unhandled:")
            var last: CharUtils.CharacterType? = CharUtils.CharacterType.NONE
            for (b in unhandled) {
                if (b != null) {
                    last = CharUtils.appendChar(sb, last, b)
                }
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        appendToBuffer(sb)
        return sb.toString()
    }

    fun startsWithExclamationMark(): Boolean {
        return myStartsWithExclamationMark
    }

    fun startsWithQuestionMark(): Boolean {
        return myStartsWithQuestionMark
    }

    fun startsWithMoreMark(): Boolean {
        return myStartsWithMoreMark
    }

    val debugInfo: String
        get() {
            val sb = StringBuilder()
            sb.append("parsed: ")
            appendToBuffer(sb)
            sb.append(", raw: ESC[")
            sb.append(mySequenceString)
            return sb.toString()
        }
}