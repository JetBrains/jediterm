/**
 *
 */
package com.jediterm.emulator.display;

import com.jediterm.emulator.RequestOrigin;
import com.jediterm.emulator.TerminalDisplay;
import com.jediterm.emulator.TerminalMode;
import com.jediterm.emulator.TerminalWriter;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.EnumSet;

public class BufferedTerminalWriter implements TerminalWriter {
  private static final Logger LOG = Logger.getLogger(BufferedTerminalWriter.class.getName());

  private final static int TAB = 8;
  private static final int MIN_WIDTH = 5;

  private int myScrollRegionTop;
  private int myScrollRegionBottom;
  volatile private int myCursorX = 0;
  volatile private int myCursorY = 1;

  private int myTerminalWidth = 80;
  private int myTerminalHeight = 24;

  private final TerminalDisplay myDisplay;
  private final BackBuffer myBackBuffer;
  private final StyleState myStyleState;

  private final EnumSet<TerminalMode> myModes = EnumSet.of(TerminalMode.ANSI);

  public BufferedTerminalWriter(final TerminalDisplay term, final BackBuffer buf, final StyleState styleState) {
    myDisplay = term;
    myBackBuffer = buf;
    myStyleState = styleState;

    myTerminalWidth = term.getColumnCount();
    myTerminalHeight = term.getRowCount();

    myScrollRegionTop = 1;
    myScrollRegionBottom = myTerminalHeight;
  }

  public void setMode(TerminalMode mode) {
    myModes.add(mode);
    switch (mode) {
      case WideColumn:
        resize(new Dimension(132, 24), RequestOrigin.Remote);
        clearScreen();
        restoreCursor(null);
        break;
    }
  }

  public void unsetMode(TerminalMode mode) {
    myModes.remove(mode);
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
    if (myCursorX >= myTerminalWidth) {
      myCursorX = 0;
      moveLine();
    }
  }

  private void finishText() {
    myDisplay.setCursor(myCursorX, myCursorY);
    scrollY();
  }

