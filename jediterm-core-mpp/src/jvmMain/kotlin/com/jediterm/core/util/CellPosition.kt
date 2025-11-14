package com.jediterm.core.util

class CellPosition(/* one-based column */ val x: Int,
                   /* one-based row    */ val y: Int) {

  init {
    require(x >= 1) { "Positive column is expected, got $x" }
    require(y >= 1) { "Positive row is expected, got $y" }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CellPosition

    return x == other.x && y == other.y
  }

  override fun hashCode(): Int {
    return 31 * x + y
  }

  override fun toString(): String {
    return "column=$x, row=$y"
  }
}
