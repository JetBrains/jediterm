package com.jediterm.emulator;

import com.jediterm.emulator.display.StoredCursor;
import com.jediterm.emulator.display.StyleState;

import java.awt.*;
import java.io.UnsupportedEncodingException;

/**
 * @author traff
 */
public interface Terminal {
  Dimension resize(Dimension dimension, RequestOrigin origin);

  void beep();

  void backspace();

  void horizontalTab();

  void carriageReturn();

  void newLine();

  void writeDoubleByte(char[] bytes) throws UnsupportedEncodingException;

  void writeASCII(char[] buf, int offset, int len);

  int distanceToLineEnd();

  void reverseIndex();

  void index();

  void nextLine();

  void fillScreen(char c);

  void storeCursor(StoredCursor cursor);

  void restoreCursor(StoredCursor cursor);

  void setCharacterAttributes(StyleState styleState);

  void setScrollingRegion(int top, int bottom);

  void cursorPosition(int x, int y);

  void cursorUp(int countY);

  void cursorDown(int dY);

  void cursorForward(int dX);

  void cursorBackward(int dX);

  void eraseInLine(int arg);

  int getTerminalHeight();

  void eraseInDisplay(int arg);

  void setMode(TerminalMode mode);

  void unsetMode(TerminalMode mode);

  void disconnected();
}
