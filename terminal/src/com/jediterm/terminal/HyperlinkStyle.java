package com.jediterm.terminal;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class HyperlinkStyle extends TextStyle implements Runnable {
  @NotNull
  private final LinkInfo myLinkInfo;

  @NotNull
  private TextStyle myHighlightStyle;

  @NotNull
  HighlightMode myHighlightMode = HighlightMode.HOVER;

  public HyperlinkStyle(@NotNull TerminalColor foreground, @NotNull TerminalColor background, @NotNull LinkInfo hyperlinkInfo) {
    super(null, null);
    myHighlightStyle = new TextStyle(foreground, background);
    myHighlightStyle.setOption(Option.UNDERLINED, true);
    myLinkInfo = hyperlinkInfo;
  }

  @NotNull
  public HyperlinkStyle withHighlightMode(@NotNull HighlightMode mode) {
    myHighlightMode = mode;
    return this;
  }

  @Override
  public void run() {
    myLinkInfo.navigate();
  }

  @NotNull
  public TextStyle getHighlightStyle() {
    return myHighlightStyle;
  }

  @NotNull
  public LinkInfo getLinkInfo() {
    return myLinkInfo;
  }

  @NotNull
  public HighlightMode getHighlightMode() {
    return myHighlightMode;
  }

  public enum HighlightMode {
    ALWAYS, NEVER, HOVER
  }
}
