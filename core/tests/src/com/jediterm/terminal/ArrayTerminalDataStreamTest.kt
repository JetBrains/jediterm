package com.jediterm.terminal

import junit.framework.TestCase
import java.io.IOException

class ArrayTerminalDataStreamTest : TestCase() {

  fun testPushBackBufferBasic() {
    val stream = ArrayTerminalDataStream("Hello".toCharArray())
    assertEquals("Hel", read(stream, 3))
    stream.pushBackBuffer("XX".toCharArray(), 2)
    assertEquals("XXlo", readAll(stream))
  }

  fun testPushBackBufferWithSufficientSpace() {
    val buffer = CharArray(10)
    System.arraycopy("ABC".toCharArray(), 0, buffer, 5, 3)
    val stream = ArrayTerminalDataStream(buffer, 5, 3)
    assertEquals('A', stream.getChar())
    stream.pushBackBuffer("XY".toCharArray(), 2)
    assertEquals("XYBC", readAll(stream))
  }

  fun testPushBackBufferWithExpansion() {
    val stream = ArrayTerminalDataStream("AB".toCharArray())

    assertEquals("AB", readAll(stream))

    stream.pushBackBuffer("XYZW".toCharArray(), 4)
    assertFalse(stream.isEmpty)
    assertEquals("XYZW", readAll(stream))
  }

  fun testPushBackBufferWithShifting() {
    val stream = ArrayTerminalDataStream("Hello".toCharArray())

    // Read one char to create small offset
    assertEquals('H', stream.getChar())

    stream.pushBackBuffer("ABC".toCharArray(), 3)
    assertEquals("ABCello", readAll(stream))
  }

  fun testMultiplePushBackBuffer() {
    val stream = ArrayTerminalDataStream("Hello".toCharArray())
    assertEquals("He", read(stream, 2))
    stream.pushBackBuffer("12".toCharArray(), 2)
    stream.pushBackBuffer("AB".toCharArray(), 2)
    assertEquals("AB12llo", readAll(stream))
  }

  fun testPushBackBufferOnEmptyStream() {
    val stream = ArrayTerminalDataStream("AB".toCharArray())
    assertEquals("AB", readAll(stream))
    stream.pushBackBuffer("XYZ".toCharArray(), 3)
    assertEquals("XYZ", readAll(stream))
  }

  fun testPushBackBufferPartialArray() {
    val stream = ArrayTerminalDataStream("Hello".toCharArray())
    assertEquals('H', stream.getChar())
    val buffer = "ABCDE".toCharArray()
    stream.pushBackBuffer(buffer, 2)
    assertEquals("ABello", readAll(stream))
  }

  fun testPushBackBufferSingleChar() {
    val stream = ArrayTerminalDataStream("ABC".toCharArray())

    assertEquals('A', stream.getChar())

    stream.pushBackBuffer(charArrayOf('X'), 1)

    assertEquals("XBC", readAll(stream))
  }

  fun testPushBackBufferLargeBuffer() {
    val stream = ArrayTerminalDataStream("ABC".toCharArray())

    assertEquals("AB", read(stream, 2))

    val largeBuffer = CharArray(100)
    for (i in 0..99) {
      largeBuffer[i] = '0' + (i % 10)
    }
    stream.pushBackBuffer(largeBuffer, 100)

    for (i in 0..99) {
      assertEquals('0' + (i % 10), stream.getChar())
    }

    assertEquals("C", readAll(stream))
  }

  fun testPushBackBufferOrder() {
    val stream = ArrayTerminalDataStream("Original".toCharArray())
    assertEquals("Orig", read(stream, 4))
    stream.pushBackBuffer("1234".toCharArray(), 4)
    assertEquals("1234inal", readAll(stream))
  }

  fun testPushBackBufferAfterEOF() {
    val stream = ArrayTerminalDataStream("Hi".toCharArray())

    assertEquals('H', stream.getChar())
    assertEquals('i', stream.getChar())

    try {
      stream.getChar()
      fail("Expected EOF exception")
    } catch (_: TerminalDataStream.EOF) {
      // Expected
    }

    // Push back after EOF
    stream.pushBackBuffer("New".toCharArray(), 3)

    assertEquals("New", readAll(stream))
  }

  fun testPushBackBufferZeroLength() {
    val stream = ArrayTerminalDataStream("ABC".toCharArray())

    assertEquals('A', stream.getChar())

    // Push back zero chars
    stream.pushBackBuffer("XYZ".toCharArray(), 0)

    assertEquals("BC", readAll(stream))
  }

  fun testPushBackBufferAndIsEmpty() {
    val stream = ArrayTerminalDataStream("AB".toCharArray())
    assertFalse(stream.isEmpty)
    assertEquals("AB", readAll(stream))
    stream.pushBackBuffer("XY".toCharArray(), 2)
    assertEquals("XY", readAll(stream))
  }

  @Throws(IOException::class)
  private fun readAll(stream: ArrayTerminalDataStream): String {
    val sb = StringBuilder()
    while (!stream.isEmpty) {
      sb.append(stream.getChar())
    }
    return sb.toString()
  }

  @Throws(IOException::class)
  private fun read(stream: ArrayTerminalDataStream, limit: Int): String {
    check(limit >= 0)
    val sb = StringBuilder()
    while (!stream.isEmpty) {
      if (sb.length >= limit) {
        return sb.toString()
      }
      sb.append(stream.getChar())
    }
    return sb.toString()
  }
}
