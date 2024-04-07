package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.*;

import static com.jediterm.terminal.ui.UtilKt.isWindows;

public class DefaultTerminalCopyPasteHandler implements TerminalCopyPasteHandler, ClipboardOwner {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultTerminalCopyPasteHandler.class);

  @Override
  public void setContents(@NotNull String text, boolean useSystemSelectionClipboardIfAvailable) {
    if (useSystemSelectionClipboardIfAvailable) {
      Clipboard systemSelectionClipboard = getSystemSelectionClipboard();
      if (systemSelectionClipboard != null) {
        setClipboardContents(new StringSelection(text), systemSelectionClipboard);
        return;
      }
    }
    setSystemClipboardContents(text);
  }

  @Nullable
  @Override
  public String getContents(boolean useSystemSelectionClipboardIfAvailable) {
    if (useSystemSelectionClipboardIfAvailable) {
      Clipboard systemSelectionClipboard = getSystemSelectionClipboard();
      if (systemSelectionClipboard != null) {
        return getClipboardContents(systemSelectionClipboard);
      }
    }
    return getSystemClipboardContents();
  }

  @SuppressWarnings("WeakerAccess")
  protected void setSystemClipboardContents(@NotNull String text) {
    setClipboardContents(new StringSelection(text), getSystemClipboard());
  }

  @Nullable
  private String getSystemClipboardContents() {
    return getClipboardContents(getSystemClipboard());
  }

  private void setClipboardContents(@NotNull Transferable contents, @Nullable Clipboard clipboard) {
    if (clipboard != null) {
      try {
        clipboard.setContents(contents, this);
      }
      catch (IllegalStateException e) {
        logException("Cannot set contents", e);
      }
    }
  }

  @Nullable
  private String getClipboardContents(@Nullable Clipboard clipboard) {
    if (clipboard != null) {
      try {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
          return (String) clipboard.getData(DataFlavor.stringFlavor);
        }
      }
      catch (Exception e) {
        logException("Cannot get clipboard contents", e);
      }
    }
    return null;
  }

  @Nullable
  private static Clipboard getSystemClipboard() {
    try {
      return Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    catch (IllegalStateException e) {
      logException("Cannot get system clipboard", e);
      return null;
    }
  }

  @Nullable
  private static Clipboard getSystemSelectionClipboard() {
    try {
      return Toolkit.getDefaultToolkit().getSystemSelection();
    }
    catch (IllegalStateException e) {
      logException("Cannot get system selection clipboard", e);
      return null;
    }
  }

  private static void logException(@NotNull String message, @NotNull Exception e) {
    if (isWindows() && e instanceof IllegalStateException) {
      LOG.debug(message, e);
    }
    else {
      LOG.warn(message, e);
    }
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
  }
}
