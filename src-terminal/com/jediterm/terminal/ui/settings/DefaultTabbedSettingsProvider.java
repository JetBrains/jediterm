package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TtyConnector;

/**
 * @author traff
 */
public class DefaultTabbedSettingsProvider extends DefaultSettingsProvider implements TabbedSettingsProvider {
  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return true;
  }

  @Override
  public String tabName(TtyConnector ttyConnector, String sessionName) {
    return sessionName;
  }
}
