package com.jediterm.terminal.model;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface TerminalApplicationTitleListener {
  void onApplicationTitleChanged(@NotNull @Nls String newApplicationTitle);

  // Default empty implementation for backward compatibility
  default void onApplicationIconTitleChanged(@NotNull @Nls String newIconTitle) {
  }
}
