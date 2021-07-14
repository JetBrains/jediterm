package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class TypeAheadTerminalDataStream implements TerminalDataStream {
  private final TerminalDataStream myDelegate;
  private final StringBuilder myRecordedReadChars = new StringBuilder();
  private boolean myIsRecordingReadChars;

  public TypeAheadTerminalDataStream(@NotNull TerminalDataStream delegate) {
    myDelegate = delegate;
  }

  @Override
  public char getChar() throws IOException {
    char ch = myDelegate.getChar();
    if (myIsRecordingReadChars) {
      myRecordedReadChars.append(ch);
    }
    return ch;
  }

  @Override
  public void pushChar(char c) throws IOException {
    myDelegate.pushChar(c);
    if (myIsRecordingReadChars) {
      if (myRecordedReadChars.length() == 0) {
        throw new IllegalStateException("Pushing back " + c + ", but nothing recorded");
      }
      char lastChar = myRecordedReadChars.charAt(myRecordedReadChars.length() - 1);
      if (lastChar != c) {
        throw new IllegalStateException("Not matched: pushing back " + c + ", last recorded: " + lastChar);
      }
      myRecordedReadChars.deleteCharAt(myRecordedReadChars.length() - 1);
    }
  }

  @Override
  public String readNonControlCharacters(int maxChars) throws IOException {
    String nonControlCharacters = myDelegate.readNonControlCharacters(maxChars);
    if (myIsRecordingReadChars) {
      myRecordedReadChars.append(nonControlCharacters);
    }
    return nonControlCharacters;
  }

  @Override
  public void pushBackBuffer(char[] bytes, int length) throws IOException {
    myDelegate.pushBackBuffer(bytes, length);
  }

  @Override
  public boolean isEmpty() {
    return myDelegate.isEmpty();
  }

  public void startRecordingReadChars() {
    myIsRecordingReadChars = true;
  }

  public @NotNull String stopRecodingReadCharsAndGet() {
    myIsRecordingReadChars = false;
    String recordedCharacters = myRecordedReadChars.toString();
    myRecordedReadChars.setLength(0);
    return recordedCharacters;
  }
}
