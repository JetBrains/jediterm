package com.jediterm.terminal.util

class JediTermDoubleWidthProvider : DoubleWidthProvider {
  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    return CharUtils.isDoubleWidthCharacter(codePoint, areAmbiguousCharactersDoubleWidth)
  }

  override val name: String
    get() = "JediTerm"
}
