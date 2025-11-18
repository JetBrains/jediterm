package com.jediterm.terminal.model

class SubCharBuffer(val parent: CharBuffer, val offset: Int, length: Int) : CharBuffer(
    parent.buf, parent.start + offset, length
)
