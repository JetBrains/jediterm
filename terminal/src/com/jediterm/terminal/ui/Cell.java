package com.jediterm.terminal.ui;

final class Cell {
  private final int myLine;
  private final int myColumn;

  public Cell(int line, int column) {
    myLine = line;
    myColumn = column;
  }

  public int getLine() {
    return myLine;
  }

  public int getColumn() {
    return myColumn;
  }
}
