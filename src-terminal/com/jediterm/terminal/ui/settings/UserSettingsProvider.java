package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;

public interface UserSettingsProvider {
  ColorPalette getTerminalColorPalette();

  boolean shouldCloseTabOnLogout(TtyConnector ttyConnector);
}
