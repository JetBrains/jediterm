package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalApplicationTitleListener;

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

  Dimension getPreferredSize();

  TerminalDisplay getTerminalDisplay();

  void addListener(TerminalWidgetListener listener);
  void removeListener(TerminalWidgetListener listener);
}
