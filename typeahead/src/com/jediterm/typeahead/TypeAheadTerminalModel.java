package com.jediterm.typeahead;

import org.jetbrains.annotations.NotNull;

import java.util.List;
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

  ShellType getShellType();

  enum ShellType {
    Bash,
    Zsh,
    Unknown
  }

  static ShellType commandLineToShellType(List<String> commandLine) {
    if (commandLine == null || commandLine.isEmpty()) return ShellType.Unknown;
    String command = commandLine.get(0);

    if (command.endsWith("bash")) return ShellType.Bash;
    else if (command.endsWith("zsh")) return ShellType.Zsh;
    else return ShellType.Unknown;
  }

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

    void moveToWordBoundary(boolean isDirectionRight, ShellType shellType) {
      switch (shellType) {
        case Bash:
        default: // fallback to bash
          moveToWordBoundaryBash(isDirectionRight);
          break;
        case Zsh:
          moveToWordBoundaryZsh(isDirectionRight);
          break;
      }
    }

    private void moveToWordBoundaryZsh(boolean isDirectionRight) {
      String text = myLineText.toString();

      // https://github.com/zsh-users/zsh/blob/00d20ed15e18f5af682f0daec140d6b8383c479a/Src/zsh_system.h#L452
      String defaultWordChars = "*?_-.[]~=/&;!#$%^(){}<>";
      if (isDirectionRight) {
        while (myCursorX < text.length()
          && (Character.isLetterOrDigit(text.charAt(myCursorX)) || defaultWordChars.indexOf(text.charAt(myCursorX)) != -1)) {
          myCursorX++;
        }
        while (myCursorX < text.length()
          && !(Character.isLetterOrDigit(text.charAt(myCursorX)) || defaultWordChars.indexOf(text.charAt(myCursorX)) != -1)) {
          myCursorX++;
        }
      } else {
        myCursorX--;
        while (myCursorX >= 0
          && !(Character.isLetterOrDigit(text.charAt(myCursorX)) || defaultWordChars.indexOf(text.charAt(myCursorX)) != -1)) {
          myCursorX--;
        }
        while (myCursorX >= 0
          && (Character.isLetterOrDigit(text.charAt(myCursorX)) || defaultWordChars.indexOf(text.charAt(myCursorX)) != -1)) {
          myCursorX--;
        }
        myCursorX++;
      }
    }

    private void moveToWordBoundaryBash(boolean isDirectionRight) {
      String text = myLineText.toString();

      if (!isDirectionRight) {
        myCursorX -= 1;
      }

      boolean ateLeadingWhitespace = false;
      while (myCursorX >= 0) {
        if (myCursorX >= text.length()) {
          return;
        }

        char currentChar = text.charAt(myCursorX);
        if (Character.isLetterOrDigit(currentChar)) {
          ateLeadingWhitespace = true;
        } else if (ateLeadingWhitespace) {
          break;
        }

        myCursorX += isDirectionRight ? 1 : -1;
      }

      if (!isDirectionRight) {
        myCursorX += 1;
      }
    }
  }
}
