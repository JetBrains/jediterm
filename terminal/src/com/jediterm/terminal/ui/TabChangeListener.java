package com.jediterm.terminal.ui;

/**
 * @author gaudima
 */
public interface TabChangeListener {
    void tabAdded(TerminalTabs tabs, int index, JediTermWidget terminal);
    void tabRenamed(TerminalTabs tabs, int index, JediTermWidget terminal, String name);
    void tabRemoved(TerminalTabs tabs, int index, JediTermWidget terminal);
}
