package com.jediterm.terminal.ui.settings;

import javax.swing.*;

public interface SystemSettingsProvider {
  KeyStroke[] getCopyKeyStrokes();

  KeyStroke[] getPasteKeyStrokes();

  KeyStroke[] getNewSessionKeyStrokes();

  KeyStroke[] getCloseSessionKeyStrokes();
}
