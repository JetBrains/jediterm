package com.jediterm.terminal.ui.settings;

import java.awt.Font;

import javax.swing.KeyStroke;

import com.jediterm.terminal.TextStyle;
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

  public Font getTerminalFont() {
    return usp.getTerminalFont();
  }

  public float getTerminalFontSize() {
    return usp.getTerminalFontSize();
  }

  public float getLineSpace() {
    return usp.getLineSpace();
  }

  public TextStyle getDefaultStyle() {
    return usp.getDefaultStyle();
  }

  public TextStyle getSelectionColor() {
    return usp.getSelectionColor();
  }

  public boolean useInverseSelectionColor() {
    return usp.useInverseSelectionColor();
  }

  public boolean useAntialiasing() {
    return usp.useAntialiasing();
  }

  public ColorPalette getTerminalColorPalette() {
    return usp.getTerminalColorPalette();
  }

  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return usp.shouldCloseTabOnLogout(ttyConnector);
  }

}