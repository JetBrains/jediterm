package com.jediterm.terminal.util

internal val StringBuilder.charArray: CharArray
  get() {
    return CharArray(this.length).also {
      this.getChars(0, this.length, it, 0)
    }
  }
