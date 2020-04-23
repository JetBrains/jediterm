package com.jediterm.terminal.ui;

import com.jediterm.terminal.TabbedTerminalWidget;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @autor Nikita Sirotenko
 */
public abstract class AbstractTerminalPanel {
    public static final Logger LOG = Logger.getLogger(AbstractTerminalFrame.class);

    private JPanel terminalPanel = new JPanel();

    private TerminalWidget myTerminal;

    @Nullable
    protected JediTermWidget openSession(TerminalWidget terminal) {
        if (terminal.canOpenSession()) {
            return openSession(terminal, createTtyConnector());
        }
        return null;
    }

    public JediTermWidget openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
        JediTermWidget session = terminal.createTerminalSession(ttyConnector);
        session.start();
        return session;
    }

    public abstract TtyConnector createTtyConnector();

    public JPanel getTerminalPanel() {
        return terminalPanel;
    }

    protected AbstractTerminalPanel(int column, int lines) {
        myTerminal = createTabbedTerminalWidget(column, lines);
        this.terminalPanel.add(myTerminal.getComponent());
        openSession(myTerminal);
    }

    @NotNull
    protected AbstractTabbedTerminalWidget createTabbedTerminalWidget(int column, int lines) {
        return new TabbedTerminalWidget(new DefaultTabbedSettingsProvider(), this::openSession) {
            @Override
            public JediTermWidget createInnerTerminalWidget() {
                return createTerminalWidget(column, lines,getSettingsProvider());
            }
        };
    }

    protected JediTermWidget createTerminalWidget(int column, int lines,@NotNull TabbedSettingsProvider settingsProvider) {
        return new JediTermWidget(column,lines,settingsProvider);
    }
}

