package com.jediterm.pty;

import com.jediterm.pty.process.monitor.ProcessMonitor;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TabChangeListener;
import com.jediterm.terminal.ui.TerminalTabs;

/**
 * @author gaudima
 */
public class PtyTabChangeListener implements TabChangeListener {
    @Override
    public void tabAdded(TerminalTabs tabs, int index, JediTermWidget terminal) {
        final int ind = index;
        ProcessMonitor.getInstance().watchPid(
                terminal.getTtyConnector().getTtyPid(),
                new ProcessMonitor.TabNameChanger() {
                    int index = ind;

                    @Override
                    public void changeName(String name) {
                        if(index < tabs.getTabCount()) {
                            tabs.setTitleAt(index, name);
                        }
                    }
                }
        );
    }

    @Override
    public void tabRenamed(TerminalTabs tabs, int index, JediTermWidget terminal, String title) {
        if(title.equals("")) {
            tabAdded(tabs, index, terminal);
        } else {
            ProcessMonitor.getInstance().unwatchPid(terminal.getTtyConnector().getTtyPid());
        }
    }

    @Override
    public void tabRemoved(TerminalTabs tabs, int index, JediTermWidget terminal) {
        ProcessMonitor.getInstance().unwatchPid(terminal.getTtyConnector().getTtyPid());
        if (tabs != null){
            for (int i = 0; i < tabs.getTabCount(); i++) {
                final int ind = i;
                ProcessMonitor.getInstance().watchPid(
                        tabs.getComponentAt(i).getTtyConnector().getTtyPid(),
                        new ProcessMonitor.TabNameChanger() {
                            int index = ind;

                            @Override
                            public void changeName(String name) {
                                if(index < tabs.getTabCount()) {
                                    tabs.setTitleAt(index, name);
                                }
                            }
                        }
                );
            }
        }
    }
}
