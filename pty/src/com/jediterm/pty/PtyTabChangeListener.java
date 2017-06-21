package com.jediterm.pty;

import com.jediterm.pty.process.monitor.ProcessMonitor;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TabChangeListener;
import com.jediterm.terminal.ui.TerminalTabs;
import org.apache.log4j.Logger;

/**
 * @author gaudima
 */
public class PtyTabChangeListener implements TabChangeListener {
  private static final Logger LOG = Logger.getLogger(PtyTabChangeListener.class);

  @Override
  public void tabAdded(TerminalTabs tabs, int index, JediTermWidget terminal) {
    final int ind = index;
    try {
      ProcessMonitor.getInstance().watchPid(
              terminal.getTtyConnector().getTtyPid(),
              new ProcessMonitor.TabNameChanger() {
                int index = ind;

                @Override
                public void changeName(String name) {
                  if (index < tabs.getTabCount()) {
                    tabs.setTitleAt(index, name);
                  }
                }
              }
      );
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void tabRenamed(TerminalTabs tabs, int index, JediTermWidget terminal, String title) {
    if (title.equals("")) {
      tabAdded(tabs, index, terminal);
    } else {
      try {
        ProcessMonitor.getInstance().unwatchPid(terminal.getTtyConnector().getTtyPid());
      } catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void tabRemoved(TerminalTabs tabs, int index, JediTermWidget terminal) {
    try {
      ProcessMonitor.getInstance().unwatchPid(terminal.getTtyConnector().getTtyPid());
    } catch (Exception e) {
      LOG.error(e);
    }
    if (tabs != null) {
      for (int i = 0; i < tabs.getTabCount(); i++) {
        final int ind = i;
        try {
          ProcessMonitor.getInstance().watchPid(
                  tabs.getComponentAt(i).getTtyConnector().getTtyPid(),
                  new ProcessMonitor.TabNameChanger() {
                    int index = ind;

                    @Override
                    public void changeName(String name) {
                      if (index < tabs.getTabCount()) {
                        tabs.setTitleAt(index, name);
                      }
                    }
                  }
          );
        } catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }
}
