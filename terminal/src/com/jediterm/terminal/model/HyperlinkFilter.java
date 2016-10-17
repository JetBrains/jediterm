package com.jediterm.terminal.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public interface HyperlinkFilter {

  @Nullable
  Result apply(String line);

  interface Result {
    List<ResultItem> getResultItems();
  }

  interface ResultItem {
    int getStartOffset();
    int getEndOffset();
    void navigate();
  }
}
