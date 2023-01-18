package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public interface TabbedSettingsProvider extends SettingsProvider {
  boolean shouldCloseTabOnLogout(TtyConnector ttyConnector);

  String tabName(TtyConnector ttyConnector, String sessionName);

  @NotNull TerminalActionPresentation getPreviousTabActionPresentation();

  @NotNull TerminalActionPresentation getNextTabActionPresentation();
}
