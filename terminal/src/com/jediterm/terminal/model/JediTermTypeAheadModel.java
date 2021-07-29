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
  private final @NotNull PredictionMatcher myPredictionMatcher;

  @Nullable List<@NotNull TypeAheadPrediction> lastPredictions = null;

  public JediTermTypeAheadModel(@NotNull Terminal terminal,
                                @NotNull TerminalTextBuffer textBuffer,
                                @NotNull SettingsProvider settingsProvider,
                                @NotNull PredictionMatcher predictionMatcher) {
    myTerminal = terminal;
    myTerminalTextBuffer = textBuffer;
    mySettingsProvider = settingsProvider;
    myPredictionMatcher = predictionMatcher;
  }

  @Override
  public void applyPredictions(@NotNull List<@NotNull TypeAheadPrediction> predictions) {
    lastPredictions = predictions;
    LineWithCursor lineWithCursor = getCurrentLineWithCursor();

    TerminalLine terminalLine = myTerminalTextBuffer.getLine(lineWithCursor.myCursorY);
    TerminalLine newTerminalLine = terminalLine.copy();

    int cursorX = lineWithCursor.myCursorX;
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
  public void matchPrediction(TypeAheadPrediction prediction) {
    myPredictionMatcher.matchPrediction(prediction);
  }

  @Override
  public LineWithCursor getCurrentLineWithCursor() {
    int cursorX = myTerminal.getCursorX() - 1;
    int cursorY = myTerminal.getCursorY() - 1;
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(cursorY);

    return new LineWithCursor(new StringBuffer(terminalLine.getText()), cursorX, cursorY);
  }

  @Override
  public int getTerminalWidth() {
    return myTerminal.getTerminalWidth();
  }

  private int updateTerminalLinePrediction(@NotNull TerminalLine terminalLine, // TODO: very similar to LineWithCursor#applyPrediction
                                            int cursorX,
                                            @NotNull TerminalTypeAheadManager.TypeAheadPrediction prediction) {
    if (prediction instanceof TentativeBoundary) {
      prediction = ((TentativeBoundary) prediction).myInnerPrediction;
    }

    if (prediction instanceof CharacterPrediction) {
      char ch = ((CharacterPrediction) prediction).myCharacter;
      TextStyle typeAheadTextStyle = mySettingsProvider.getTypeAheadSettings().getTextStyle();
      terminalLine.writeString(cursorX, new CharBuffer(ch, 1), typeAheadTextStyle);
      cursorX++;
    } else if (prediction instanceof BackspacePrediction) {
      if (cursorX > 0) {
        cursorX--;
        terminalLine.deleteCharacters(cursorX, 1, TextStyle.EMPTY);
      }
    } else if (prediction instanceof CursorMovePrediction) {
      if (0 <= prediction.myPredictedCursorX && prediction.myPredictedCursorX < myTerminal.getTerminalWidth()) {
        cursorX = prediction.myPredictedCursorX;
      }
    } else {
      throw new IllegalStateException("Predictions should be filtered but prediction type is" + prediction.getClass().getSimpleName());
    }

    return cursorX;
  }

}
