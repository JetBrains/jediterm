/**
 *
 */
package com.jediterm.terminal.display;

import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.TermCharset;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * Terminal that reflects obtained commands and text at {@link TerminalDisplay}(handles change of cursor position, screen size etc)
 * and  {@link BackBuffer}(stores printed text)
 *
 * @author traff
 */
public class BufferedDisplayTerminal implements Terminal {
  private static final Logger LOG = Logger.getLogger(BufferedDisplayTerminal.class.getName());

  private final static int TAB = 8; //TODO: move to settings
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

  private final StoredCursor myStoredCursor = new StoredCursor();

  private final EnumSet<TerminalMode> myModes = EnumSet.noneOf(TerminalMode.class);

  private final TerminalKeyEncoder myTerminalKeyEncoder = new TerminalKeyEncoder();

  private TermCharset[] myG = new TermCharset[4]; //initialized in reset
  private int myCurrentCharset = 0;

  private int mySingleShift = -1;

  public BufferedDisplayTerminal(final TerminalDisplay display, final BackBuffer buf, final StyleState initialStyleState) {
    myDisplay = display;
    myBackBuffer = buf;
    myStyleState = initialStyleState;

    myTerminalWidth = display.getColumnCount();
    myTerminalHeight = display.getRowCount();

    myScrollRegionTop = 1;
    myScrollRegionBottom = myTerminalHeight;

    reset();
  }


  @Override
  public void setModeEnabled(TerminalMode mode, boolean enabled) {
    if (enabled) {
      myModes.add(mode);
    }
    else {
      myModes.remove(mode);
    }

    mode.setEnabled(this, enabled);
  }

  @Override
  public void disconnected() {
    myDisplay.setCursorVisible(false);
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

  @Override
  public void writeCharacters(String string) {
    writeCharacters(decode(string).toCharArray(), 0, string.length());
  }

  public void writeCharacters(final char[] chosenBuffer, final int start,
                              final int length) {
    myBackBuffer.lock();
    try {
      wrapLines();
      scrollY();

      if (length != 0) {
        myBackBuffer.clearArea(myCursorX, myCursorY - 1, myCursorX + length, myCursorY);
        myBackBuffer.writeBytes(myCursorX, myCursorY, chosenBuffer, start, length);
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
    doWriteString(decode(string));
  }

  private String decode(String string) {
    StringBuilder result = new StringBuilder();
    for (char c : string.toCharArray()) {
      TermCharset charset = myG[myCurrentCharset];
      if (mySingleShift != -1) {
        charset = myG[mySingleShift];
        mySingleShift = -1;
      }
      result.append(charset.decode(c));
    }

    return result.toString();
  }

  private void doWriteString(String string) {
    myBackBuffer.lock();
    try {
      wrapLines();
      scrollY();

      myBackBuffer.clearArea(myCursorX, myCursorY - 1, myCursorX + string.length(), myCursorY);
      myBackBuffer.writeString(myCursorX, myCursorY, string);
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

  @Override
  public void invokeCharacterSet(int num) {
    myCurrentCharset = num;
  }

  @Override
  public void designateCharacterSet(int tableNumber, TermCharset charset) {
    myG[tableNumber] = charset;
  }

  @Override
  public void singleShiftSelect(int num) {
    mySingleShift = num;
  }

  @Override
  public void setWindowTitle(String name) {
    //TODO: implement
    //To change body of implemented methods use File | Settings | File Templates.
  }

  private void moveLine() {
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

  @Override
  public void clearScreen() {
    clearLines(0, myTerminalHeight);
  }

  @Override
  public void setCursorVisible(boolean visible) {
    myDisplay.setCursorVisible(visible);
  }

  @Override
  public void useAlternateBuffer(boolean enabled) {
    myBackBuffer.useAlternateBuffer(enabled);
    myDisplay.setScrollingEnabled(!enabled);
  }

  @Override
  public byte[] getCodeForKey(int key) {
    return myTerminalKeyEncoder.getCode(key);
  }

  @Override
  public void setApplicationArrowKeys(boolean enabled) {
    if (enabled) {
      myTerminalKeyEncoder.arrowKeysApplicationSequences();
    }
    else {
      myTerminalKeyEncoder.arrowKeysAnsiCursorSequences();
    }
  }

  @Override
  public void setApplicationKeypad(boolean enabled) {
    if (enabled) {
      myTerminalKeyEncoder.keypadApplicationSequences();
    }
    else {
      myTerminalKeyEncoder.normalKeypad();
    }
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

  public void deleteCharacter(int count) {
    myBackBuffer.lock();
    try {
        final int extent = Math.min(count, myTerminalWidth - myCursorX);
        myBackBuffer.deleteCharacter(myCursorY - 1, myCursorX, extent);
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  @Override
  public void insertLines(int num) {
    myBackBuffer.lock();
    try {
      myBackBuffer.insertLines(myCursorY - 1, num);
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

  public void cursorHorizontalAbsolute(int x) {
    cursorPosition(x, myCursorY);
  }

  public void linePositionAbsolute(int y) {
    myCursorY = y;
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void cursorPosition(int x, int y) {
    myCursorX = x - 1;
    myCursorY = y;
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void setScrollingRegion(int top, int bottom) {
    myScrollRegionTop = Math.max(1, top);
    myScrollRegionBottom = Math.min(myTerminalHeight, bottom);
  }

  public void characterAttributes(final StyleState styleState) {
    myStyleState.set(styleState);
  }

  public void beep() {
    myDisplay.beep();
  }

  public int distanceToLineEnd() {
    return myTerminalWidth - myCursorX;
  }

  @Override
  public void storeCursor() {
    storeCursor(myStoredCursor);
  }

  @Override
  public void restoreCursor() {
    restoreCursor(myStoredCursor);
  }

  @Override
  public void reset() {
    invokeCharacterSet(0);
    designateCharacterSet(0, TermCharset.USASCII);
    designateCharacterSet(1, TermCharset.SpecialCharacters);
    designateCharacterSet(2, TermCharset.USASCII);
    designateCharacterSet(3, TermCharset.USASCII);

    myStyleState.reset();
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
        myBackBuffer.writeString(0, row, str);
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

  @Override
  public int getCursorX() {
    return myCursorX;
  }

  @Override
  public int getCursorY() {
    return myCursorY;
  }

  public StyleState getStyleState() {
    return myStyleState;
  }
}