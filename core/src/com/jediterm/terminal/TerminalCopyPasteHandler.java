package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TerminalCopyPasteHandler {
  void setContents(@NotNull String text, boolean useSystemSelectionClipboard);

  @Nullable
  String getContents(boolean useSystemSelectionClipboardIfAvailable);
}
