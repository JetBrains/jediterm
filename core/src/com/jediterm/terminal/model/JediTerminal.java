package com.jediterm.terminal.model;

import com.jediterm.core.Color;
import com.jediterm.core.Platform;
import com.jediterm.core.TerminalCoordinates;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.input.MouseEvent;
import com.jediterm.core.input.MouseWheelEvent;
import com.jediterm.core.util.CellPosition;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.charset.CharacterSet;
import com.jediterm.terminal.emulator.charset.GraphicSet;
import com.jediterm.terminal.emulator.charset.GraphicSetState;
import com.jediterm.terminal.emulator.mouse.*;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Terminal that reflects obtained commands and text at {@link TerminalDisplay}(handles change of cursor position, screen size etc)
 * and  {@link TerminalTextBuffer}(stores printed text)
 *
 * @author traff
 */
public class JediTerminal implements Terminal, TerminalMouseListener, TerminalCoordinates {
  private static final Logger LOG = LoggerFactory.getLogger(JediTerminal.class.getName());

  private static final int MIN_COLUMNS = 5;
  private static final int MIN_ROWS = 2;

  private int myScrollRegionTop;
  private int myScrollRegionBottom;
  volatile private int myCursorX = 0;
  volatile private int myCursorY = 1;

  private int myTerminalWidth;
  private int myTerminalHeight;

  private final TerminalDisplay myDisplay;
  private final TerminalTextBuffer myTerminalTextBuffer;

  private final StyleState myStyleState;

  private StoredCursor myStoredCursor = null;

  private final EnumSet<TerminalMode> myModes = EnumSet.noneOf(TerminalMode.class);

  private final TerminalKeyEncoder myTerminalKeyEncoder;

  private final Stack<String> myWindowTitlesStack = new Stack<>();

  private final Tabulator myTabulator;

  private final GraphicSetState myGraphicSetState;

  private MouseFormat myMouseFormat = MouseFormat.MOUSE_FORMAT_XTERM;

  @Nullable
  private TerminalOutputStream myTerminalOutput = null;

  private MouseMode myMouseMode = MouseMode.MOUSE_REPORTING_NONE;
  private Point myLastMotionReport = null;
  private boolean myCursorYChanged;

  private final List<TerminalApplicationTitleListener> myApplicationTitleListeners = new CopyOnWriteArrayList<>();
  private final List<TerminalResizeListener> myTerminalResizeListeners = new CopyOnWriteArrayList<>();

  public JediTerminal(@NotNull TerminalDisplay display, @NotNull TerminalTextBuffer buf, @NotNull StyleState initialStyleState) {
    myTerminalKeyEncoder = new TerminalKeyEncoder(Platform.current());
    myDisplay = display;
    myTerminalTextBuffer = buf;
    myStyleState = initialStyleState;

    myTerminalWidth = buf.getWidth();
    myTerminalHeight = buf.getHeight();

    myScrollRegionTop = 1;
    myScrollRegionBottom = myTerminalHeight;

    myTabulator = new DefaultTabulator(myTerminalWidth);

    myGraphicSetState = new GraphicSetState();

    reset(true);
  }


  @Override
  public void setModeEnabled(TerminalMode mode, boolean enabled) {
    if (enabled) {
      myModes.add(mode);
    } else {
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
      // clear the end of the line in the text buffer 
      myTerminalTextBuffer.getLine(myCursorY - 1).deleteCharacters(myTerminalWidth);
      if (isAutoWrap()) {
        myTerminalTextBuffer.getLine(myCursorY - 1).setWrapped(true);
        myCursorY += 1;
      }
    }
  }

  private void finishText() {
    myDisplay.setCursor(myCursorX, myCursorY);
    scrollY();
  }

  @Override
  public void writeCharacters(String string) {
    String normalized = Normalizer.normalize(string, Normalizer.Form.NFC);
    writeDecodedCharacters(decodeUsingGraphicalState(normalized));
  }

