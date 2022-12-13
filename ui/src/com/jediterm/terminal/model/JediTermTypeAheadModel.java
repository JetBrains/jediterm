package com.jediterm.terminal.model;

import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

public class JediTermTypeAheadModel implements TypeAheadTerminalModel {
  private final @NotNull Terminal myTerminal;
  private final @NotNull TerminalTextBuffer myTerminalTextBuffer;
  private final @NotNull SettingsProvider mySettingsProvider;
  private @NotNull TypeAheadTerminalModel.ShellType myShellType = ShellType.Unknown;

  private boolean isPredictionsApplied = false;

  public JediTermTypeAheadModel(@NotNull Terminal terminal,
                                @NotNull TerminalTextBuffer textBuffer,
                                @NotNull SettingsProvider settingsProvider) {
    myTerminal = terminal;
    myTerminalTextBuffer = textBuffer;
    mySettingsProvider = settingsProvider;
  }

  @Override
  public void insertCharacter(char ch, int index) {
    isPredictionsApplied = true;
    TerminalLine typeAheadLine = getTypeAheadLine();

    TextStyle typeAheadStyle = mySettingsProvider.getTypeAheadSettings().getTypeAheadStyle();
    typeAheadLine.insertString(index, new CharBuffer(ch, 1), typeAheadStyle);

    setTypeAheadLine(typeAheadLine);
  }

  @Override
  public void removeCharacters(int from, int count) {
    isPredictionsApplied = true;
    TerminalLine typeAheadLine = getTypeAheadLine();

    typeAheadLine.deleteCharacters(from, count, TextStyle.EMPTY);

    setTypeAheadLine(typeAheadLine);
  }

  public void forceRedraw() {
    myTerminalTextBuffer.fireTypeAheadModelChangeEvent();
  }

  @Override
  public void moveCursor(int index) {}

  @Override
  public void clearPredictions() {
    if (isPredictionsApplied) {
      myTerminalTextBuffer.clearTypeAheadPredictions();
    }
    isPredictionsApplied = false;
  }

  @Override
  public void lock() {
    myTerminalTextBuffer.lock();
  }

  @Override
  public void unlock() {
    myTerminalTextBuffer.unlock();
  }

  @Override
  public boolean isUsingAlternateBuffer() {
    return myTerminalTextBuffer.isUsingAlternateBuffer();
  }

  @Override
  public boolean isTypeAheadEnabled() {
    return mySettingsProvider.getTypeAheadSettings().isEnabled();
  }

  @Override
  public long getLatencyThreshold() {
    return mySettingsProvider.getTypeAheadSettings().getLatencyThreshold();
  }

  @Override
  public @NotNull ShellType getShellType() {
    return myShellType;
  }

  public void setShellType(ShellType shellType) {
    myShellType = shellType;
  }

  @Override
  public @NotNull TypeAheadTerminalModel.LineWithCursorX getCurrentLineWithCursor() {
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
    return new LineWithCursorX(new StringBuffer(terminalLine.getText()), myTerminal.getCursorX() - 1);
  }

  @Override
  public int getTerminalWidth() {
    return myTerminal.getTerminalWidth();
  }

  private @NotNull TerminalLine getTypeAheadLine() {
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
    if (terminalLine.myTypeAheadLine != null) {
      terminalLine = terminalLine.myTypeAheadLine;
    }
    return terminalLine.copy();
  }

  private void setTypeAheadLine(@NotNull TerminalLine typeAheadTerminalLine) {
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
    terminalLine.myTypeAheadLine = typeAheadTerminalLine;
  }
}
