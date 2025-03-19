package com.jediterm.terminal.util

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UProperty

class Icu4jProvider : DoubleWidthProvider {

  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    val eastAsianWidth = UCharacter.getIntPropertyValue(codePoint, UProperty.EAST_ASIAN_WIDTH)
    return eastAsianWidth == UCharacter.EastAsianWidth.WIDE ||
      eastAsianWidth == UCharacter.EastAsianWidth.FULLWIDTH
  }

  override val name: String
    get() = "ICU4J"
}
