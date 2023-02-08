package com.jediterm.terminal;

import com.jediterm.core.Color;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.StyleState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Executes terminal commands interpreted by {@link com.jediterm.terminal.emulator.Emulator}, receives text
 *
 * @author traff
 */
public interface Terminal {
  void resize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin);

  void resize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin, @NotNull CompletableFuture<?> promptUpdated);

  void beep();

  void backspace();

  void horizontalTab();

  void carriageReturn();

  void newLine();

  void mapCharsetToGL(int num);

  void mapCharsetToGR(int num);

  void designateCharacterSet(int tableNumber, char ch);

  void setAnsiConformanceLevel(int level);

  void writeDoubleByte(char[] bytes) throws UnsupportedEncodingException;

  void writeCharacters(String string);

  int distanceToLineEnd();

  void reverseIndex();

  void index();

  void nextLine();

  void fillScreen(char c);

  void saveCursor();

  void restoreCursor();

  void reset();

  void characterAttributes(TextStyle textStyle);

  void setScrollingRegion(int top, int bottom);

  void scrollUp(int count);

  void scrollDown(int count);

  void resetScrollRegions();

  void cursorHorizontalAbsolute(int x);

  void linePositionAbsolute(int y);

  void cursorPosition(int x, int y);

  void cursorUp(int countY);

  void cursorDown(int dY);

  void cursorForward(int dX);

  void cursorBackward(int dX);

  void cursorShape(CursorShape shape);

  void eraseInLine(int arg);

  void deleteCharacters(int count);

  int getTerminalWidth();

  int getTerminalHeight();

  void eraseInDisplay(int arg);

  void setModeEnabled(TerminalMode mode, boolean enabled);

  void disconnected();

  int getCursorX();

  int getCursorY();

  void singleShiftSelect(int num);

  void setWindowTitle(String name);

  void saveWindowTitleOnStack();

  void restoreWindowTitleFromStack();

  void clearScreen();

  void setCursorVisible(boolean visible);

  void useAlternateBuffer(boolean enabled);

  byte[] getCodeForKey(int key, int modifiers);

  void setApplicationArrowKeys(boolean enabled);

  void setApplicationKeypad(boolean enabled);

  void setAutoNewLine(boolean enabled);

  StyleState getStyleState();

  void insertLines(int count);

  void deleteLines(int count);

  void setBlinkingCursor(boolean enabled);

  void eraseCharacters(int count);

  void insertBlankCharacters(int count);

  void clearTabStopAtCursor();

  void clearAllTabStops();

  void setTabStopAtCursor();

  void writeUnwrappedString(String string);

  void setTerminalOutput(@Nullable TerminalOutputStream terminalOutput);

  void setMouseMode(@NotNull MouseMode mode);

  void setMouseFormat(MouseFormat mouseFormat);

  void setAltSendsEscape(boolean enabled);

  void deviceStatusReport(String str);

  void deviceAttributes(byte[] response);

  void setLinkUriStarted(@NotNull String uri);

  void setLinkUriFinished();

  void setBracketedPasteMode(boolean enabled);

  @Nullable Color getWindowForeground();

  @Nullable Color getWindowBackground();

  default void addCustomCommandListener(@NotNull TerminalCustomCommandListener listener) {}

  default void removeCustomCommandListener(@NotNull TerminalCustomCommandListener listener) {}

  default void processCustomCommand(@NotNull List<String> args) {}
}
