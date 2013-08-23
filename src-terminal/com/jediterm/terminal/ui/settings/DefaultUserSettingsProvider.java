package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.sun.jna.Platform;

public class DefaultUserSettingsProvider implements UserSettingsProvider {

  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return true;
  }

  @Override
  public ColorPalette getTerminalColorPalette() {
    return Platform.isWindows() ? ColorPalette.WINDOWS_PALETTE : ColorPalette.XTERM_PALETTE;
  }

}
