package com.jediterm.terminal.model;

import com.jediterm.terminal.model.TerminalTypeAheadManager.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TypeAheadTerminalModel {
  void applyPredictions(@NotNull List<@NotNull TypeAheadPrediction> predictions);

  void clearPredictions();

  void lock();

  void unlock();

  boolean isUsingAlternateBuffer();

  @NotNull LineWithCursor getCurrentLineWithCursor();

  int getTerminalWidth();

  boolean isTypeAheadEnabled();

  long getLatencyThreshold();

  void matchPrediction(@NotNull TypeAheadPrediction prediction);

  class LineWithCursor {
    final @NotNull StringBuffer myLineText;
    int myCursorX;
    int myCursorY;

    public LineWithCursor(@NotNull StringBuffer terminalLine, int cursorX, int cursorY) {
      myLineText = terminalLine;
      myCursorX = cursorX;
      myCursorY = cursorY;
    }

    public @NotNull LineWithCursor copy() {
      return new LineWithCursor(new StringBuffer(myLineText), myCursorX, myCursorY);
    }

    public void applyPrediction(TypeAheadPrediction prediction) {
      if (prediction instanceof TentativeBoundary) {
        prediction = ((TentativeBoundary) prediction).myInnerPrediction;
      }

      if (prediction instanceof CharacterPrediction) {
        if (0 <= myCursorX || myCursorX <= myLineText.length()) {
          char ch = ((CharacterPrediction) prediction).myCharacter;
          myLineText.insert(myCursorX, ch);
          myCursorX++;
        }
      } else if (prediction instanceof BackspacePrediction) {
        if (myCursorX > 0) {
          myCursorX--;
          myLineText.deleteCharAt(myCursorX);
        }
      } else if (prediction instanceof CursorMovePrediction) {
        myCursorX = prediction.myPredictedCursorX;
      } else {
        throw new IllegalStateException("Predictions should be filtered but prediction type is" + prediction.getClass().getSimpleName());
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

        index += direction.myDelta;
      }

      if (direction == TerminalTypeAheadManager.CursorMoveDirection.Back) {
        index += 1;
      }

      return index;
    }
  }
}
