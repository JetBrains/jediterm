package com.jediterm.util

import com.jediterm.terminal.util.DoubleWidthProvider
import com.jediterm.terminal.util.Icu4jProvider
import org.junit.Assert
import org.junit.Test

class DoubleWidthTest {

  val provider: DoubleWidthProvider = Icu4jProvider()

  @Test
  fun name() {
    // \uD83D
    // \uDE01
    //assertDoubleWidth("\u1F601")
    //assertDoubleWidth("😁")
  }

  private fun assertDoubleWidth(str: String) {
    Assert.assertTrue(str.length == 1)
    val codePoint = str[0].code
    Assert.assertTrue(provider.isDoubleWidth(codePoint, false))
  }
}