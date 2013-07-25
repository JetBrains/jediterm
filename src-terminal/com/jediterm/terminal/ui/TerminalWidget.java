package com.jediterm.terminal.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public interface TerminalWidget {
  TerminalSession createTerminalSession();

  JComponent getComponent();

  boolean canOpenSession();

  void setTerminalPanelListener(TerminalPanelListener terminalPanelListener);

  Dimension getPreferredSize();

  TerminalSession getCurrentSession();
}
