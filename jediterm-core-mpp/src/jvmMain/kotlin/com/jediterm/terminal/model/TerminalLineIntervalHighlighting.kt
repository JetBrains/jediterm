package com.jediterm.terminal.model

import com.jediterm.terminal.TextStyle

abstract class TerminalLineIntervalHighlighting internal constructor(
    line: TerminalLine,
    startOffset: Int,
    length: Int,
    style: TextStyle
) {
    val line: TerminalLine
    val startOffset: Int
    val endOffset: Int
    private val myStyle: TextStyle
    var isDisposed: Boolean = false
        private set

    init {
        require(startOffset >= 0) { "Negative startOffset: " + startOffset }
        require(length >= 0) { "Negative length: " + length }
        this.line = line
        this.startOffset = startOffset
        this.endOffset = startOffset + length
        myStyle = style
    }

    val length: Int
        get() = this.endOffset - this.startOffset

    fun dispose() {
        doDispose()
        this.isDisposed = true
    }

    protected abstract fun doDispose()

    fun intersectsWith(otherStartOffset: Int, otherEndOffset: Int): Boolean {
        return !(this.endOffset <= otherStartOffset || otherEndOffset <= this.startOffset)
    }

    fun mergeWith(style: TextStyle): TextStyle {
        var foreground = myStyle.foreground
        if (foreground == null) {
            foreground = style.foreground
        }
        var background = myStyle.background
        if (background == null) {
            background = style.background
        }
        return TextStyle(foreground, background)
    }

    override fun toString(): String {
        return "startOffset=" + this.startOffset +
                ", endOffset=" + this.endOffset +
                ", disposed=" + this.isDisposed
    }
}
