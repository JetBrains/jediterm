/**
 *
 */
package com.jediterm;

import java.awt.Dimension;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.EnumSet;

import org.apache.log4j.Logger;

public class BackBufferTerminalWriter implements TerminalWriter {
  private static final Logger logger = Logger.getLogger(BackBufferTerminalWriter.class);
  private final int tab = 8;

  private int scrollRegionTop;
  private int scrollRegionBottom;
  private int cursorX = 0;
  private int cursorY = 1;

  private int termWidth = 80;
  private int myTerminalHeight = 24;

  private final TerminalDisplay display;
  private final BackBuffer backBuffer;
  private final StyleState myStyleState;

  private final EnumSet<TerminalMode> modes = EnumSet.of(TerminalMode.ANSI);

  public BackBufferTerminalWriter(final TerminalDisplay term, final BackBuffer buf, final StyleState styleState) {
    this.display = term;
    this.backBuffer = buf;
    this.myStyleState = styleState;

    termWidth = term.getColumnCount();
    myTerminalHeight = term.getRowCount();

    scrollRegionTop = 1;
    scrollRegionBottom = myTerminalHeight;
  }

  public void setMode(TerminalMode mode) {
    modes.add(mode);
    switch (mode) {
      case WideColumn:
        resize(new Dimension(132, 24), RequestOrigin.Remote);
        clearScreen();
        restoreCursor(null);
        break;
    }
  }

  public void unsetMode(TerminalMode mode) {
    modes.remove(mode);
    switch (mode) {
      case WideColumn:
        resize(new Dimension(80, 24), RequestOrigin.Remote);
        clearScreen();
        restoreCursor(null);
        break;
    }
  }

  private void wrapLines() {
    if (cursorX >= termWidth) {
      cursorX = 0;
      cursorY += 1;
    }
  }

  private void finishText() {
    display.setCursor(cursorX, cursorY);
    scrollY();
  }

