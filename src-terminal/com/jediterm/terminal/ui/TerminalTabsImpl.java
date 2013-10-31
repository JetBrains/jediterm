package com.jediterm.terminal.ui;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author traff
 */
public class TerminalTabsImpl implements TerminalTabs {
  private JTabbedPane myTabbedPane = new JTabbedPane();

  @Override
  public int getTabCount() {
    return myTabbedPane.getTabCount();
  }

  @Override
  public void addTab(String name, JediTermWidget terminal) {
    myTabbedPane.addTab(name, terminal);
  }

  @Override
  public String getTitleAt(int index) {
    return myTabbedPane.getTitleAt(index);
  }

  @Override
  public Component getTabComponentAt(int index) {
    return myTabbedPane.getTabComponentAt(index);
  }

  @Override
  public int getSelectedIndex() {
    return myTabbedPane.getSelectedIndex();
  }

  @Override
  public void setTabComponentAt(int index, Component component) {
    myTabbedPane.setTabComponentAt(index, component);
  }

  @Override
  public int indexOfTabComponent(Component component) {
    return myTabbedPane.indexOfTabComponent(component);
  }

  @Override
  public void removeAll() {
    myTabbedPane.removeAll();
  }

  @Override
  public void remove(JediTermWidget terminal) {
    myTabbedPane.remove(terminal);
  }

  @Override
  public void setTitleAt(int index, String name) {
    myTabbedPane.setTitleAt(index, name);
  }

  @Override
  public void setSelectedComponent(JediTermWidget terminal) {
    myTabbedPane.setSelectedComponent(terminal);
  }

  @Override
  public Component getComponent() {
    return myTabbedPane;
  }

  @Override
  public JediTermWidget getComponentAt(int index) {
    return (JediTermWidget)myTabbedPane.getComponentAt(index);
  }

  @Override
  public void addChangeListener(ChangeListener listener) {
    myTabbedPane.addChangeListener(listener);
  }
}
