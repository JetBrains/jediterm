package com.jediterm.terminal.model

import junit.framework.TestCase

class LimitedSizeLinesStorageTest : TestCase() {
  private val lines = listOf(
    terminalLine("line1"),
    terminalLine("line2"),
    terminalLine("line3"),
  )

  fun `test adding to bottom without overflow`() {
    val storage = createStorage(5)
    for (line in lines) {
      storage.addToBottom(line)
    }

    assertEquals(3, storage.size)
    for ((ind, line) in lines.withIndex()) {
      assertEquals(line, storage[ind])
    }
  }

  fun `test adding to bottom with overflow`() {
    val storage = createStorage(2)
    for (line in lines) {
      storage.addToBottom(line)
    }

    assertEquals(2, storage.size)
    assertEquals(lines[1], storage[0])
    assertEquals(lines[2], storage[1])
  }

  fun `test adding to top without overflow`() {
    val storage = createStorage(5)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val newLine = terminalLine("newLine")
    storage.addToTop(newLine)

    assertEquals(4, storage.size)
    assertEquals(newLine, storage[0])
    for ((ind, line) in lines.withIndex()) {
      assertEquals(line, storage[ind + 1])
    }
  }

  fun `test adding to top when storage is full`() {
    val storage = createStorage(2)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val newLine = terminalLine("newLine")
    storage.addToTop(newLine) // It should not be added

    assertEquals(2, storage.size)
    assertEquals(lines[1], storage[0])
    assertEquals(lines[2], storage[1])
  }

  fun `test remove from bottom`() {
    val storage = createStorage(5)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val line = storage.removeFromBottom()
    assertEquals(lines[2], line)

    assertEquals(2, storage.size)
    assertEquals(lines[0], storage[0])
    assertEquals(lines[1], storage[1])
  }

  fun `test remove from bottom when storage is full`() {
    val storage = createStorage(3)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val line = storage.removeFromBottom()
    assertEquals(lines[2], line)

    assertEquals(2, storage.size)
    assertEquals(lines[0], storage[0])
    assertEquals(lines[1], storage[1])
  }

  fun `test remove from bottom after overflow`() {
    val storage = createStorage(2)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val line = storage.removeFromBottom()
    assertEquals(lines[2], line)

    assertEquals(1, storage.size)
    assertEquals(lines[1], storage[0])
  }

  fun `test remove all lines from bottom`() {
    val storage = createStorage(3)
    for (line in lines) {
      storage.addToBottom(line)
    }

    assertEquals(lines[2], storage.removeFromBottom())
    assertEquals(lines[1], storage.removeFromBottom())
    assertEquals(lines[0], storage.removeFromBottom())

    assertEquals(0, storage.size)
  }

  fun `test remove from bottom and add new one`() {
    val storage = createStorage(2)
    for (line in lines) {
      storage.addToBottom(line)
    }

    storage.removeFromBottom()
    val newLine = terminalLine("new line")
    storage.addToBottom(newLine)

    assertEquals(2, storage.size)
    assertEquals(lines[1], storage[0])
    assertEquals(newLine, storage[1])
  }

  fun `test remove from top without overflow`() {
    val storage = createStorage(5)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val line = storage.removeFromTop()
    assertEquals(lines[0], line)

    assertEquals(2, storage.size)
    assertEquals(lines[1], storage[0])
    assertEquals(lines[2], storage[1])
  }

  fun `test remove from top after overflow`() {
    val storage = createStorage(2)
    for (line in lines) {
      storage.addToBottom(line)
    }

    val line = storage.removeFromTop()
    assertEquals(lines[1], line)

    assertEquals(1, storage.size)
    assertEquals(lines[2], storage[0])
  }

  fun `test clear lines`() {
    val storage = createStorage(5)
    for (line in lines) {
      storage.addToBottom(line)
    }

    storage.clear()
    assertEquals(0, storage.size)
  }
  
  private fun createStorage(maxSize: Int): LinesStorage {
    return CyclicBufferLinesStorage(maxSize)
  }
}
