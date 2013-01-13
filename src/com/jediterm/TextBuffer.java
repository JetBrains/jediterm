package com.jediterm;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author traff
 */
public class TextBuffer {
  private final List<String> myLines = Lists.newArrayList();

  public void addLine(String line) {
    myLines.add(line);
  }

  public void processRows(int startRow, int endRow, StyledTextConsumer consumer) {

  }
}
