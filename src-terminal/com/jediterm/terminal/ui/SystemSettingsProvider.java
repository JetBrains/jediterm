package com.jediterm.terminal.ui;

import javax.swing.*;

/**
 * @author traff
 */
public interface SystemSettingsProvider {
  AbstractAction getNewSessionAction();

  KeyStroke[] getCopyKeyStrokes();

  KeyStroke[] getPasteKeyStrokes();

  KeyStroke[] getNewSessionKeyStrokes();
}
