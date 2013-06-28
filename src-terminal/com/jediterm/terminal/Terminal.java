package com.jediterm.terminal;

import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.emulator.TermCharset;

import java.awt.*;
import java.io.UnsupportedEncodingException;

/**
 * Executes terminal commands interpreted by {@link com.jediterm.terminal.emulator.Emulator}, receives text
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

  void invokeCharacterSet(int num);

  void designateCharacterSet(int tableNumber, TermCharset ch);

  void writeDoubleByte(char[] bytes) throws UnsupportedEncodingException;

  void writeCharacters(char[] buf, int offset, int len);

  void writeCharacters(String string);

  int distanceToLineEnd();

  void reverseIndex();

  void index();

  void nextLine();

  void fillScreen(char c);

  void storeCursor();

  void restoreCursor();

  void reset();

  void characterAttributes(StyleState styleState);

  void setScrollingRegion(int top, int bottom);

  void cursorHorizontalAbsolute(int x);

  void linePositionAbsolute(int y);
  
  void cursorPosition(int x, int y);

  void cursorUp(int countY);

  void cursorDown(int dY);

  void cursorForward(int dX);

  void cursorBackward(int dX);

  void eraseInLine(int arg);

  void deleteCharacters(int count);

  int getTerminalHeight();

  void eraseInDisplay(int arg);

  void setModeEnabled(TerminalMode mode, boolean enabled);
  
  void disconnected();

  int getCursorX();

  int getCursorY();

  void singleShiftSelect(int num);

  void setWindowTitle(String name);

  void clearScreen();

  void setCursorVisible(boolean visible);

  void useAlternateBuffer(boolean enabled);

  byte[] getCodeForKey(int key);

  void setApplicationArrowKeys(boolean enabled);

  void setApplicationKeypad(boolean enabled);

  StyleState getStyleState();

  void insertLines(int num);

  void setBlinkingCursor(boolean enabled);
}
