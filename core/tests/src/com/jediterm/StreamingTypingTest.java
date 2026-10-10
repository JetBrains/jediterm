package com.jediterm;

import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;

/** Exercises the widget's refillable stream, rather than treating every read as EOF. */
public class StreamingTypingTest extends TestCase {
  public void testTypingAcrossEveryEscapeSequenceSplit() throws Exception {
    String[] streams = {
      "blah blah",
      "bl\u001bPzz\u001b\\ah blah",
      "\u001b[3;1H\u001bPzz\u001b\\\u001b[0%m\u001b[6n\u001b[1;1Hblah blah",
      "bla\u001b[1;3Ha\u001b[1;4Hh blah",
      "\u001b[?2026hbl\u001bPzz\u001b\\ah blah\u001b[?2026l"
    };
    for (String text : streams) {
      for (int split = 1; split < text.length(); split++) {
        assertTyping(text, new ChunkedConnector(text, split, text.length()));
      }
      assertTyping(text, new ChunkedConnector(text, 1, 1));
    }
  }

  private void assertTyping(String text, TtyConnector connector) throws Exception {
    TestSession session = new TestSession(24, 8);
    JediEmulator emulator = new JediEmulator(new TtyBasedArrayDataStream(connector), session.getTerminal());
    while (emulator.hasNext()) emulator.next();
    assertEquals(text, "blah blah", session.getTerminalTextBuffer().getScreenLines().trim());
    session.assertCursorPosition(10, 1);
  }

  private static final class ChunkedConnector implements TtyConnector {
    private final String text;
    private final int split;
    private final int chunkSize;
    private int offset;

    ChunkedConnector(String text, int split, int chunkSize) {
      this.text = text;
      this.split = split;
      this.chunkSize = chunkSize;
    }

    @Override
    public int read(char[] buf, int off, int len) {
      if (offset == text.length()) return -1;
      int boundary = offset < split ? split : text.length();
      int count = Math.min(Math.min(len, chunkSize), boundary - offset);
      text.getChars(offset, offset + count, buf, off);
      offset += count;
      return count;
    }

    @Override public void write(byte[] bytes) { }
    @Override public void write(String string) { }
    @Override public boolean isConnected() { return offset < text.length(); }
    @Override public int waitFor() { return 0; }
    @Override public boolean ready() { return isConnected(); }
    @Override public String getName() { return "offline-chunked-typing"; }
    @Override public void close() { }
  }
}
