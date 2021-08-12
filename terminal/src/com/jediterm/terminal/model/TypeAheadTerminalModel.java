package com.jediterm.terminal.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface TypeAheadTerminalModel {
  void insertCharacter(char ch, int index);

  void removeCharacters(int from, int count);

  void moveCursor(int index);

  void forceRedraw();

  void clearPredictions();

  void lock();

  void unlock();

  boolean isUsingAlternateBuffer();

  @NotNull LineWithCursorX getCurrentLineWithCursor();

  int getTerminalWidth();

  boolean isTypeAheadEnabled();

  long getLatencyThreshold();

  class LineWithCursorX {
    public final @NotNull StringBuffer myLineText;
    public int myCursorX;

    public LineWithCursorX(@NotNull StringBuffer terminalLine, int cursorX) {
      myLineText = terminalLine;
      myCursorX = cursorX;
    }

    public @NotNull LineWithCursorX copy() {
      return new LineWithCursorX(new StringBuffer(myLineText), myCursorX);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LineWithCursorX)) return false;
      LineWithCursorX that = (LineWithCursorX) o;
      return myCursorX == that.myCursorX
        && myLineText.toString().stripTrailing().equals(that.myLineText.toString().stripTrailing());
    }

    @Override
    public int hashCode() {
      return Objects.hash(myLineText, myCursorX);
    }

    static int moveToWordBoundary(@NotNull String text, int index, boolean isDirectionRight) {
      if (!isDirectionRight) {
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

        index += isDirectionRight ? 1 : -1;
      }

      if (!isDirectionRight) {
        index += 1;
      }

      return index;
    }
  }
}
