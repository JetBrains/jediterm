package com.jediterm.core.util

import java.util.*

class TermSize(columns: Int, rows: Int) {
    val columns: Int
    val rows: Int

    init {
        require(columns >= 0) { "negative column count: " + columns }
        require(rows >= 0) { "negative row count: " + rows }
        this.columns = columns
        this.rows = rows
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val other = o as TermSize
        return this.columns == other.columns && this.rows == other.rows
    }

    override fun hashCode(): Int {
        return Objects.hash(this.columns, this.rows)
    }

    override fun toString(): String {
        return "columns=" + this.columns + ", rows=" + this.rows
    }
}
