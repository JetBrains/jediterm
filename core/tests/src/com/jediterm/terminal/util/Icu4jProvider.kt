package com.jediterm.terminal.util

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UProperty

/**
 * Width source: ICU4J's Unicode `East_Asian_Width` property.
 *
 * Used as a maintained, authoritative reference to validate [WidecharwidthProvider] in
 * [DoubleWidthProviderComparisonTest]. Kept in the test source set so ICU4J stays a test-only
 * dependency and never ships in `core`.
 */
internal class Icu4jProvider : DoubleWidthProvider {

  override fun isDoubleWidth(codePoint: Int, areAmbiguousCharactersDoubleWidth: Boolean): Boolean {
    return when (UCharacter.getIntPropertyValue(codePoint, UProperty.EAST_ASIAN_WIDTH)) {
      UCharacter.EastAsianWidth.WIDE, UCharacter.EastAsianWidth.FULLWIDTH -> true
      UCharacter.EastAsianWidth.AMBIGUOUS -> areAmbiguousCharactersDoubleWidth
      else -> false
    }
  }

  override val name: String
    get() = "ICU4J"
}
