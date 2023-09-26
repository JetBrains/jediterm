package com.jediterm.terminal.model;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface TerminalApplicationTitleListener {
  void onApplicationTitleChanged(@NotNull @Nls String newApplicationTitle);
}
