package com.jediterm.terminal.model;

import com.google.common.collect.Lists;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;

import java.awt.*;
import java.util.List;

/**
 * @author traff
 */
public class TextProcessing {
  private final List<HyperlinkFilter> myHyperlinkFilter;
  private TextStyle myHyperlinkColor;

  public TextProcessing(TextStyle hyperlinkColor) {
    myHyperlinkColor = hyperlinkColor;
    myHyperlinkFilter = Lists.newArrayList();
  }

  public void processHyperlinks(TerminalLine line) {
    for (HyperlinkFilter filter : myHyperlinkFilter) {
      HyperlinkFilter.Result result = filter.apply(line.getText());
      if (result != null) {
        for (HyperlinkFilter.ResultItem item : result.getResultItems()) {
          TextStyle style = new HyperlinkStyle(myHyperlinkColor.getForeground(), myHyperlinkColor.getBackground(), item::navigate);
          line.writeString(item.getStartOffset(), new CharBuffer(line.getText().substring(item.getStartOffset(), item.getEndOffset())), style);
        }
      }
    }
  }

  public void addHyperlinkFilter(HyperlinkFilter filter) {
    myHyperlinkFilter.add(filter);
  }
}
