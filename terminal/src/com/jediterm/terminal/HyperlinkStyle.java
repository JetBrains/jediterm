package com.jediterm.terminal;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * @author traff
 */
public class HyperlinkStyle extends TextStyle implements Runnable {
  @NotNull
  private final LinkInfo myLinkInfo;

  @NotNull
  private TextStyle myHighlightStyle;

  @Nullable
  private TextStyle myPrevTextStyle;

  @NotNull
  private HighlightMode myHighlightMode = HighlightMode.HOVER;

  public HyperlinkStyle(@NotNull TextStyle prevTextStyle, @NotNull LinkInfo hyperlinkInfo) {
    this(prevTextStyle.getForeground(), prevTextStyle.getBackground(), hyperlinkInfo);
    myPrevTextStyle = prevTextStyle;
  }

  public HyperlinkStyle(@NotNull TerminalColor foreground, @NotNull TerminalColor background, @NotNull LinkInfo hyperlinkInfo) {
    super(null, null);
    myHighlightStyle = new TextStyle(foreground, background);
    myHighlightStyle.setOption(Option.UNDERLINED, true);
    myLinkInfo = hyperlinkInfo;
  }

  @Nullable
  public TextStyle getPrevTextStyle() {
    return myPrevTextStyle;
  }

  @NotNull
  public HyperlinkStyle withHighlightMode(@NotNull HighlightMode mode) {
    myHighlightMode = mode;
    return this;
  }

  @Override
  public TextStyle clone() {
    HyperlinkStyle style = new HyperlinkStyle(getForeground(), getBackground(), myLinkInfo);
    style.myPrevTextStyle = myPrevTextStyle;
    style.myHighlightMode = myHighlightMode;
    return style;
  }

  public TextStyle readonlyCopy() {
    HyperlinkStyle result = new HyperlinkStyle(getForeground(), getBackground(), myLinkInfo) {
      private TextStyle readonly() {
        throw new IllegalStateException("Text Style is readonly");
      }

      @Override
      public void setBackground(TerminalColor background) {
        readonly();
      }

      @Override
      public void setForeground(TerminalColor foreground) {
        readonly();
      }

      @Override
      public void setOptions(EnumSet<Option> options) {
        readonly();
      }
    };
    result.myPrevTextStyle = myPrevTextStyle != null ? myPrevTextStyle.readonlyCopy() : null;
    result.myHighlightMode = myHighlightMode;
    return result;
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
