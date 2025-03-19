package com.jediterm.terminal.util

interface DoubleWidthProvider {

  fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean

  val name: String
}