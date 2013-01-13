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
  private final static int TAB = 8;

  private int scrollRegionTop;
  private int scrollRegionBottom;
  private int cursorX = 0;
  private int cursorY = 1;

  private int termWidth = 80;
  private int myTerminalHeight = 24;

  private final TerminalDisplay myDisplay;
  private final BackBuffer myBackBuffer;
  private final StyleState myStyleState;

  private final EnumSet<TerminalMode> modes = EnumSet.of(TerminalMode.ANSI);

  public BackBufferTerminalWriter(final TerminalDisplay term, final BackBuffer buf, final StyleState styleState) {
    myDisplay = term;
    myBackBuffer = buf;
    myStyleState = styleState;

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
    myDisplay.setCursor(cursorX, cursorY);
    scrollY();
  }

  public void writeASCII(final byte[] chosenBuffer, final int start,
                         final int length) {
    myBackBuffer.lock();
    try {
      wrapLines();
      if (length != 0) {
        myBackBuffer.clearArea(cursorX, cursorY - 1, cursorX + length, cursorY);
        myBackBuffer.drawBytes(cursorX, cursorY, chosenBuffer, start, length);
      }
      cursorX += length;
      finishText();
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void writeDoubleByte(final byte[] bytesOfChar) throws UnsupportedEncodingException {
    writeString(new String(bytesOfChar, 0, 2, "EUC-JP"));
  }

  public void writeString(String string) {
    myBackBuffer.lock();
    try {
      wrapLines();
      myBackBuffer.clearArea(cursorX, cursorY - 1, cursorX + string.length(), cursorY);
      myBackBuffer.drawString(cursorX, cursorY, string);
      cursorX += string.length();
      finishText();
    }
    finally {
      myBackBuffer.unlock();
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
    myBackBuffer.lock();
    try {
      if (cursorY > scrollRegionBottom) {
        final int dy = scrollRegionBottom - cursorY;
        cursorY = scrollRegionBottom;
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, dy);
        myBackBuffer.clearArea(0, cursorY - 1, termWidth, cursorY);
        myDisplay.setCursor(cursorX, cursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void newLine() {
    myBackBuffer.moveToTextBuffer(cursorY);
    cursorY += 1;
    myDisplay.setCursor(cursorX, cursorY);

    scrollY();
  }

  public void backspace() {
    cursorX -= 1;
    if (cursorX < 0) {
      cursorY -= 1;
      cursorX = termWidth - 1;
    }
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void carriageReturn() {
    cursorX = 0;
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void horizontalTab() {
    cursorX = (cursorX / TAB + 1) * TAB;
    if (cursorX >= termWidth) {
      cursorX = 0;
      cursorY += 1;
    }
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void eraseInDisplay(final int arg) {
    myBackBuffer.lock();
    try {
      int beginY;
      int endY;

      switch (arg) {
        case 0:
          // Initial line
          if (cursorX < termWidth) {
            myBackBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
          }
          // Rest
          beginY = cursorY;
          endY = myTerminalHeight;

          break;
        case 1:
          // initial line
          myBackBuffer.clearArea(0, cursorY - 1, cursorX + 1, cursorY);

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
      myBackBuffer.unlock();
    }
  }

  public void clearLines(final int beginY, final int endY) {
    myBackBuffer.lock();
    try {
      myBackBuffer.clearArea(0, beginY, termWidth, endY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void clearScreen() {
    clearLines(0, myTerminalHeight);
  }

  public void eraseInLine(int arg) {
    myBackBuffer.lock();
    try {
      switch (arg) {
        case 0:
          if (cursorX < termWidth) {
            myBackBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
          }
          break;
        case 1:
          final int extent = Math.min(cursorX + 1, termWidth);
          myBackBuffer.clearArea(0, cursorY - 1, extent, cursorY);
          break;
        case 2:
          myBackBuffer.clearArea(0, cursorY - 1, termWidth, cursorY);
          break;
        default:
          logger.error("Unsupported erase in line mode:" + arg);
          break;
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorUp(final int countY) {
    myBackBuffer.lock();
    try {
      cursorY -= countY;
      cursorY = Math.max(cursorY, 1);
      myDisplay.setCursor(cursorX, cursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorDown(final int dY) {
    myBackBuffer.lock();
    try {
      cursorY += dY;
      cursorY = Math.min(cursorY, myTerminalHeight);
      myDisplay.setCursor(cursorX, cursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void index() {
    myBackBuffer.lock();
    try {
      if (cursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        myBackBuffer.clearArea(0, scrollRegionBottom - 1, termWidth,
                               scrollRegionBottom);
      }
      else {
        cursorY += 1;
        myDisplay.setCursor(cursorX, cursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  // Dodgy ?
  private void scrollArea(int y, int h, int dy) {
    myDisplay.scrollArea(y, h, dy);
    myBackBuffer.scrollArea(y, h, dy);
  }

  public void nextLine() {
    myBackBuffer.lock();
    try {
      cursorX = 0;
      if (cursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        myBackBuffer.clearArea(0, scrollRegionBottom - 1, termWidth,
                               scrollRegionBottom);
      }
      else {
        cursorY += 1;
      }
      myDisplay.setCursor(cursorX, cursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void reverseIndex() {
    myBackBuffer.lock();
    try {
      if (cursorY == 1) {
        scrollArea(scrollRegionTop - 1, scrollRegionBottom
                                        - scrollRegionTop, 1);
        myBackBuffer.clearArea(cursorX, cursorY - 1, termWidth, cursorY);
      }
      else {
        cursorY -= 1;
        myDisplay.setCursor(cursorX, cursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorForward(final int dX) {
    cursorX += dX;
    cursorX = Math.min(cursorX, termWidth - 1);
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void cursorBackward(final int dX) {
    cursorX -= dX;
    cursorX = Math.max(cursorX, 0);
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void cursorPosition(int x, int y) {
    cursorX = x - 1;
    cursorY = y;
    myDisplay.setCursor(cursorX, cursorY);
  }

  public void setScrollingRegion(int top, int bottom) {
    scrollRegionTop = top;
    scrollRegionBottom = bottom;
  }

  public void setCharacterAttributes(final StyleState styleState) {
    myStyleState.set(styleState);
  }

  public void beep() {
    myDisplay.beep();
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
    myDisplay.setCursor(cursorX, cursorY);
  }

  public Dimension resize(final Dimension pendingResize, final RequestOrigin origin) {
    final int oldHeight = myTerminalHeight;
    final Dimension pixelSize = myDisplay.doResize(pendingResize, origin);

    termWidth = myDisplay.getColumnCount();
    myTerminalHeight = myDisplay.getRowCount();

    scrollRegionBottom += myTerminalHeight - oldHeight;
    cursorY += myTerminalHeight - oldHeight;
    cursorY = Math.max(1, cursorY);
    return pixelSize;
  }

  public void fillScreen(final char c) {
    myBackBuffer.lock();
    try {
      final char[] chars = new char[termWidth];
      Arrays.fill(chars, c);
      final String str = new String(chars);

      for (int row = 1; row <= myTerminalHeight; row++) {
        myBackBuffer.drawString(0, row, str);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  @Override
  public int getTerminalHeight() {
    return myTerminalHeight;
  }
}