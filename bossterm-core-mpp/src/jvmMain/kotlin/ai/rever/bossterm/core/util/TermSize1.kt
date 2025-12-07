package ai.rever.bossterm.core.util

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TermSize
        return this.columns == that.columns && this.rows == that.rows
    }

    override fun hashCode(): Int {
        return Objects.hash(this.columns, this.rows)
    }

    override fun toString(): String {
        return "columns=" + this.columns + ", rows=" + this.rows
    }
}
