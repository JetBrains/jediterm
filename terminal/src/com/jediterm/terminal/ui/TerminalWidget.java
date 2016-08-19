package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TtyConnector;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public interface TerminalWidget {
  TerminalSession createTerminalSession(TtyConnector ttyConnector);

  JComponent getComponent();

  boolean canOpenSession();

  void setTerminalPanelListener(TerminalPanelListener terminalPanelListener);

  Dimension getPreferredSize();

  TerminalSession getCurrentSession();

  TerminalDisplay getTerminalDisplay();
}
