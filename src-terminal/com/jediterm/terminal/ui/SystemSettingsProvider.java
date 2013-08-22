package com.jediterm.terminal.ui;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;

import javax.swing.*;

/**
 * @author traff
 */
public interface SystemSettingsProvider {
  KeyStroke[] getCopyKeyStrokes();

  KeyStroke[] getPasteKeyStrokes();

  KeyStroke[] getNewSessionKeyStrokes();

  KeyStroke[] getCloseSessionKeyStrokes();
  
  ColorPalette getPalette();

  boolean shouldCloseTabOnLogout(TtyConnector ttyConnector);
}
