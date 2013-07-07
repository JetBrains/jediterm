package com.jediterm.terminal.ui;

import java.awt.*;

/**
 * @author traff
 */
public interface TerminalWidget {
    TerminalSession createTerminalSession();

    Component getComponent();

    boolean canOpenSession();

    void setTerminalPanelListener(TerminalPanelListener terminalPanelListener);

    Dimension getPreferredSize();

    TerminalSession getCurrentSession();
}
