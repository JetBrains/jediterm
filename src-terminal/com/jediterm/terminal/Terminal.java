package com.jediterm.terminal;

import com.jediterm.terminal.display.StyleState;

import java.awt.*;
import java.io.UnsupportedEncodingException;

/**
 * Executes terminal commands interpreted by {@link Emulator}, receives text
 *
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

  void writeASCII(String string);

  int distanceToLineEnd();

  void reverseIndex();

  void index();

  void nextLine();

  void fillScreen(char c);

  void storeCursor();

  void restoreCursor();

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

  int getCursorX();

  int getCursorY();
}
