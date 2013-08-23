package com.jediterm.terminal.ui.settings;

import javax.swing.*;

public interface SystemSettingsProvider {
  AbstractAction getNewSessionAction();

  KeyStroke[] getCopyKeyStrokes();

  KeyStroke[] getPasteKeyStrokes();

  KeyStroke[] getNewSessionKeyStrokes();

  KeyStroke[] getCloseSessionKeyStrokes();
}
