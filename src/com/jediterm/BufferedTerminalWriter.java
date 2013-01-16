/**
 *
 */
package com.jediterm;

import java.awt.Dimension;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.EnumSet;

import org.apache.log4j.Logger;

public class BufferedTerminalWriter implements TerminalWriter {
  private static final Logger logger = Logger.getLogger(BufferedTerminalWriter.class);
  private final static int TAB = 8;

  private int scrollRegionTop;
  private int scrollRegionBottom;
  volatile private int cursorX = 0;
  volatile private int myCursorY = 1;

  private int myTerminalWidth = 80;
  private int myTerminalHeight = 24;

  private final TerminalDisplay myDisplay;
  private final BackBuffer myBackBuffer;
  private final StyleState myStyleState;

  private final EnumSet<TerminalMode> modes = EnumSet.of(TerminalMode.ANSI);

  public BufferedTerminalWriter(final TerminalDisplay term, final BackBuffer buf, final StyleState styleState) {
    myDisplay = term;
    myBackBuffer = buf;
    myStyleState = styleState;

    myTerminalWidth = term.getColumnCount();
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

  @Override
  public void disconnected() {
    myDisplay.setShouldDrawCursor(false);
  }

  private void wrapLines() {
    if (cursorX >= myTerminalWidth) {
      cursorX = 0;
      moveLine();
    }
  }

  private void finishText() {
    myDisplay.setCursor(cursorX, myCursorY);
    scrollY();
  }

  public void writeASCII(final char[] chosenBuffer, final int start,
                         final int length) {
    myBackBuffer.lock();
    try {
      wrapLines();
      if (length != 0) {
        myBackBuffer.clearArea(cursorX, myCursorY - 1, cursorX + length, myCursorY);
        myBackBuffer.drawBytes(cursorX, myCursorY, chosenBuffer, start, length);
      }
      cursorX += length;
      finishText();
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void writeDoubleByte(final char[] bytesOfChar) throws UnsupportedEncodingException {
    writeString(new String(bytesOfChar, 0, 2));
  }

  public void writeString(String string) {
    myBackBuffer.lock();
    try {
      wrapLines();
      myBackBuffer.clearArea(cursorX, myCursorY - 1, cursorX + string.length(), myCursorY);
      myBackBuffer.drawString(cursorX, myCursorY, string);
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
      if (myCursorY > scrollRegionBottom) {
        final int dy = scrollRegionBottom - myCursorY;
        myCursorY = scrollRegionBottom;
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, dy);
        myBackBuffer.clearArea(0, myCursorY - 1, myTerminalWidth, myCursorY);
        myDisplay.setCursor(cursorX, myCursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void newLine() {
    moveLine();
    myDisplay.setCursor(cursorX, myCursorY);

    scrollY();
  }

  private void moveLine() {
    if (myCursorY < myTerminalHeight) {
      myBackBuffer.moveToTextBuffer(myCursorY);
    }
    myCursorY += 1;
  }

  public void backspace() {
    cursorX -= 1;
    if (cursorX < 0) {
      myCursorY -= 1;
      cursorX = myTerminalWidth - 1;
    }
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public void carriageReturn() {
    cursorX = 0;
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public void horizontalTab() {
    cursorX = (cursorX / TAB + 1) * TAB;
    if (cursorX >= myTerminalWidth) {
      cursorX = 0;
      myCursorY += 1;
    }
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public void eraseInDisplay(final int arg) {
    myBackBuffer.lock();
    try {
      int beginY;
      int endY;

      switch (arg) {
        case 0:
          // Initial line
          if (cursorX < myTerminalWidth) {
            myBackBuffer.clearArea(cursorX, myCursorY - 1, myTerminalWidth, myCursorY);
          }
          // Rest
          beginY = myCursorY;
          endY = myTerminalHeight;

          break;
        case 1:
          // initial line
          myBackBuffer.clearArea(0, myCursorY - 1, cursorX + 1, myCursorY);

          beginY = 0;
          endY = myCursorY - 1;
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
      myBackBuffer.clearLines(beginY, endY);
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
          if (cursorX < myTerminalWidth) {
            myBackBuffer.clearArea(cursorX, myCursorY - 1, myTerminalWidth, myCursorY);
          }
          break;
        case 1:
          final int extent = Math.min(cursorX + 1, myTerminalWidth);
          myBackBuffer.clearArea(0, myCursorY - 1, extent, myCursorY);
          break;
        case 2:
          myBackBuffer.clearArea(0, myCursorY - 1, myTerminalWidth, myCursorY);
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
      myCursorY -= countY;
      myCursorY = Math.max(myCursorY, 1);
      myDisplay.setCursor(cursorX, myCursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorDown(final int dY) {
    myBackBuffer.lock();
    try {
      myCursorY += dY;
      myCursorY = Math.min(myCursorY, myTerminalHeight);
      myDisplay.setCursor(cursorX, myCursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void index() {
    myBackBuffer.lock();
    try {
      if (myCursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        myBackBuffer.clearArea(0, scrollRegionBottom - 1, myTerminalWidth,
                               scrollRegionBottom);
      }
      else {
        myCursorY += 1;
        myDisplay.setCursor(cursorX, myCursorY);
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
      if (myCursorY == myTerminalHeight) {
        scrollArea(scrollRegionTop, scrollRegionBottom
                                    - scrollRegionTop, -1);
        myBackBuffer.clearArea(0, scrollRegionBottom - 1, myTerminalWidth,
                               scrollRegionBottom);
      }
      else {
        myCursorY += 1;
      }
      myDisplay.setCursor(cursorX, myCursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void reverseIndex() {
    myBackBuffer.lock();
    try {
      if (myCursorY == 1) {
        scrollArea(scrollRegionTop - 1, scrollRegionBottom
                                        - scrollRegionTop, 1);
        myBackBuffer.clearArea(cursorX, myCursorY - 1, myTerminalWidth, myCursorY);
      }
      else {
        myCursorY -= 1;
        myDisplay.setCursor(cursorX, myCursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorForward(final int dX) {
    cursorX += dX;
    cursorX = Math.min(cursorX, myTerminalWidth - 1);
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public void cursorBackward(final int dX) {
    cursorX -= dX;
    cursorX = Math.max(cursorX, 0);
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public void cursorPosition(int x, int y) {
    cursorX = x - 1;
    myCursorY = y;
    myDisplay.setCursor(cursorX, myCursorY);
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
    return myTerminalWidth - cursorX;
  }

  public void storeCursor(final StoredCursor storedCursor) {
    storedCursor.x = cursorX;
    storedCursor.y = myCursorY;
  }

  public void restoreCursor(final StoredCursor storedCursor) {
    cursorX = 0;
    myCursorY = 1;
    if (storedCursor != null) {
      // TODO: something with origin modes
      cursorX = storedCursor.x;
      myCursorY = storedCursor.y;
    }
    myDisplay.setCursor(cursorX, myCursorY);
  }

  public interface ResizeHandler {
    void sizeUpdated(int termWidth, int termHeight, int cursorY);
  }

  public Dimension resize(final Dimension pendingResize, final RequestOrigin origin) {
    final int oldHeight = myTerminalHeight;
    if (pendingResize.width <= cursorX + 1) {
      pendingResize.setSize(cursorX + 2, pendingResize.height);
    }
    final Dimension pixelSize = myDisplay.requestResize(pendingResize, origin, myCursorY, new ResizeHandler() {
      @Override
      public void sizeUpdated(int termWidth, int termHeight, int cursorY) {
        myTerminalWidth = termWidth;
        myTerminalHeight = termHeight;
        myCursorY = cursorY;
      }
    });

    scrollRegionBottom += myTerminalHeight - oldHeight;
    return pixelSize;
  }

  public void fillScreen(final char c) {
    myBackBuffer.lock();
    try {
      final char[] chars = new char[myTerminalWidth];
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

  public int getCursorY() {
    return myCursorY;
  }
}