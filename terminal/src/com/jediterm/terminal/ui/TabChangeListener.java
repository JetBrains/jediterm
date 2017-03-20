package com.jediterm.terminal.ui;

/**
 * @author gaudima
 */
public interface TabChangeListener {
    void tabAdded(TerminalTabs tabs, int index, JediTermWidget terminal);
    void tabRemoved(TerminalTabs tabs, int index, JediTermWidget terminal);
}
