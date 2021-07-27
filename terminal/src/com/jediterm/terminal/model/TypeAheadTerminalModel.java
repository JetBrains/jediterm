package com.jediterm.terminal.model;

import com.jediterm.terminal.model.TerminalTypeAheadManager.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TypeAheadTerminalModel {
  void applyPredictions(@NotNull List<@NotNull TypeAheadPrediction> predictions,
                        @NotNull LineWithCursor lineWithCursor);

  void clearPredictions();

  void lock();

  void unlock();

  boolean isUsingAlternateBuffer();

  LineWithCursor getCurrentLineWithCursor();

  int getTerminalWidth();

  boolean isTypeAheadEnabled();

  long getLatencyThreshold();

  class LineWithCursor {
    final @NotNull StringBuffer myLineText;
    int myCursorX;
    int myCursorY;

    LineWithCursor(@NotNull StringBuffer terminalLine, int cursorX, int cursorY) {
      myLineText = terminalLine;
      myCursorX = cursorX;
      myCursorY = cursorY;
    }

    public @NotNull LineWithCursor copy() {
      return new LineWithCursor(new StringBuffer(myLineText), myCursorX, myCursorY);
    }

    public void applyPrediction(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent, int terminalWidth) {
      switch (keyEvent.myEventType) {
        case Character:
          Character ch = keyEvent.getCharacterOrNull();
          if (ch == null) {
            throw new IllegalStateException("TypeAheadKeyboardEvent.Character has myCharacter == null");
          }
          myLineText.insert(myCursorX, ch);
          myCursorX++;
          break;
        case Backspace:
          if (myCursorX > 0) {
            myCursorX--;
            myLineText.deleteCharAt(myCursorX);
          }
          break;
        case LeftArrow:
        case RightArrow:
          int delta = keyEvent.myEventType == TerminalTypeAheadManager.TypeAheadEvent.EventType.RightArrow ? 1 : -1;
          if (0 <= myCursorX + delta && myCursorX + delta < terminalWidth) {
            myCursorX += delta;
          }
          break;
        case AltLeftArrow:
        case AltRightArrow:
          TerminalTypeAheadManager.CursorMoveDirection direction = keyEvent.myEventType == TerminalTypeAheadManager.TypeAheadEvent.EventType.AltRightArrow
            ? TerminalTypeAheadManager.CursorMoveDirection.Forward : TerminalTypeAheadManager.CursorMoveDirection.Back;
          myCursorX = moveToWordBoundary(myLineText.toString(), myCursorX, direction);
          break;
        default:
          throw new IllegalStateException("Events should be filtered but keyEvent is " + keyEvent);
      }
    }

    static int moveToWordBoundary(@NotNull String text, int index, @NotNull TerminalTypeAheadManager.CursorMoveDirection direction) {
      if (direction == TerminalTypeAheadManager.CursorMoveDirection.Back) {
        index -= 1;
      }

      boolean ateLeadingWhitespace = false;
      while (index >= 0) {
        if (index >= text.length()) {
          return index;
        }

        char currentChar = text.charAt(index);
        if (Character.isLetterOrDigit(currentChar)) {
          ateLeadingWhitespace = true;
        } else if (ateLeadingWhitespace) {
          break;
        }

        index += direction == TerminalTypeAheadManager.CursorMoveDirection.Forward ? 1 : -1;
      }

      if (direction == TerminalTypeAheadManager.CursorMoveDirection.Back) {
        index += 1;
      }

      return index;
    }
  }
}
