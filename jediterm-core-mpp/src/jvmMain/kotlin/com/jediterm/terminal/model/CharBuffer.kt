package com.jediterm.terminal.model

import com.jediterm.terminal.util.CharUtils
import java.util.*

/**
 *
 * @author traff
 */
open class CharBuffer(buf: CharArray, start: Int, length: Int) : Iterable<Char?>, CharSequence {
    val buf: CharArray
    val start: Int
    private val myLength: Int

    override val length: Int
        get() = myLength

    init {
        require(start + length <= buf.size) { String.format("Out ouf bounds %d+%d>%d", start, length, buf.size) }
        this.buf = buf
        this.start = start
        myLength = length

        check(myLength >= 0) { "Length can't be negative: " + myLength }

        check(this.start >= 0) { "Start position can't be negative: " + this.start }

        check(this.start + myLength <= buf.size) {
            String.format(
                "Interval is out of array bounds: %d+%d>%d",
                this.start, myLength, buf.size
            )
        }
    }

    constructor(c: Char, count: Int) : this(CharArray(count), 0, count) {
        assert(!CharUtils.isDoubleWidthCharacter(c.code, false))
        Arrays.fill(this.buf, c)
    }

    constructor(str: String) : this(str.toCharArray(), 0, str.length)

    override fun iterator(): MutableIterator<Char?> {
        return object : MutableIterator<Char?> {
            private var myCurPosition: Int = start

            override fun hasNext(): Boolean {
                return myCurPosition < buf.size && myCurPosition < start + myLength
            }

            override fun next(): Char {
                return buf[myCurPosition++]
            }

            override fun remove() {
                throw IllegalStateException("Can't remove from buffer")
            }
        }
    }

    fun subBuffer(start: Int, length: Int): CharBuffer {
        return CharBuffer(this.buf, this.start + start, length)
    }

    fun subBuffer(range: Pair<Int?, Int?>): CharBuffer {
        val first = range.first ?: 0
        val second = range.second ?: 0
        return CharBuffer(this.buf, this.start + first, second - first)
    }

    val isNul: Boolean
        get() = myLength > 0 && this.buf[0] == CharUtils.NUL_CHAR

    fun unNullify() {
        Arrays.fill(this.buf, CharUtils.EMPTY_CHAR)
    }

    override fun get(index: Int): Char {
        return this.buf[this.start + index]
    }

    override fun subSequence(start: Int, end: Int): CharSequence {
        return CharBuffer(this.buf, this.start + start, end - start)
    }

    override fun toString(): String {
        return String(this.buf, this.start, myLength)
    }

    fun clone(): CharBuffer {
        val newBuf = Arrays.copyOfRange(this.buf, this.start, this.start + myLength)

        return CharBuffer(newBuf, 0, myLength)
    }

    companion object {
        val EMPTY: CharBuffer = CharBuffer(CharArray(0), 0, 0)
    }
}
