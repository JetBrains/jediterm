package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import org.jetbrains.annotations.NotNull;


public interface TerminalPanelListener {
  void onPanelResize(@NotNull RequestOrigin origin);

  void onTitleChanged(String title);
}
