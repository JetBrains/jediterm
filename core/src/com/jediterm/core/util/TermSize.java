package com.jediterm.core.util;

import java.util.Objects;

public final class TermSize {

  private final int myColumns;
  private final int myRows;

  public TermSize(int columns, int rows) {
    if (columns < 0) {
      throw new IllegalArgumentException("negative column count: " + columns);
    }
    if (rows < 0) {
      throw new IllegalArgumentException("negative row count: " + rows);
    }
    myColumns = columns;
    myRows = rows;
  }

  public int getColumns() {
    return myColumns;
  }

  public int getRows() {
    return myRows;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TermSize other = (TermSize) o;
    return myColumns == other.myColumns && myRows == other.myRows;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myColumns, myRows);
  }

  @Override
  public String toString() {
    return "columns=" + myColumns + ", rows=" + myRows;
  }
}