  public void writeASCII(final byte[] chosenBuffer, final int start,
                         final int length) {
    backBuffer.lock();
    try {
      wrapLines();
      if (length != 0) {
        backBuffer.clearArea(cursorX, cursorY - 1, cursorX + length, cursorY);
        backBuffer.drawBytes(chosenBuffer, start, length, cursorX, cursorY);
      }
      cursorX += length;
      finishText();
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void writeDoubleByte(final byte[] bytesOfChar) throws UnsupportedEncodingException {
    writeString(new String(bytesOfChar, 0, 2, "EUC-JP"));
  }

  public void writeString(String string) {
    backBuffer.lock();
    try {
      wrapLines();
      backBuffer.clearArea(cursorX, cursorY - 1, cursorX + string.length(), cursorY);
      backBuffer.drawString(string, cursorX, cursorY);
      cursorX += string.length();
      finishText();
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void writeUnwrappedString(String string) {
    int length = string.length();
    int off = 0;
    while (off < length) {
      int amountInLine = Math.min(distanceToLineEnd(), length - off);
      writeString(string.substring(off, off + amountInLine));
      wrapLines();
      scrollY();
      off += amountInLine;
    }
  }


  public void scrollY() {
    backBuffer.lock();
    try {
      if (cursorY > scrollRegionBottom) {
        final int dy = scrollRegionBottom - cursorY;
        cursorY = scrollRegionBottom;
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, dy);
        backBuffer.clearArea(0, cursorY - 1, termWidth, cursorY);
        display.setCursor(cursorX, cursorY);
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void newLine() {
    cursorY += 1;
    display.setCursor(cursorX, cursorY);
    scrollY();
  }

  public void backspace() {
    cursorX -= 1;
    if (cursorX < 0) {
      cursorY -= 1;
      cursorX = termWidth - 1;
    }
    display.setCursor(cursorX, cursorY);
  }

  public void carriageReturn() {
    cursorX = 0;
    display.setCursor(cursorX, cursorY);
  }

  public void horizontalTab() {
    cursorX = (cursorX / tab + 1) * tab;
    if (cursorX >= termWidth) {
      cursorX = 0;
      cursorY += 1;
    }
    display.setCursor(cursorX, cursorY);
  }

  public void eraseInDisplay(final int arg) {
    backBuffer.lock();
    try {
      int beginY;
      int endY;

      switch (arg) {
        case 0:
          // Initial line
          if (cursorX < termWidth) {
            backBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
          }
          // Rest
          beginY = cursorY;
          endY = myTerminalHeight;

          break;
        case 1:
          // initial line
          backBuffer.clearArea(0, cursorY - 1, cursorX + 1, cursorY);

          beginY = 0;
          endY = cursorY - 1;
          break;
        case 2:
          beginY = 0;
          endY = myTerminalHeight;
          break;
        default:
          logger.error("Unsupported erase in display mode:" + arg);
          beginY = 1;
          endY = 1;
          break;
      }
      // Rest of lines
      if (beginY != endY) {
        clearLines(beginY, endY);
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void clearLines(final int beginY, final int endY) {
    backBuffer.lock();
    try {
      backBuffer.clearArea(0, beginY, termWidth, endY);
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void clearScreen() {
    clearLines(0, myTerminalHeight);
  }

  public void eraseInLine(int arg) {
    backBuffer.lock();
    try {
      switch (arg) {
        case 0:
          if (cursorX < termWidth) {
            backBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
          }
          break;
        case 1:
          final int extent = Math.min(cursorX + 1, termWidth);
          backBuffer.clearArea(0, cursorY - 1, extent, cursorY);
          break;
        case 2:
          backBuffer.clearArea(0, cursorY - 1, termWidth, cursorY);
          break;
        default:
          logger.error("Unsupported erase in line mode:" + arg);
          break;
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void cursorUp(final int countY) {
    backBuffer.lock();
    try {
      cursorY -= countY;
      cursorY = Math.max(cursorY, 1);
      display.setCursor(cursorX, cursorY);
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void cursorDown(final int dY) {
    backBuffer.lock();
    try {
      cursorY += dY;
      cursorY = Math.min(cursorY, myTerminalHeight);
      display.setCursor(cursorX, cursorY);
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void index() {
    backBuffer.lock();
    try {
      if (cursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        backBuffer.clearArea(0, scrollRegionBottom - 1, termWidth,
                             scrollRegionBottom);
      }
      else {
        cursorY += 1;
        display.setCursor(cursorX, cursorY);
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  // Dodgy ?
  private void scrollArea(int y, int h, int dy) {
    display.scrollArea(y, h, dy);
    backBuffer.scrollArea(y, h, dy);
  }

  public void nextLine() {
    backBuffer.lock();
    try {
      cursorX = 0;
      if (cursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        backBuffer.clearArea(0, scrollRegionBottom - 1, termWidth,
                             scrollRegionBottom);
      }
      else {
        cursorY += 1;
      }
      display.setCursor(cursorX, cursorY);
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void reverseIndex() {
    backBuffer.lock();
    try {
      if (cursorY == 1) {
        scrollArea(scrollRegionTop - 1, scrollRegionBottom
                                        - scrollRegionTop, 1);
        backBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
      }
      else {
        cursorY -= 1;
        display.setCursor(cursorX, cursorY);
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  public void cursorForward(final int dX) {
    cursorX += dX;
    cursorX = Math.min(cursorX, termWidth - 1);
    display.setCursor(cursorX, cursorY);
  }

  public void cursorBackward(final int dX) {
    cursorX -= dX;
    cursorX = Math.max(cursorX, 0);
    display.setCursor(cursorX, cursorY);
  }

  public void cursorPosition(int x, int y) {
    cursorX = x - 1;
    cursorY = y;
    display.setCursor(cursorX, cursorY);
  }

  public void setScrollingRegion(int top, int bottom) {
    scrollRegionTop = top;
    scrollRegionBottom = bottom;
  }

  public void setCharacterAttributes(final StyleState styleState) {
    myStyleState.set(styleState);
  }

  public void beep() {
    display.beep();
  }

  public int distanceToLineEnd() {
    return termWidth - cursorX;
  }

  public void storeCursor(final StoredCursor storedCursor) {
    storedCursor.x = cursorX;
    storedCursor.y = cursorY;
  }

  public void restoreCursor(final StoredCursor storedCursor) {
    cursorX = 0;
    cursorY = 1;
    if (storedCursor != null) {
      // TODO: something with origin modes
      cursorX = storedCursor.x;
      cursorY = storedCursor.y;
    }
    display.setCursor(cursorX, cursorY);
  }

  public Dimension resize(final Dimension pendingResize, final RequestOrigin origin) {
    final int oldHeight = myTerminalHeight;
    final Dimension pixelSize = display.doResize(pendingResize, origin);

    termWidth = display.getColumnCount();
    myTerminalHeight = display.getRowCount();

    scrollRegionBottom += myTerminalHeight - oldHeight;
    cursorY += myTerminalHeight - oldHeight;
    cursorY = Math.max(1, cursorY);
    return pixelSize;
  }

  public void fillScreen(final char c) {
    backBuffer.lock();
    try {
      final char[] chars = new char[termWidth];
      Arrays.fill(chars, c);
      final String str = new String(chars);

      for (int row = 1; row <= myTerminalHeight; row++) {
        backBuffer.drawString(str, 0, row);
      }
    }
    finally {
      backBuffer.unlock();
    }
  }

  @Override
  public int getTerminalHeight() {
    return myTerminalHeight;
  }
}