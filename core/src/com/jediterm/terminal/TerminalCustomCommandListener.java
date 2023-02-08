package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TerminalCustomCommandListener {
  void process(@NotNull List<String> args);
}
