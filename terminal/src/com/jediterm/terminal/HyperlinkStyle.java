package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class HyperlinkStyle extends TextStyle implements Runnable {
  @NotNull
  private final Runnable myNavigateAction;

  public HyperlinkStyle(@NotNull TerminalColor foreground, @NotNull TerminalColor background, @NotNull Runnable navigateAction) {
    super(foreground, background);
    setOption(Option.UNDERLINED, true);
    myNavigateAction = navigateAction;
  }

  @Override
  public void run() {
    myNavigateAction.run();
  }
}
