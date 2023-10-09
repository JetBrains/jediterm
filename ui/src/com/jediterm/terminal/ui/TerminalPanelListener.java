package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalApplicationTitleListener;
import com.jediterm.terminal.model.TerminalResizeListener;
import org.jetbrains.annotations.NotNull;


public interface TerminalPanelListener {
  /**
   * @deprecated use {@link JediTerminal#addResizeListener(TerminalResizeListener)} instead
   */
  @Deprecated
  void onPanelResize(@NotNull RequestOrigin origin);

  /**
   * @deprecated use {@link JediTerminal#addApplicationTitleListener(TerminalApplicationTitleListener)} instead
   */
  @Deprecated
  default void onTitleChanged(String title) {}
}
