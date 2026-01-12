package com.jediterm.terminal.emulator

import com.jediterm.terminal.model.getLineTexts
import com.jediterm.util.TestSession
import junit.framework.TestCase
import org.junit.Assert

class SynchronizedOutputTest : TestCase() {

  fun testBasicSynchronizedOutput() {
    val session = TestSession(20, 5)
    session.process("Before" + BEGIN_SYNC_OUTPUT + "Sync" + END_SYNC_OUTPUT + "After")
    assertScreenLines(session, listOf("BeforeSyncAfter"))
  }

  fun testSynchronizedOutputWithNewlines() {
    val session = TestSession(20, 5)
    session.process("Line1\r\n" + BEGIN_SYNC_OUTPUT + "Line2\r\nLine3" + END_SYNC_OUTPUT + "\r\nLine4")
    assertScreenLines(session, listOf("Line1", "Line2", "Line3", "Line4"))
  }

  fun testSynchronizedOutputWithControlSequences() {
    val session = TestSession(20, 5)
    session.process(
      "$BEGIN_SYNC_OUTPUT\u001b[1;1HFirst\u001b[2;1HSecond$END_SYNC_OUTPUT"
    )
    assertScreenLines(session, listOf("First", "Second"))
  }

  fun testMultipleSynchronizedOutputBlocks() {
    val session = TestSession(20, 5)
    session.process("A" + BEGIN_SYNC_OUTPUT + "B" + END_SYNC_OUTPUT + "C" + BEGIN_SYNC_OUTPUT + "D" + END_SYNC_OUTPUT + "E")
    assertScreenLines(session, listOf("ABCDE"))
  }

  fun testEmptySynchronizedOutputBlock() {
    val session = TestSession(20, 5)
    session.process("Before" + BEGIN_SYNC_OUTPUT + END_SYNC_OUTPUT + "After")
    assertScreenLines(session, listOf("BeforeAfter"))
  }

  fun testDoubleBeginCSI() {
    val session = TestSession(20, 5)
    // Process content with duplicated begin sync output sequence
    session.process(BEGIN_SYNC_OUTPUT + "Foo\r\n" + BEGIN_SYNC_OUTPUT + "Bar" + END_SYNC_OUTPUT)
    assertScreenLines(session, listOf("Foo", "Bar"))
  }

  fun testSynchronizedOutputWithCursorMovement() {
    val session = TestSession(20, 5)
    session.process(BEGIN_SYNC_OUTPUT + "Hello" + "\u001b[1;1H" + "X" + END_SYNC_OUTPUT)
    assertScreenLines(session, listOf("Xello"))
  }

  fun testSynchronizedOutputWithColors() {
    val session = TestSession(20, 5)
    session.process("$BEGIN_SYNC_OUTPUT\u001b[31mRed\u001b[0m Normal$END_SYNC_OUTPUT")
    assertScreenLines(session, listOf("Red Normal"))
  }

  fun testSynchronizedOutputWithBackspace() {
    val session = TestSession(20, 5)
    session.process("Foo" + BEGIN_SYNC_OUTPUT + "Bar\b\b\b\b1234" + END_SYNC_OUTPUT)
    assertScreenLines(session, listOf("Fo1234"))
  }

  fun testNoEndSequenceBeforeFinish() {
    val session = TestSession(20, 5)
    session.process("Foo" + BEGIN_SYNC_OUTPUT + "Bar")
    assertScreenLines(session, listOf("FooBar"))
  }

  private fun assertScreenLines(session: TestSession, expectedScreenLines: List<String>) {
    Assert.assertEquals(expectedScreenLines, session.terminalTextBuffer.screenLinesStorage.getLineTexts())
  }

  companion object {
    private const val BEGIN_SYNC_OUTPUT = "\u001b[?2026h"
    private const val END_SYNC_OUTPUT = "\u001b[?2026l"
  }
}
