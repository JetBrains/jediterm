package com.jediterm.terminal;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * @author traff
 */
public class HyperlinkStyle extends TextStyle {
  @NotNull
  private final LinkInfo myLinkInfo;

  @NotNull
  private final HighlightMode myHighlightMode;

  public HyperlinkStyle(@NotNull TextStyle prevTextStyle, @NotNull LinkInfo hyperlinkInfo) {
    this(
      prevTextStyle.getForeground(),
      prevTextStyle.getBackground(),
      prevTextStyle.getOptions(),
      hyperlinkInfo,
      HighlightMode.HOVER
    );
  }

  public HyperlinkStyle(@Nullable TerminalColor foreground,
                        @Nullable TerminalColor background,
                        @NotNull LinkInfo hyperlinkInfo,
                        @NotNull HighlightMode mode) {
    this(foreground, background, EnumSet.noneOf(Option.class), hyperlinkInfo, mode);
  }

  private HyperlinkStyle(@Nullable TerminalColor foreground,
                         @Nullable TerminalColor background,
                         @NotNull EnumSet<Option> options,
                         @NotNull LinkInfo hyperlinkInfo,
                         @NotNull HighlightMode mode) {
    super(foreground, background, options);
    myLinkInfo = hyperlinkInfo;
    myHighlightMode = mode;
  }

  @NotNull
  public LinkInfo getLinkInfo() {
    return myLinkInfo;
  }

  @NotNull
  public HighlightMode getHighlightMode() {
    return myHighlightMode;
  }

  @NotNull
  @Override
  public Builder toBuilder() {
    return new Builder(this);
  }

  public enum HighlightMode {
    ALWAYS, NEVER, HOVER
  }

  public static class Builder extends TextStyle.Builder {

    @NotNull
    private final LinkInfo myLinkInfo;

    @NotNull
    private final HighlightMode myHighlightMode;

    private Builder(@NotNull HyperlinkStyle style) {
      super(style);
      myLinkInfo = style.myLinkInfo;
      myHighlightMode = style.myHighlightMode;
    }

    @NotNull
    public HyperlinkStyle build() {
      TextStyle style = super.build();
      TerminalColor foreground = style.getForeground();
      TerminalColor background = style.getBackground();
      EnumSet<Option> options = style.getOptions();
      return new HyperlinkStyle(foreground, background, options, myLinkInfo, myHighlightMode);
    }
  }
}
