package com.jediterm.terminal.ui;

import com.jediterm.core.TerminalDisplay;
import com.jediterm.core.TtyConnector;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public interface TerminalWidget {
  JediTermWidget createTerminalSession(TtyConnector ttyConnector);

  JComponent getComponent();

  default JComponent getPreferredFocusableComponent() {
    return getComponent();
  }

  boolean canOpenSession();

  void setTerminalPanelListener(TerminalPanelListener terminalPanelListener);

  Dimension getPreferredSize();

  TerminalSession getCurrentSession();

  TerminalDisplay getTerminalDisplay();

  void addListener(TerminalWidgetListener listener);
  void removeListener(TerminalWidgetListener listener);
}
