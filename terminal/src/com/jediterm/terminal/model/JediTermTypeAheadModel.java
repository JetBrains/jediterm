package com.jediterm.terminal.model;

import java.util.List;

import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalTypeAheadManager.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.*;

import static com.jediterm.terminal.model.TypeAheadTerminalModel.LineWithCursor.moveToWordBoundary;

public class JediTermTypeAheadModel implements TypeAheadTerminalModel {
  @NotNull Terminal myTerminal;
  @NotNull TerminalTextBuffer myTerminalTextBuffer;
  @NotNull SettingsProvider mySettingsProvider;

  @Nullable List<TypeAheadPrediction> lastPredictions = null;

  public JediTermTypeAheadModel(@NotNull Terminal terminal,
                                @NotNull TerminalTextBuffer textBuffer,
                                @NotNull SettingsProvider settingsProvider) {
    myTerminal = terminal;
    myTerminalTextBuffer = textBuffer;
    mySettingsProvider = settingsProvider;
  }

  @Override
  public void applyPredictions(@NotNull List<@NotNull TypeAheadPrediction> predictions,
                               @NotNull LineWithCursor lineWithCursor) {
    lastPredictions = predictions;
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(lineWithCursor.myCursorY);
    TerminalLine newTerminalLine = terminalLine.copy();

    int cursorX = lineWithCursor.myCursorX;
    for (TypeAheadPrediction prediction : predictions) {
      cursorX = updateTerminalLinePrediction(newTerminalLine, cursorX, prediction.myKeyEvent);
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
                                            @NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent) {
    switch (keyEvent.myEventType) {
      case Character:
        Character ch = keyEvent.getCharacterOrNull();
        if (ch == null) {
          throw new IllegalStateException("TypeAheadKeyboardEvent.Character has myCharacter == null");
        }
        TextStyle typeAheadTextStyle = mySettingsProvider.getTypeAheadSettings().getTextStyle();
        terminalLine.writeString(cursorX, new CharBuffer(ch, 1), typeAheadTextStyle);
        cursorX++;
        break;
      case Backspace:
        if (cursorX > 0) {
          cursorX--;
          terminalLine.deleteCharacters(cursorX, 1, TextStyle.EMPTY);
        }
        break;
      case LeftArrow:
      case RightArrow:
        int delta = keyEvent.myEventType == TypeAheadEvent.EventType.RightArrow ? 1 : -1;
        if (0 <= cursorX + delta && cursorX + delta < myTerminal.getTerminalWidth()) {
          cursorX += delta;
        }
        break;
      case AltLeftArrow:
      case AltRightArrow:
        CursorMoveDirection direction = keyEvent.myEventType == TypeAheadEvent.EventType.AltRightArrow
          ? CursorMoveDirection.Forward : CursorMoveDirection.Back;
        cursorX = moveToWordBoundary(terminalLine.getText(), cursorX, direction);
        break;
      default:
        throw new IllegalStateException("Events should be filtered but keyEvent is " + keyEvent);
    }

    return cursorX;
  }

}
