package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TtyConnector;

/**
 * @author traff
 */
public interface TabbedSettingsProvider extends SettingsProvider {
  boolean shouldCloseTabOnLogout(TtyConnector ttyConnector);

  String tabName(TtyConnector ttyConnector, String sessionName);
}
