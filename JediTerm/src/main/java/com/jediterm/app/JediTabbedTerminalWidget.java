package com.jediterm.app;

import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import com.jediterm.ui.AbstractTabbedTerminalWidget;
import com.jediterm.ui.AbstractTabs;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

/**
 * @author traff
 */
public class JediTabbedTerminalWidget extends AbstractTabbedTerminalWidget<JediTerminalWidget> {

  private final TabbedSettingsProvider mySettingsProvider;

  public JediTabbedTerminalWidget(@NotNull TabbedSettingsProvider settingsProvider,
                                  @NotNull Function<Pair<TerminalWidget, String>, JediTerminalWidget> createNewSessionAction) {
    super(settingsProvider, input -> createNewSessionAction.apply(new Pair<>(input, null)));
    mySettingsProvider = settingsProvider;
  }


  @Override
  public JediTerminalWidget createInnerTerminalWidget() {
    return new JediTerminalWidget(mySettingsProvider);
  }

  @Override
  protected JediTerminalTabs createTabbedPane() {
    return new JediTerminalTabs();
  }

  public static class JediTerminalTabs implements AbstractTabs<JediTerminalWidget> {
    private final JTabbedPane myTabs;

    private final CopyOnWriteArraySet<TabChangeListener> myListeners = new CopyOnWriteArraySet<>();

    public JediTerminalTabs() {
      myTabs = new JTabbedPane(JTabbedPane.TOP);
      myTabs.addChangeListener(e -> {
        if (myTabs.getSelectedIndex() >= 0) {
          for (TabChangeListener each : myListeners) {
            each.selectionChanged();
          }
        }
      });

      myTabs.addContainerListener(new ContainerAdapter() {
        @Override
        public void componentRemoved(ContainerEvent e) {
          for (TabChangeListener each : myListeners) {
            each.tabRemoved();
          }
        }
      });
    }

    @Override
    public int getSelectedIndex() {
      return myTabs.getSelectedIndex();
    }

    @Override
    public void setSelectedIndex(int index) {
      myTabs.setSelectedIndex(index);
    }

    @Override
    public int indexOfComponent(Component component) {
      for (int i = 0; i < myTabs.getTabCount(); i++) {
        if (component.equals(myTabs.getComponentAt(i))) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public JediTerminalWidget getComponentAt(int i) {
      return (JediTerminalWidget)myTabs.getComponentAt(i);
    }

    @Override
    public void addChangeListener(TabChangeListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void setTitleAt(int index, String title) {
      myTabs.setTitleAt(index, title);
    }

    @Override
    public void setSelectedComponent(JediTerminalWidget terminal) {
      myTabs.setSelectedComponent(terminal);
    }

    @Override
    public JComponent getComponent() {
      return myTabs;
    }

    @Override
    public int getTabCount() {
      return myTabs.getTabCount();
    }

    @Override
    public void addTab(String name, JediTerminalWidget terminal) {
      myTabs.addTab(name, terminal);
    }

    public String getTitleAt(int i) {
      return myTabs.getTitleAt(i);
    }

    public void removeAll() {
      myTabs.removeAll();
    }

    @Override
    public void remove(JediTerminalWidget terminal) {
      myTabs.remove(terminal);
    }
  }
}
