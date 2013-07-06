package com.jediterm.terminal.ui;

import java.awt.*;

/**
 * @author traff
 */
public interface TerminalWidget {
    TerminalSession createTerminalSession();

    Component getComponent();

    boolean canOpenSession();

    void setResizePanelDelegate(ResizePanelDelegate resizePanelDelegate);

    Dimension getPreferredSize();

    TerminalSession getCurrentSession();
}
