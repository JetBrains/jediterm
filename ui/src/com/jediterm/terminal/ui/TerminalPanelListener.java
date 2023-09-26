package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalApplicationTitleListener;
import org.jetbrains.annotations.NotNull;


public interface TerminalPanelListener {
  void onPanelResize(@NotNull RequestOrigin origin);

  /**
   * @deprecated use {@link JediTerminal#addApplicationTitleListener(TerminalApplicationTitleListener)} instead
   */
  @Deprecated
  default void onTitleChanged(String title) {}
}
