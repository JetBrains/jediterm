package com.jediterm.terminal.ui;

final class LineCellInterval {
  private final int myLine;
  private final int myStartColumn;
  private final int myEndColumn;

  public LineCellInterval(int line, int startColumn, int endColumn) {
    myLine = line;
    myStartColumn = startColumn;
    myEndColumn = endColumn;
  }

  public int getLine() {
    return myLine;
  }

  public int getStartColumn() {
    return myStartColumn;
  }

  public int getEndColumn() {
    return myEndColumn;
  }

  public int getCellCount() {
    return myEndColumn - myStartColumn + 1;
  }
}
