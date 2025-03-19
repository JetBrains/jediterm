package com.jediterm.terminal.util

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

class NativeDoubleWidthProvider: DoubleWidthProvider {

  private val libc: CLibraryNative = Native.load(Platform.C_LIBRARY_NAME, CLibraryNative::class.java)
  
  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    return libc.wcwidth(codePoint.toChar()) == 2
  }

  override val name: String
    get() = "native wcwidth()"
}

private interface CLibraryNative : Library {
  // https://man7.org/linux/man-pages/man3/wcwidth.3.html
  fun wcwidth(wc: Char): Int
}
