package com.jediterm.terminal.ui;

import com.jediterm.terminal.emulator.ColorPalette;

import javax.swing.*;

/**
 * @author traff
 */
public interface SystemSettingsProvider {
  AbstractAction getNewSessionAction();

  KeyStroke[] getCopyKeyStrokes();

  KeyStroke[] getPasteKeyStrokes();

  KeyStroke[] getNewSessionKeyStrokes();

  ColorPalette getPalette();
}
