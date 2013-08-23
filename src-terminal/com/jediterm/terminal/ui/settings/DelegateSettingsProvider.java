package com.jediterm.terminal.ui.settings;

import javax.swing.KeyStroke;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;

public class DelegateSettingsProvider implements SettingsProvider {

  private SystemSettingsProvider ssp;
  private UserSettingsProvider usp;

  public DelegateSettingsProvider(SystemSettingsProvider ssp, UserSettingsProvider usp) {
    this.ssp = ssp;
    this.usp = usp;
  }

  public KeyStroke[] getCopyKeyStrokes() {
    return ssp.getCopyKeyStrokes();
  }

  public KeyStroke[] getPasteKeyStrokes() {
    return ssp.getPasteKeyStrokes();
  }

  public KeyStroke[] getNewSessionKeyStrokes() {
    return ssp.getNewSessionKeyStrokes();
  }

  public KeyStroke[] getCloseSessionKeyStrokes() {
    return ssp.getCloseSessionKeyStrokes();
  }

  public ColorPalette getTerminalColorPalette() {
    return usp.getTerminalColorPalette();
  }

  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return usp.shouldCloseTabOnLogout(ttyConnector);
  }

}