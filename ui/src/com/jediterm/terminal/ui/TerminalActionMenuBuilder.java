package com.jediterm.terminal.ui;

import org.jetbrains.annotations.NotNull;

public interface TerminalActionMenuBuilder {
  void addAction(@NotNull TerminalAction action);
  void addSeparator();
}