  public void writeASCII(final char[] chosenBuffer, final int start,
                         final int length) {
    myBackBuffer.lock();
    try {
      wrapLines();
      if (length != 0) {
        myBackBuffer.clearArea(myCursorX, myCursorY - 1, myCursorX + length, myCursorY);
        myBackBuffer.drawBytes(myCursorX, myCursorY, chosenBuffer, start, length);
      }
      myCursorX += length;
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
      myBackBuffer.clearArea(myCursorX, myCursorY - 1, myCursorX + string.length(), myCursorY);
      myBackBuffer.drawString(myCursorX, myCursorY, string);
      myCursorX += string.length();
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
      if (myCursorY > myScrollRegionBottom) {
        final int dy = myScrollRegionBottom - myCursorY;
        myCursorY = myScrollRegionBottom;
        scrollArea(myScrollRegionTop, myScrollRegionBottom
                                    - myScrollRegionTop, dy);
        myBackBuffer.clearArea(0, myCursorY - 1, myTerminalWidth, myCursorY);
        myDisplay.setCursor(myCursorX, myCursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void newLine() {
    moveLine();
    myDisplay.setCursor(myCursorX, myCursorY);

    scrollY();
  }

  private void moveLine() {
    if (myCursorY <= myTerminalHeight) {
      myBackBuffer.moveToTextBuffer(myCursorY);
    }
    myCursorY += 1;
  }

  public void backspace() {
    myCursorX -= 1;
    if (myCursorX < 0) {
      myCursorY -= 1;
      myCursorX = myTerminalWidth - 1;
    }
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void carriageReturn() {
    myCursorX = 0;
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void horizontalTab() {
    myCursorX = (myCursorX / TAB + 1) * TAB;
    if (myCursorX >= myTerminalWidth) {
      myCursorX = 0;
      myCursorY += 1;
    }
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void eraseInDisplay(final int arg) {
    myBackBuffer.lock();
    try {
      int beginY;
      int endY;

      switch (arg) {
        case 0:
          // Initial line
          if (myCursorX < myTerminalWidth) {
            myBackBuffer.clearArea(myCursorX, myCursorY - 1, myTerminalWidth, myCursorY);
          }
          // Rest
          beginY = myCursorY;
          endY = myTerminalHeight;

          break;
        case 1:
          // initial line
          myBackBuffer.clearArea(0, myCursorY - 1, myCursorX + 1, myCursorY);

          beginY = 0;
          endY = myCursorY - 1;
          break;
        case 2:
          beginY = 0;
          endY = myTerminalHeight;
          break;
        default:
          LOG.error("Unsupported erase in display mode:" + arg);
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
          if (myCursorX < myTerminalWidth) {
            myBackBuffer.clearArea(myCursorX, myCursorY - 1, myTerminalWidth, myCursorY);
          }
          break;
        case 1:
          final int extent = Math.min(myCursorX + 1, myTerminalWidth);
          myBackBuffer.clearArea(0, myCursorY - 1, extent, myCursorY);
          break;
        case 2:
          myBackBuffer.clearArea(0, myCursorY - 1, myTerminalWidth, myCursorY);
          break;
        default:
          LOG.error("Unsupported erase in line mode:" + arg);
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
      myDisplay.setCursor(myCursorX, myCursorY);
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
      myDisplay.setCursor(myCursorX, myCursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void index() {
    myBackBuffer.lock();
    try {
      if (myCursorY == myTerminalHeight) {
        scrollArea(myScrollRegionTop, myScrollRegionBottom
                                    - myScrollRegionTop, -1);
        myBackBuffer.clearArea(0, myScrollRegionBottom - 1, myTerminalWidth,
                               myScrollRegionBottom);
      }
      else {
        myCursorY += 1;
        myDisplay.setCursor(myCursorX, myCursorY);
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
      myCursorX = 0;
      if (myCursorY == myTerminalHeight) {
        scrollArea(myScrollRegionTop, myScrollRegionBottom
                                    - myScrollRegionTop, -1);
        myBackBuffer.clearArea(0, myScrollRegionBottom - 1, myTerminalWidth,
                               myScrollRegionBottom);
      }
      else {
        myCursorY += 1;
      }
      myDisplay.setCursor(myCursorX, myCursorY);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void reverseIndex() {
    myBackBuffer.lock();
    try {
      if (myCursorY == 1) {
        scrollArea(myScrollRegionTop - 1, myScrollRegionBottom
                                        - myScrollRegionTop, 1);
        myBackBuffer.clearArea(myCursorX, myCursorY - 1, myTerminalWidth, myCursorY);
      }
      else {
        myCursorY -= 1;
        myDisplay.setCursor(myCursorX, myCursorY);
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  public void cursorForward(final int dX) {
    myCursorX += dX;
    myCursorX = Math.min(myCursorX, myTerminalWidth - 1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void cursorBackward(final int dX) {
    myCursorX -= dX;
    myCursorX = Math.max(myCursorX, 0);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void cursorPosition(int x, int y) {
    myCursorX = x - 1;
    myCursorY = y;
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void setScrollingRegion(int top, int bottom) {
    myScrollRegionTop = top;
    myScrollRegionBottom = bottom;
  }

  public void setCharacterAttributes(final StyleState styleState) {
    myStyleState.set(styleState);
  }

  public void beep() {
    myDisplay.beep();
  }

  public int distanceToLineEnd() {
    return myTerminalWidth - myCursorX;
  }

  public void storeCursor(final StoredCursor storedCursor) {
    storedCursor.x = myCursorX;
    storedCursor.y = myCursorY;
  }

  public void restoreCursor(final StoredCursor storedCursor) {
    myCursorX = 0;
    myCursorY = 1;
    if (storedCursor != null) {
      // TODO: something with origin modes
      myCursorX = storedCursor.x;
      myCursorY = storedCursor.y;
    }
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public interface ResizeHandler {
    void sizeUpdated(int termWidth, int termHeight, int cursorY);
  }

  public Dimension resize(final Dimension pendingResize, final RequestOrigin origin) {
    final int oldHeight = myTerminalHeight;
    if (pendingResize.width <= MIN_WIDTH) {
      pendingResize.setSize(MIN_WIDTH, pendingResize.height);
    }
    final Dimension pixelSize = myDisplay.requestResize(pendingResize, origin, myCursorY, new ResizeHandler() {
      @Override
      public void sizeUpdated(int termWidth, int termHeight, int cursorY) {
        myTerminalWidth = termWidth;
        myTerminalHeight = termHeight;
        myCursorY = cursorY;
      }
    });

    myScrollRegionBottom += myTerminalHeight - oldHeight;
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