  private void writeDecodedCharacters(char[] string) {
    myTerminalTextBuffer.lock();
    try {
      if (myCursorYChanged && string.length > 0) {
        myCursorYChanged = false;
        if (myCursorY > 1) {
          myTerminalTextBuffer.getLine(myCursorY - 2).setWrapped(false);
        }
      }
      wrapLines();
      scrollY();

      if (string.length != 0) {
        CharBuffer characters = newCharBuf(string);
        myTerminalTextBuffer.writeString(myCursorX, myCursorY, characters);
        myCursorX += characters.length();
      }

      finishText();
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void writeDoubleByte(final char[] bytesOfChar) throws UnsupportedEncodingException {
    writeCharacters(new String(bytesOfChar, 0, 2));
  }


  private char[] decodeUsingGraphicalState(String string) {
    StringBuilder result = new StringBuilder();
    for (char c : string.toCharArray()) {
      result.append(myGraphicSetState.map(c));
    }

    return result.toString().toCharArray();
  }

  public void writeUnwrappedString(String string) {
    int length = string.length();
    int off = 0;
    while (off < length) {
      int amountInLine = Math.min(distanceToLineEnd(), length - off);
      writeCharacters(string.substring(off, off + amountInLine));
      wrapLines();
      scrollY();
      off += amountInLine;
    }
  }


  public void scrollY() {
    myTerminalTextBuffer.lock();
    try {
      if (myCursorY > myScrollRegionBottom) {
        final int dy = myScrollRegionBottom - myCursorY;
        myCursorY = myScrollRegionBottom;
        scrollArea(myScrollRegionTop, scrollingRegionSize(), dy);
        myDisplay.setCursor(myCursorX, myCursorY);
      }
      if (myCursorY < myScrollRegionTop) {
        myCursorY = myScrollRegionTop;
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public void crnl() {
    carriageReturn();
    newLine();
  }

  @Override
  public void newLine() {
    myCursorYChanged = true;
    myCursorY += 1;

    scrollY();

    if (isAutoNewLine()) {
      carriageReturn();
    }

    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void mapCharsetToGL(int num) {
    myGraphicSetState.setGL(num);
  }

  @Override
  public void mapCharsetToGR(int num) {
    myGraphicSetState.setGR(num);
  }

  @Override
  public void designateCharacterSet(int tableNumber, char charset) {
    GraphicSet gs = myGraphicSetState.getGraphicSet(tableNumber);
    myGraphicSetState.designateGraphicSet(gs, charset);
  }

  @Override
  public void singleShiftSelect(int num) {
    myGraphicSetState.overrideGL(num);
  }

  @Override
  public void setAnsiConformanceLevel(int level) {
    if (level == 1 || level == 2) {
      myGraphicSetState.designateGraphicSet(0, CharacterSet.ASCII); //ASCII designated as G0
      myGraphicSetState
              .designateGraphicSet(1, CharacterSet.DEC_SUPPLEMENTAL); //TODO: not DEC supplemental, but ISO Latin-1 supplemental designated as G1
      mapCharsetToGL(0);
      mapCharsetToGR(1);
    } else if (level == 3) {
      designateCharacterSet(0, 'B'); //ASCII designated as G0
      mapCharsetToGL(0);
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public void setWindowTitle(@NotNull String name) {
    changeApplicationTitle(name);
  }

  @Override
  public void addApplicationTitleListener(@NotNull TerminalApplicationTitleListener listener) {
    myApplicationTitleListeners.add(listener);
  }

  @Override
  public void removeApplicationTitleListener(@NotNull TerminalApplicationTitleListener listener) {
    myApplicationTitleListeners.remove(listener);
  }

  private void changeApplicationTitle(@Nls String newApplicationTitle) {
    for (TerminalApplicationTitleListener applicationTitleListener : myApplicationTitleListeners) {
      applicationTitleListener.onApplicationTitleChanged(newApplicationTitle);
    }
    myDisplay.setWindowTitle(newApplicationTitle);
  }

  @Override
  public void saveWindowTitleOnStack() {
    String title = myDisplay.getWindowTitle();
    myWindowTitlesStack.push(title);
  }

  @Override
  public void restoreWindowTitleFromStack() {
    if (!myWindowTitlesStack.empty()) {
      String title = myWindowTitlesStack.pop();
      changeApplicationTitle(title);
    }
  }

  @Override
  public void addResizeListener(@NotNull TerminalResizeListener listener) {
    myTerminalResizeListeners.add(listener);
  }

  @Override
  public void removeResizeListener(@NotNull TerminalResizeListener listener) {
    myTerminalResizeListeners.remove(listener);
  }

  @Override
  public @Nullable Color getWindowForeground() {
    return myDisplay.getWindowForeground();
  }

  @Override
  public @Nullable Color getWindowBackground() {
    return myDisplay.getWindowBackground();
  }

  private final List<TerminalCustomCommandListener> myCustomCommandListeners = new CopyOnWriteArrayList<>();

  @Override
  public void addCustomCommandListener(@NotNull TerminalCustomCommandListener listener) {
    myCustomCommandListeners.add(listener);
  }

  @Override
  public void processCustomCommand(@NotNull List<String> args) {
    for (TerminalCustomCommandListener listener : myCustomCommandListeners) {
      listener.process(args);
    }
  }

  @Override
  public void backspace() {
    myCursorX -= 1;
    if (myCursorX < 0) {
      myCursorY -= 1;
      myCursorX = myTerminalWidth - 1;
    }
    adjustXY(-1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void carriageReturn() {
    myCursorX = 0;
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void horizontalTab() {
    if (myCursorX >= myTerminalWidth) {
      return;
    }
    int length = myTerminalTextBuffer.getLine(myCursorY - 1).getText().length();
    int stop = myTabulator.nextTab(myCursorX);
    myCursorX = Math.max(myCursorX, length);
    if (myCursorX < stop) {
      char[] chars = new char[stop - myCursorX];
      Arrays.fill(chars, CharUtils.EMPTY_CHAR);
      writeDecodedCharacters(chars);
    } else {
      myCursorX = stop;
    }
    adjustXY(+1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void eraseInDisplay(final int arg) {
    // ED (Erase in Display) https://vt100.net/docs/vt510-rm/ED.html
    myTerminalTextBuffer.lock();
    try {
      int beginY;
      int endY;

      switch (arg) {
        case 0:
          // Initial line
          if (myCursorX < myTerminalWidth) {
            myTerminalTextBuffer.eraseCharacters(myCursorX, -1, myCursorY - 1);
          }
          // Rest
          beginY = myCursorY;
          endY = myTerminalHeight - 1;

          break;
        case 1:
          // initial line
          myTerminalTextBuffer.eraseCharacters(0, myCursorX + 1, myCursorY - 1);

          beginY = 0;
          endY = myCursorY - 1;
          break;
        case 2:
          beginY = 0;
          endY = myTerminalHeight - 1;
          myTerminalTextBuffer.moveScreenLinesToHistory();
          break;
        case 3:
          // Clear entire screen and delete all lines saved in the scrollback buffer (xterm).
          // https://en.wikipedia.org/wiki/ANSI_escape_code#CSIsection
          // `clear` command emits it, and the scroll buffer is expected to be cleared as a result.
          beginY = 0;
          endY = myTerminalHeight - 1;
          myTerminalTextBuffer.clearHistory();
          break;
        default:
          LOG.warn("Unsupported erase in display mode:" + arg);
          beginY = 1;
          endY = 1;
          break;
      }
      // Rest of lines
      if (beginY != endY) {
        clearLines(beginY, endY);
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public void clearLines(final int beginY, final int endY) {
    myTerminalTextBuffer.lock();
    try {
      myTerminalTextBuffer.clearLines(beginY, endY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void clearScreen() {
    clearLines(0, myTerminalHeight - 1);
  }

  @Override
  public void setCursorVisible(boolean visible) {
    myDisplay.setCursorVisible(visible);
  }

  @Override
  public void useAlternateBuffer(boolean enabled) {
    myTerminalTextBuffer.useAlternateBuffer(enabled);
    myDisplay.useAlternateScreenBuffer(enabled);
  }

  @Override
  public byte[] getCodeForKey(int key, int modifiers) {
    return myTerminalKeyEncoder.getCode(key, modifiers);
  }

  @Override
  public void setApplicationArrowKeys(boolean enabled) {
    if (enabled) {
      myTerminalKeyEncoder.arrowKeysApplicationSequences();
    } else {
      myTerminalKeyEncoder.arrowKeysAnsiCursorSequences();
    }
  }

  @Override
  public void setApplicationKeypad(boolean enabled) {
    if (enabled) {
      myTerminalKeyEncoder.keypadApplicationSequences();
    } else {
      myTerminalKeyEncoder.keypadAnsiSequences();
    }
  }

  @Override
  public void setAutoNewLine(boolean enabled) {
    myTerminalKeyEncoder.setAutoNewLine(enabled);
  }

  public void eraseInLine(int arg) {
    myTerminalTextBuffer.lock();
    try {
      switch (arg) {
        case 0:
          if (myCursorX < myTerminalWidth) {
            myTerminalTextBuffer.eraseCharacters(myCursorX, -1, myCursorY - 1);
          }
          // delete to the end of line : line is no more wrapped
          myTerminalTextBuffer.getLine(myCursorY - 1).setWrapped(false);
          break;
        case 1:
          final int extent = Math.min(myCursorX + 1, myTerminalWidth);
          myTerminalTextBuffer.eraseCharacters(0, extent, myCursorY - 1);
          break;
        case 2:
          myTerminalTextBuffer.eraseCharacters(0, -1, myCursorY - 1);
          break;
        default:
          LOG.warn("Unsupported erase in line mode:" + arg);
          break;
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void deleteCharacters(int count) {
    myTerminalTextBuffer.lock();
    try {
      myTerminalTextBuffer.deleteCharacters(myCursorX, myCursorY - 1, count);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void insertBlankCharacters(int count) {
    myTerminalTextBuffer.lock();
    try {
      final int extent = Math.min(count, myTerminalWidth - myCursorX);
      myTerminalTextBuffer.insertBlankCharacters(myCursorX, myCursorY - 1, extent);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void eraseCharacters(int count) {
    //Clear the next n characters on the cursor's line, including the cursor's
    //position.
    myTerminalTextBuffer.lock();
    try {
      myTerminalTextBuffer.eraseCharacters(myCursorX, myCursorX + count, myCursorY - 1);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void clearTabStopAtCursor() {
    myTabulator.clearTabStop(myCursorX);
  }

  @Override
  public void clearAllTabStops() {
    myTabulator.clearAllTabStops();
  }

  @Override
  public void setTabStopAtCursor() {
    myTabulator.setTabStop(myCursorX);
  }

  @Override
  public void insertLines(int count) {
    myTerminalTextBuffer.lock();
    try {
      myTerminalTextBuffer.insertLines(myCursorY - 1, count, myScrollRegionBottom);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void deleteLines(int count) {
    myTerminalTextBuffer.lock();
    try {
      myTerminalTextBuffer.deleteLines(myCursorY - 1, count, myScrollRegionBottom);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void cursorUp(final int countY) {
    myTerminalTextBuffer.lock();
    try {
      myCursorYChanged = true;
      myCursorY -= countY;
      myCursorY = Math.max(myCursorY, scrollingRegionTop());
      adjustXY(-1);
      myDisplay.setCursor(myCursorX, myCursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void cursorDown(final int dY) {
    myTerminalTextBuffer.lock();
    try {
      myCursorYChanged = true;
      myCursorY += dY;
      myCursorY = Math.min(myCursorY, scrollingRegionBottom());
      adjustXY(-1);
      myDisplay.setCursor(myCursorX, myCursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void index() {
    //Moves the cursor down one line in the
    //same column. If the cursor is at the
    //bottom margin, the page scrolls up
    myTerminalTextBuffer.lock();
    try {
      if (myCursorY == myScrollRegionBottom) {
        scrollArea(myScrollRegionTop, scrollingRegionSize(), -1);
      } else {
        myCursorY += 1;
        adjustXY(-1);
        myDisplay.setCursor(myCursorX, myCursorY);
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
    myDisplay.scrollArea(scrollRegionTop, scrollRegionSize, dy);
    myTerminalTextBuffer.scrollArea(scrollRegionTop, dy, scrollRegionTop + scrollRegionSize - 1);
  }

  @Override
  public void nextLine() {
    myTerminalTextBuffer.lock();
    try {
      myCursorX = 0;
      if (myCursorY == myScrollRegionBottom) {
        scrollArea(myScrollRegionTop, scrollingRegionSize(), -1);
      } else {
        myCursorY += 1;
      }
      myDisplay.setCursor(myCursorX, myCursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private int scrollingRegionSize() {
    return myScrollRegionBottom - myScrollRegionTop + 1;
  }

  @Override
  public void reverseIndex() {
    //Moves the cursor up one line in the same
    //column. If the cursor is at the top margin,
    //the page scrolls down.
    myTerminalTextBuffer.lock();
    try {
      if (myCursorY == myScrollRegionTop) {
        scrollArea(myScrollRegionTop, scrollingRegionSize(), 1);
      } else {
        myCursorY -= 1;
        myDisplay.setCursor(myCursorX, myCursorY);
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private int scrollingRegionTop() {
    return isOriginMode() ? myScrollRegionTop : 1;
  }

  private int scrollingRegionBottom() {
    return isOriginMode() ? myScrollRegionBottom : myTerminalHeight;
  }

  @Override
  public void cursorForward(final int dX) {
    myCursorX += dX;
    myCursorX = Math.min(myCursorX, myTerminalWidth - 1);
    adjustXY(+1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void cursorBackward(final int dX) {
    myCursorX -= dX;
    myCursorX = Math.max(myCursorX, 0);
    adjustXY(-1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void cursorShape(@Nullable CursorShape shape) {
    myDisplay.setCursorShape(shape);
  }

  @Override
  public void cursorHorizontalAbsolute(int x) {
    cursorPosition(x, myCursorY);
  }

  @Override
  public void linePositionAbsolute(int y) {
    myCursorY = y;
    adjustXY(-1);
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  @Override
  public void cursorPosition(int x, int y) {
    myTerminalTextBuffer.modify(() -> {
      if (isOriginMode()) {
        myCursorY = y + scrollingRegionTop() - 1;
      } else {
        myCursorY = y;
      }

      if (myCursorY > scrollingRegionBottom()) {
        myCursorY = scrollingRegionBottom();
      }

      // avoid issue due to malformed sequence
      myCursorX = Math.max(0, x - 1);
      myCursorX = Math.min(myCursorX, myTerminalWidth - 1);

      myCursorY = Math.max(0, myCursorY);

      adjustXY(-1);

      myDisplay.setCursor(myCursorX, myCursorY);
    });
  }

  @Override
  public void setScrollingRegion(int top, int bottom) {
    if (top > bottom) {
      LOG.error("Top margin of scroll region can't be greater then bottom: " + top + ">" + bottom);
    }
    myScrollRegionTop = Math.max(1, top);
    myScrollRegionBottom = Math.min(myTerminalHeight, bottom);

    //DECSTBM moves the cursor to column 1, line 1 of the page
    cursorPosition(1, 1);
  }

  @Override
  public void scrollUp(int count) {
    scrollDown(-count);
  }

  @Override
  public void scrollDown(int count) {
    myTerminalTextBuffer.lock();
    try {
      scrollArea(myScrollRegionTop, scrollingRegionSize(), count);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @Override
  public void resetScrollRegions() {
    setScrollingRegion(1, myTerminalHeight);
  }

  @Override
  public void characterAttributes(final TextStyle textStyle) {
    myStyleState.setCurrent(textStyle);
  }

  @Override
  public void beep() {
    myDisplay.beep();
  }

  @Override
  public int distanceToLineEnd() {
    return myTerminalWidth - myCursorX;
  }

  @Override
  public void saveCursor() {
    myStoredCursor = createCursorState();
  }

  private StoredCursor createCursorState() {
    return new StoredCursor(myCursorX, myCursorY, myStyleState.getCurrent(),
            isAutoWrap(), isOriginMode(), myGraphicSetState);
  }

  @Override
  public void restoreCursor() {
    if (myStoredCursor != null) {
      restoreCursor(myStoredCursor);
    } else { //If nothing was saved by DECSC
      setModeEnabled(TerminalMode.OriginMode, false); //Resets origin mode (DECOM)
      cursorPosition(1, 1); //Moves the cursor to the home position (upper left of screen).
      myStyleState.reset(); //Turns all character attributes off (normal setting).

      myGraphicSetState.resetState();
      //myGraphicSetState.designateGraphicSet(0, CharacterSet.ASCII);//Maps the ASCII character set into GL
      //mapCharsetToGL(0);
      //myGraphicSetState.designateGraphicSet(1, CharacterSet.DEC_SUPPLEMENTAL);
      //mapCharsetToGR(1); //and the DEC Supplemental Graphic set into GR
    }
    myDisplay.setCursor(myCursorX, myCursorY);
  }

  public void restoreCursor(@NotNull StoredCursor storedCursor) {
    myCursorX = storedCursor.getCursorX();
    myCursorY = storedCursor.getCursorY();

    adjustXY(-1);

    myStyleState.setCurrent(storedCursor.getTextStyle());

    setModeEnabled(TerminalMode.AutoWrap, storedCursor.isAutoWrap());
    setModeEnabled(TerminalMode.OriginMode, storedCursor.isOriginMode());

    CharacterSet[] designations = storedCursor.getDesignations();
    for (int i = 0; i < designations.length; i++) {
      myGraphicSetState.designateGraphicSet(i, designations[i]);
    }
    myGraphicSetState.setGL(storedCursor.getGLMapping());
    myGraphicSetState.setGR(storedCursor.getGRMapping());

    if (storedCursor.getGLOverride() >= 0) {
      myGraphicSetState.overrideGL(storedCursor.getGLOverride());
    }
  }

  @Override
  public void reset(boolean clearScrollBackBuffer) {
    myGraphicSetState.resetState();

    myStyleState.reset();

    resetScrollRegions();

    useAlternateBuffer(false);
    if (clearScrollBackBuffer) {
      myTerminalTextBuffer.clearScreenAndHistoryBuffers();
    }
    else {
      myTerminalTextBuffer.clearScreenBuffer();
    }

    initModes();

    initMouseModes();

    cursorPosition(1, 1);
    cursorShape(null);
  }

  private void initMouseModes() {
    setMouseMode(MouseMode.MOUSE_REPORTING_NONE);
    setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM);
  }

  private void initModes() {
    myModes.clear();
    setModeEnabled(TerminalMode.AutoWrap, true);
    setModeEnabled(TerminalMode.AutoNewLine, false);
    setModeEnabled(TerminalMode.CursorVisible, true);
  }

  public boolean isModelEnabled(@NotNull TerminalMode terminalMode) {
    return myModes.contains(terminalMode);
  }

  public boolean isAutoNewLine() {
    return myModes.contains(TerminalMode.AutoNewLine);
  }

  public boolean isOriginMode() {
    return myModes.contains(TerminalMode.OriginMode);
  }

  public boolean isAutoWrap() {
    return myModes.contains(TerminalMode.AutoWrap);
  }

  private byte[] mouseReport(int button, int x, int y) {
    StringBuilder sb = new StringBuilder();
    String charset = "UTF-8"; // extended mode requires UTF-8 encoding
    switch (myMouseFormat) {
      case MOUSE_FORMAT_XTERM_EXT:
        sb.append(String.format("\033[M%c%c%c",
                (char) (32 + button),
                (char) (32 + x),
                (char) (32 + y)));
        break;
      case MOUSE_FORMAT_URXVT:
        sb.append(String.format("\033[%d;%d;%dM", 32 + button, x, y));
        break;
      case MOUSE_FORMAT_SGR:
        if ((button & MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG) != 0) {
          // for mouse release event
          sb.append(String.format("\033[<%d;%d;%dm",
                  button ^ MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG,
                  x,
                  y));
        } else {
          // for mouse press/motion event
          sb.append(String.format("\033[<%d;%d;%dM", button, x, y));
        }
        break;
      case MOUSE_FORMAT_XTERM:
      default:
        // X10 compatibility mode requires ASCII
        // US-ASCII is only 7 bits, so we use ISO-8859-1 (8 bits with ASCII transparency)
        // to handle positions greater than 95 (= 127-32)
        charset = "ISO-8859-1";
        sb.append(String.format("\033[M%c%c%c", (char) (32 + button), (char) (32 + x), (char) (32 + y)));
        break;
    }
    LOG.debug(myMouseFormat + " (" + charset + ") report : " + button + ", " + x + "x" + y + " = " + sb);
    return sb.toString().getBytes(Charset.forName(charset));
  }

  private boolean shouldSendMouseData(MouseMode... eligibleModes) {
    if (myMouseMode == MouseMode.MOUSE_REPORTING_NONE || myTerminalOutput == null) {
      return false;
    }
    if (myMouseMode == MouseMode.MOUSE_REPORTING_ALL_MOTION) {
      return true;
    }
    for (MouseMode m : eligibleModes) {
      if (myMouseMode == m) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void mousePressed(int x, int y, @NotNull MouseEvent event) {
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      int cb = event.getButtonCode();

      if (cb != MouseButtonCodes.NONE) {
        if (cb == MouseButtonCodes.SCROLLDOWN || cb == MouseButtonCodes.SCROLLUP) {
          // convert x11 scroll button number to terminal button code
          int offset = MouseButtonCodes.SCROLLDOWN;
          cb -= offset;
          cb |= MouseButtonModifierFlags.MOUSE_BUTTON_SCROLL_FLAG;
        }

        cb |= event.getModifierKeys();

        if (myTerminalOutput != null) {
          myTerminalOutput.sendBytes(mouseReport(cb, x + 1, y + 1), true);
        }
      }
    }
  }

  @Override
  public void mouseReleased(int x, int y, @NotNull MouseEvent event) {
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_NORMAL, MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      int cb = event.getButtonCode();

      if (cb != MouseButtonCodes.NONE) {

        if (myMouseFormat == MouseFormat.MOUSE_FORMAT_SGR) {
          // for SGR 1006 mode
          cb |= MouseButtonModifierFlags.MOUSE_BUTTON_SGR_RELEASE_FLAG;
        } else {
          // for 1000/1005/1015 mode
          cb = MouseButtonCodes.RELEASE;
        }

        cb |= event.getModifierKeys();

        if (myTerminalOutput != null) {
          myTerminalOutput.sendBytes(mouseReport(cb, x + 1, y + 1), true);
        }
      }
    }
    myLastMotionReport = null;
  }

  @Override
  public void mouseMoved(int x, int y, @NotNull MouseEvent event) {
    if (myLastMotionReport != null && myLastMotionReport.equals(new Point(x, y))) {
      return;
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_ALL_MOTION)) {
      if (myTerminalOutput != null) {
        myTerminalOutput.sendBytes(mouseReport(MouseButtonCodes.RELEASE, x + 1, y + 1), true);
      }
    }
    myLastMotionReport = new Point(x, y);
  }

  @Override
  public void mouseDragged(int x, int y, @NotNull MouseEvent event) {
    if (myLastMotionReport != null && myLastMotionReport.equals(new Point(x, y))) {
      return;
    }
    if (shouldSendMouseData(MouseMode.MOUSE_REPORTING_BUTTON_MOTION)) {
      //when dragging, button is not in "button", but in "modifier"
      int cb = event.getButtonCode();

      if (cb != MouseButtonCodes.NONE) {
        cb |= MouseButtonModifierFlags.MOUSE_BUTTON_MOTION_FLAG;
        cb |= event.getModifierKeys();
        if (myTerminalOutput != null) {
          myTerminalOutput.sendBytes(mouseReport(cb, x + 1, y + 1), true);
        }
      }
    }
    myLastMotionReport = new Point(x, y);
  }

  @Override
  public void mouseWheelMoved(int x, int y, @NotNull MouseWheelEvent event) {
    // mousePressed() handles mouse wheel using SCROLLDOWN and SCROLLUP buttons 
    mousePressed(x, y, event);
  }

  public void setTerminalOutput(TerminalOutputStream terminalOutput) {
    myTerminalOutput = terminalOutput;
  }

  @Override
  public void setMouseMode(@NotNull MouseMode mode) {
    myMouseMode = mode;
    myDisplay.terminalMouseModeSet(mode);
  }

  @Override
  public void setAltSendsEscape(boolean enabled) {
    myTerminalKeyEncoder.setAltSendsEscape(enabled);
  }

  @Override
  public void deviceStatusReport(String str) {
    if (myTerminalOutput != null) {
      myTerminalOutput.sendString(str, false);
    }
  }

  @Override
  public void deviceAttributes(byte[] response) {
    if (myTerminalOutput != null) {
      myTerminalOutput.sendBytes(response, false);
    }
  }

  @Override
  public void setLinkUriStarted(@NotNull String uri) {
    TextStyle style = myStyleState.getCurrent();
    TextProcessing textProcessing = myTerminalTextBuffer.getTextProcessing();
    if (textProcessing != null) {
      List<LinkResultItem> linkResultItems = textProcessing.applyFilter(uri);
      linkResultItems.stream()
        .filter(item -> item.getStartOffset() == 0 && item.getEndOffset() == uri.length())
        .findFirst().ifPresent(linkItem ->
          myStyleState.setCurrent(new HyperlinkStyle(style, linkItem.getLinkInfo())));
    }
  }

  @Override
  public void setLinkUriFinished() {
    TextStyle current = myStyleState.getCurrent();
    if (current instanceof HyperlinkStyle) {
      TextStyle prevTextStyle = ((HyperlinkStyle) current).getPrevTextStyle();
      if (prevTextStyle != null) {
        myStyleState.setCurrent(prevTextStyle);
      }
    }
  }

  @Override
  public void setBracketedPasteMode(boolean enabled) {
    myDisplay.setBracketedPasteMode(enabled);
  }

  @Override
  public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
    myMouseFormat = mouseFormat;
    myDisplay.setMouseFormat(mouseFormat);
  }

  private void adjustXY(int dirX) {
    if (myCursorY > -myTerminalTextBuffer.getHistoryLinesCount() &&
        Character.isLowSurrogate(myTerminalTextBuffer.getCharAt(myCursorX, myCursorY - 1))) {
      // we don't want to place cursor on the second part of surrogate pair
      if (dirX > 0) { // so we move it into the predefined direction
        if (myCursorX == myTerminalWidth) { //if it is the last in the line we return where we were
          myCursorX -= 1;
        } else {
          myCursorX += 1;
        }
      } else {
        myCursorX -= 1; //low surrogate character can't be the first character in the line
      }
    }
  }

  @Override
  public int getX() {
    return myCursorX;
  }

  @Override
  public void setX(int x) {
    myCursorX = x;
    adjustXY(-1);
  }

  @Override
  public int getY() {
    return myCursorY;
  }

  @Override
  public void setY(int y) {
    myCursorY = y;
    adjustXY(-1);
  }

  public void writeString(String s) {
    writeCharacters(s);
  }

  @Override
  public void resize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin) {
    resizeInternal(ensureTermMinimumSize(newTermSize), origin);
  }

  private void resizeInternal(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin) {
    int oldHeight = myTerminalHeight;
    if (newTermSize.getColumns() == myTerminalWidth && newTermSize.getRows() == myTerminalHeight) {
      return;
    }
    doResize(newTermSize, origin, oldHeight);
  }

  private void doResize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin, int oldHeight) {
    TermSize oldTermSize = new TermSize(myTerminalWidth, myTerminalHeight);
    myTerminalTextBuffer.modify(() -> {
      TerminalResizeResult result = myTerminalTextBuffer.resize(newTermSize, getCursorPosition(), myDisplay.getSelection());
      myTerminalWidth = newTermSize.getColumns();
      myTerminalHeight = newTermSize.getRows();
      myCursorX = result.getNewCursor().getX() - 1;
      myCursorY = result.getNewCursor().getY();
      myTabulator.resize(myTerminalWidth);
      myScrollRegionBottom += myTerminalHeight - oldHeight;

      myDisplay.setCursor(myCursorX, myCursorY);
      myDisplay.onResize(newTermSize, origin);
      for (TerminalResizeListener resizeListener : myTerminalResizeListeners) {
        resizeListener.onResize(oldTermSize, newTermSize);
      }
    });
  }

  public static @NotNull TermSize ensureTermMinimumSize(@NotNull TermSize termSize) {
    return new TermSize(Math.max(MIN_COLUMNS, termSize.getColumns()), Math.max(MIN_ROWS, termSize.getRows()));
  }

  @Override
  public void fillScreen(final char c) {
    myTerminalTextBuffer.lock();
    try {
      final char[] chars = new char[myTerminalWidth];
      Arrays.fill(chars, c);

      for (int row = 1; row <= myTerminalHeight; row++) {
        myTerminalTextBuffer.writeString(0, row, newCharBuf(chars));
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  @NotNull
  private CharBuffer newCharBuf(char[] str) {
    int dwcCount = CharUtils.countDoubleWidthCharacters(str, 0, str.length, myDisplay.ambiguousCharsAreDoubleWidth());

    char[] buf;

    if (dwcCount > 0) {
      // Leave gaps for the private use "DWC" character, which simply tells the rendering code to advance one cell.
      buf = new char[str.length + dwcCount];

      int j = 0;
      for (int i = 0; i < str.length; i++) {
        buf[j] = str[i];
        int codePoint = Character.codePointAt(str, i);
        boolean doubleWidthCharacter = CharUtils.isDoubleWidthCharacter(codePoint, myDisplay.ambiguousCharsAreDoubleWidth());
        if (doubleWidthCharacter) {
          j++;
          buf[j] = CharUtils.DWC;
        }
        j++;
      }
    } else {
      buf = str;
    }
    return new CharBuffer(buf, 0, buf.length);
  }

  public @NotNull TerminalTextBuffer getTerminalTextBuffer() {
    return myTerminalTextBuffer;
  }

  @Override
  public int getTerminalWidth() {
    return myTerminalWidth;
  }

  @Override
  public int getTerminalHeight() {
    return myTerminalHeight;
  }

  @Override
  public @NotNull TermSize getSize() {
    return new TermSize(myTerminalWidth, myTerminalHeight);
  }

  @Override
  public int getCursorX() {
    return myCursorX + 1;
  }

  @Override
  public int getCursorY() {
    return myCursorY;
  }

  @Override
  public @NotNull CellPosition getCursorPosition() {
    return new CellPosition(getCursorX(), getCursorY());
  }

  @Override
  public StyleState getStyleState() {
    return myStyleState;
  }

  private static class DefaultTabulator implements Tabulator {
    private static final int TAB_LENGTH = 8;

    private final SortedSet<Integer> myTabStops;

    private int myWidth;
    private int myTabLength;

    public DefaultTabulator(int width) {
      this(width, TAB_LENGTH);
    }

    public DefaultTabulator(int width, int tabLength) {
      myTabStops = new TreeSet<Integer>();

      myWidth = width;
      myTabLength = tabLength;

      initTabStops(width, tabLength);
    }

    private void initTabStops(int columns, int tabLength) {
      for (int i = tabLength; i < columns; i += tabLength) {
        myTabStops.add(i);
      }
    }

    public void resize(int columns) {
      if (columns > myWidth) {
        for (int i = myTabLength * (myWidth / myTabLength); i < columns; i += myTabLength) {
          if (i >= myWidth) {
            myTabStops.add(i);
          }
        }
      } else {
        Iterator<Integer> it = myTabStops.iterator();
        while (it.hasNext()) {
          int i = it.next();
          if (i > columns) {
            it.remove();
          }
        }
      }

      myWidth = columns;
    }

    @Override
    public void clearTabStop(int position) {
      myTabStops.remove(Integer.valueOf(position));
    }

    @Override
    public void clearAllTabStops() {
      myTabStops.clear();
    }

    @Override
    public int getNextTabWidth(int position) {
      return nextTab(position) - position;
    }

    @Override
    public int getPreviousTabWidth(int position) {
      return position - previousTab(position);
    }

    @Override
    public int nextTab(int position) {
      int tabStop = Integer.MAX_VALUE;

      // Search for the first tab stop after the given position...
      SortedSet<Integer> tailSet = myTabStops.tailSet(position + 1);
      if (!tailSet.isEmpty()) {
        tabStop = tailSet.first();
      }

      // Don't go beyond the end of the line...
      return Math.min(tabStop, (myWidth - 1));
    }

    @Override
    public int previousTab(int position) {
      int tabStop = 0;

      // Search for the first tab stop before the given position...
      SortedSet<Integer> headSet = myTabStops.headSet(Integer.valueOf(position));
      if (!headSet.isEmpty()) {
        tabStop = headSet.last();
      }

      // Don't go beyond the start of the line...
      return Math.max(0, tabStop);
    }

    @Override
    public void setTabStop(int position) {
      myTabStops.add(Integer.valueOf(position));
    }
  }
}
