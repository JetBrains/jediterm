package com.jediterm.terminal.model;

import java.util.List;

import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalTypeAheadManager.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.*;

public class JediTermTypeAheadModel implements TypeAheadTerminalModel {
  private final @NotNull Terminal myTerminal;
  private final @NotNull TerminalTextBuffer myTerminalTextBuffer;
  private final @NotNull SettingsProvider mySettingsProvider;

  @Nullable List<@NotNull TypeAheadPrediction> lastPredictions = null;

  public JediTermTypeAheadModel(@NotNull Terminal terminal,
                                @NotNull TerminalTextBuffer textBuffer,
                                @NotNull SettingsProvider settingsProvider) {
    myTerminal = terminal;
    myTerminalTextBuffer = textBuffer;
    mySettingsProvider = settingsProvider;
  }

  @Override
  public void applyPredictions(@NotNull List<@NotNull TypeAheadPrediction> predictions) {
    lastPredictions = predictions;
    LineWithCursorX lineWithCursorX = getCurrentLineWithCursor();

    TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
    TerminalLine newTerminalLine = terminalLine.copy();

    int cursorX = lineWithCursorX.myCursorX;
    for (TypeAheadPrediction prediction : predictions) {
      cursorX = updateTerminalLinePrediction(newTerminalLine, cursorX, prediction);
    }

    terminalLine.setTypeAheadLine(newTerminalLine);
    myTerminalTextBuffer.fireModelChangeEvent();
  }

  @Override
  public void clearPredictions() {
    if (lastPredictions != null) {
      myTerminalTextBuffer.clearTypeAheadPredictions();
      myTerminalTextBuffer.fireModelChangeEvent();
    }
    lastPredictions = null;
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
  public @NotNull TypeAheadTerminalModel.LineWithCursorX getCurrentLineWithCursor() {
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(myTerminal.getCursorY() - 1);
    return new LineWithCursorX(new StringBuffer(terminalLine.getText()), myTerminal.getCursorX() - 1);
  }

  @Override
  public int getTerminalWidth() {
    return myTerminal.getTerminalWidth();
  }

  private int updateTerminalLinePrediction(@NotNull TerminalLine terminalLine,
                                           int cursorX,
                                           @NotNull TerminalTypeAheadManager.TypeAheadPrediction prediction) {
    if (prediction instanceof CharacterPrediction) {
      char ch = ((CharacterPrediction) prediction).myCharacter;
      TextStyle typeAheadTextStyle = mySettingsProvider.getTypeAheadSettings().getTextStyle();
      terminalLine.insertString(cursorX, new CharBuffer(ch, 1), typeAheadTextStyle);
      cursorX++;
    } else if (prediction instanceof BackspacePrediction) {
      if (cursorX > 0) {
        cursorX--;
        terminalLine.deleteCharacters(cursorX, 1, TextStyle.EMPTY);
      }
    } else if (prediction instanceof CursorMovePrediction) {
      int predictedCursorX = prediction.myPredictedLineWithCursorX.myCursorX;
      if (0 <= predictedCursorX && predictedCursorX < myTerminal.getTerminalWidth()) {
        cursorX = predictedCursorX;
      }
    } else {
      throw new IllegalStateException("Predictions should be filtered but prediction type is" + prediction.getClass().getSimpleName());
    }

    return cursorX;
  }

}
