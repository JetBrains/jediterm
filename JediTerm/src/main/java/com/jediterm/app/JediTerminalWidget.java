package com.jediterm.app;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;

public class JediTerminalWidget extends JediTermWidget {
  public JediTerminalWidget(SettingsProvider settingsProvider) {
    super(settingsProvider);
    setName("terminal");
  }
}
