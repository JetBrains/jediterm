package com.jediterm.terminal.ui;

import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author traff
 */
public interface AbstractTabs<T extends Component> {

  int getTabCount();

  void addTab(String name, T terminal);

  String getTitleAt(int index);

  Component getTabComponentAt(int index);

  int getSelectedIndex();

  void setTabComponentAt(int index, Component component);

  int indexOfTabComponent(Component component);

  void removeAll();

  void remove(T terminal);

  void setTitleAt(int index, String name);

  void setSelectedComponent(T terminal);

  Component getComponent();

  T getComponentAt(int index);

  void addChangeListener(ChangeListener listener);
}
