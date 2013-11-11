package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.TtyConnectorWaitFor;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import com.jediterm.terminal.util.JTextFieldLimit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @author traff
 */
public class TabbedTerminalWidget extends JPanel implements TerminalWidget, TerminalActionProvider {
  private final Object myLock = new Object();

  private TerminalPanelListener myTerminalPanelListener = null;

  private JediTermWidget myTermWidget = null;

  private TerminalTabs myTabs;

  private TabbedSettingsProvider mySettingsProvider;

  private List<TabListener> myTabListeners = Lists.newArrayList();
  private TerminalActionProvider myNextActionProvider;

  private final Predicate<TerminalWidget> myCreateNewSessionAction;

  private JPanel myPanel;

  public TabbedTerminalWidget(@NotNull TabbedSettingsProvider settingsProvider, @NotNull Predicate<TerminalWidget> createNewSessionAction) {
    super(new BorderLayout());
    mySettingsProvider = settingsProvider;
    myCreateNewSessionAction = createNewSessionAction;

    setFocusTraversalPolicy(new DefaultFocusTraversalPolicy());

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(this, BorderLayout.CENTER);
  }

  @Override
  public TerminalSession createTerminalSession(final TtyConnector ttyConnector) {
    final JediTermWidget terminal = createInnerTerminalWidget(mySettingsProvider);
    terminal.setTtyConnector(ttyConnector);
    terminal.setNextProvider(this);

    new TtyConnectorWaitFor(ttyConnector, Executors.newSingleThreadExecutor()).setTerminationCallback(new Predicate<Integer>() {
      @Override
      public boolean apply(Integer integer) {
        if (mySettingsProvider.shouldCloseTabOnLogout(ttyConnector)) {
          if (myTabs != null) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                removeTab(terminal);
              }
            });
          }
          else {
            if (myTermWidget == terminal) {
              myTermWidget = null;
            }
          }
          fireTabClosed(terminal);
        }
        return true;
      }
    });

    if (myTerminalPanelListener != null) {
      terminal.setTerminalPanelListener(myTerminalPanelListener);
    }

    if (myTermWidget == null && myTabs == null) {
      myTermWidget = terminal;
      Dimension size = terminal.getComponent().getSize();

      add(myTermWidget.getComponent(), BorderLayout.CENTER);
      setSize(size);

      if (myTerminalPanelListener != null) {
        myTerminalPanelListener.onPanelResize(size, RequestOrigin.User);
      }

      onSessionChanged();
    }
    else {
      if (myTabs == null) {
        myTabs = setupTabs();
      }

      addTab(terminal, myTabs);
    }
    return terminal;
  }

  protected JediTermWidget createInnerTerminalWidget(SettingsProvider settingsProvider) {
    return new JediTermWidget(settingsProvider);
  }

  private void addTab(JediTermWidget terminal, TerminalTabs tabs) {
    String name = generateUniqueName(mySettingsProvider.tabName(terminal.getTtyConnector(), terminal.getSessionName()), tabs);

    addTab(terminal, tabs, name);
  }

  private void addTab(JediTermWidget terminal, TerminalTabs tabs, String name) {
    tabs.addTab(name,
                terminal);

    tabs.setTabComponentAt(tabs.getTabCount() - 1, new TabComponent(tabs, terminal));
    tabs.setSelectedComponent(terminal);
  }

  public void addTab(String name, JediTermWidget terminal) {
    if (myTabs == null) {
      myTabs = setupTabs();
    }

    addTab(terminal, myTabs, name);
  }

  private static String generateUniqueName(String suggestedName, TerminalTabs tabs) {
    final Set<String> names = Sets.newHashSet();
    for (int i = 0; i < tabs.getTabCount(); i++) {
      names.add(tabs.getTitleAt(i));
    }
    String newSdkName = suggestedName;
    int i = 0;
    while (names.contains(newSdkName)) {
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }

  private TerminalTabs setupTabs() {
    final TerminalTabs tabs = createTabbedPane();

    tabs.addChangeListener(new AbstractTabs.TabChangeListener() {
      @Override
      public void tabRemoved() {
        if (myTabs.getTabCount() == 1) {
          removeTabbedPane();
        }
      }

      @Override
      public void selectionChanged() {
        onSessionChanged();
      }
    });

    remove(myTermWidget);

    addTab(myTermWidget, tabs);

    myTermWidget = null;

    add(tabs.getComponent(), BorderLayout.CENTER);

    return tabs;
  }

  public boolean isNoActiveSessions() {
    return myTabs == null && myTermWidget == null;
  }

  private void onSessionChanged() {
    JediTermWidget session = getCurrentSession();
    if (session != null) {
      if (myTerminalPanelListener != null) {
        myTerminalPanelListener.onSessionChanged(session);
      }
      session.getTerminalPanel().requestFocusInWindow();
    }
  }

  protected TerminalTabs createTabbedPane() {
    return new TerminalTabsImpl();
  }

  private void close(JediTermWidget terminal) {
    if (terminal != null) {
      terminal.close();
      if (myTabs != null) {
        removeTab(terminal);
      }
      else {
        myTermWidget = null;
      }
      fireTabClosed(terminal);
    }
  }


  public void closeCurrentSession() {
    close(getCurrentSession());
  }

  public void dispose() {
    for (TerminalSession s : getAllTerminalSessions()) {
      if (s != null) s.close();
    }
  }

  private List<JediTermWidget> getAllTerminalSessions() {
    List<JediTermWidget> session = Lists.newArrayList();
    if (myTabs != null) {
      for (int i = 0; i < myTabs.getTabCount(); i++) {
        session.add(getTerminalPanel(i));
      }
    }
    else {
      if (myTermWidget != null) {
        session.add(myTermWidget);
      }
    }
    return session;
  }

  private void removeTab(JediTermWidget terminal) {
    synchronized (myLock) {
      if (myTabs != null) {
        myTabs.remove(terminal);
      }
      onSessionChanged();
    }
  }

  private void removeTabbedPane() {
    myTermWidget = getTerminalPanel(0);
    myTabs.removeAll();
    remove(myTabs.getComponent());
    myTabs = null;
    add(myTermWidget.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList(
      new TerminalAction("New Session", mySettingsProvider.getNewSessionKeyStrokes(), new Predicate<KeyEvent>() {
        @Override
        public boolean apply(KeyEvent input) {
          handleNewSession();
          return true;
        }
      }).withMnemonicKey(KeyEvent.VK_N),
      new TerminalAction("Close Session", mySettingsProvider.getCloseSessionKeyStrokes(), new Predicate<KeyEvent>() {
        @Override
        public boolean apply(KeyEvent input) {
          handleCloseSession();
          return true;
        }
      }).withMnemonicKey(KeyEvent.VK_S),
      new TerminalAction("Next Tab", mySettingsProvider.getNextTabKeyStrokes(), new Predicate<KeyEvent>() {
        @Override
        public boolean apply(KeyEvent input) {
          selectNextTab();
          return true;
        }
      }).withEnabledSupplier(new Supplier<Boolean>() {
        @Override
        public Boolean get() {
          return myTabs != null && myTabs.getSelectedIndex() < myTabs.getTabCount() - 1;
        }
      }),
      new TerminalAction("Previous Tab", mySettingsProvider.getPreviousTabKeyStrokes(), new Predicate<KeyEvent>() {
        @Override
        public boolean apply(KeyEvent input) {
          selectPreviousTab();
          return true;
        }
      }).withEnabledSupplier(new Supplier<Boolean>() {
        @Override
        public Boolean get() {
          return myTabs != null && myTabs.getSelectedIndex() > 0;
        }
      })
    );
  }

  private void selectPreviousTab() {
    myTabs.setSelectedIndex(myTabs.getSelectedIndex() - 1);
  }

  private void selectNextTab() {
    myTabs.setSelectedIndex(myTabs.getSelectedIndex() + 1);
  }

  @Override
  public TerminalActionProvider getNextProvider() {
    return myNextActionProvider;
  }

  @Override
  public void setNextProvider(TerminalActionProvider provider) {
    myNextActionProvider = provider;
  }


  private void handleCloseSession() {
    closeCurrentSession();
  }

  private void handleNewSession() {
    myCreateNewSessionAction.apply(this);
  }

  public Component getFocusableComponent() {
    return myTabs != null ? myTabs.getComponent() : myTermWidget != null ? myTermWidget : this;
  }

  public static class TabRenamer {

    public interface RenameCallBack {

      void setComponent(Component c);

      void setNewName(int index, String name);
    }

    public void install(final int selectedIndex, final String text, final Component label, final RenameCallBack callBack) {
      final JTextField textField = createTextField();

      textField.setOpaque(false);

      textField.setDocument(new JTextFieldLimit(50));
      textField.setText(text);

      final FocusAdapter focusAdapter = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent focusEvent) {
          finishRename(selectedIndex, label, textField.getText(), callBack);
        }
      };
      textField.addFocusListener(focusAdapter);
      textField.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent keyEvent) {
          if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            textField.removeFocusListener(focusAdapter);
            finishRename(selectedIndex, label, null, callBack);
          }
          else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
            textField.removeFocusListener(focusAdapter);
            finishRename(selectedIndex, label, textField.getText(), callBack);
          }
          else {
            super.keyPressed(keyEvent);
          }
        }
      });

      callBack.setComponent(textField);


      textField.requestFocus();
      textField.selectAll();
    }

    protected JTextField createTextField() {
      return new JTextField();
    }

    private static void finishRename(int index, Component label, String newName, RenameCallBack callBack) {
      if (newName != null) {
        callBack.setNewName(index, newName);
      }
      callBack.setComponent(label);
    }
  }

  private class TabComponent extends JPanel implements FocusListener {

    private JediTermWidget myTerminal;

    private MyLabelHolder myLabelHolder = new MyLabelHolder();

    private class MyLabelHolder extends JPanel {

      public void set(Component c) {
        myLabelHolder.removeAll();
        myLabelHolder.add(c);
        myLabelHolder.validate();
        myLabelHolder.repaint();
      }
    }

    class TabComponentLabel extends JLabel {
      TabComponent getTabComponent() {
        return TabComponent.this;
      }

      public String getText() {
        if (myTabs != null) {
          int i = myTabs.indexOfTabComponent(TabComponent.this);
          if (i != -1) {
            return myTabs.getTitleAt(i);
          }
        }
        return null;
      }
    }

    private TabComponent(final @NotNull TerminalTabs tabs, final JediTermWidget terminal) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      myTerminal = terminal;
      setOpaque(false);

      setFocusable(false);

      addFocusListener(this);

      //make JLabel read titles from JTabbedPane
      JLabel label = new TabComponentLabel();

      label.addFocusListener(this);

      //add more space between the label and the button
      label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

      label.addMouseListener(new MouseAdapter() {

        @Override
        public void mouseReleased(MouseEvent event) {
          handleMouse(event);
        }

        @Override
        public void mousePressed(MouseEvent event) {
          tabs.setSelectedComponent(terminal);
          handleMouse(event);
        }
      });

      myLabelHolder.set(label);
      add(myLabelHolder);
    }

    protected void handleMouse(MouseEvent event) {
      if (event.isPopupTrigger()) {
        JPopupMenu menu = createPopup();
        menu.show(event.getComponent(), event.getX(), event.getY());
      }
      else {
        if (event.getClickCount() == 2 && !event.isConsumed()) {
          event.consume();
          renameTab();
        }
      }
    }

    protected JPopupMenu createPopup() {
      JPopupMenu popupMenu = new JPopupMenu();

      TerminalAction.addToMenu(popupMenu, TabbedTerminalWidget.this);

      JMenuItem rename = new JMenuItem("Rename Tab");

      rename.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          renameTab();
        }
      });

      popupMenu.add(rename);

      return popupMenu;
    }

    private void renameTab() {
      final int selectedIndex = myTabs.getSelectedIndex();
      final JLabel label = (JLabel)myLabelHolder.getComponent(0);

      new TabRenamer().install(selectedIndex, label.getText(), label, new TabRenamer.RenameCallBack() {
        @Override
        public void setComponent(Component c) {
          myLabelHolder.set(c);
        }

        @Override
        public void setNewName(int index, String name) {
          if (myTabs != null) {
            myTabs.setTitleAt(index, name);
          }
        }
      });
    }

    @Override
    public void focusGained(FocusEvent e) {
      myTerminal.getComponent().requestFocusInWindow();
    }

    @Override
    public void focusLost(FocusEvent e) {

    }
  }


  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public boolean canOpenSession() {
    return true;
  }

  @Override
  public void setTerminalPanelListener(TerminalPanelListener terminalPanelListener) {
    if (myTabs != null) {
      for (int i = 0; i < myTabs.getTabCount(); i++) {
        getTerminalPanel(i).setTerminalPanelListener(terminalPanelListener);
      }
    }
    myTerminalPanelListener = terminalPanelListener;
  }

  @Override
  @Nullable
  public JediTermWidget getCurrentSession() {
    if (myTabs != null) {
      return getTerminalPanel(myTabs.getSelectedIndex());
    }
    else {
      return myTermWidget;
    }
  }

  @Override
  public TerminalDisplay getTerminalDisplay() {
    return getCurrentSession().getTerminalDisplay();
  }

  @Nullable
  private JediTermWidget getTerminalPanel(int index) {
    if (index < myTabs.getTabCount() && index >= 0) {
      return (JediTermWidget)myTabs.getComponentAt(index);
    }
    else {
      return null;
    }
  }

  public void addTabListener(TabListener listener) {
    myTabListeners.add(listener);
  }

  public void removeTabListener(TabListener listener) {
    myTabListeners.remove(listener);
  }

  private void fireTabClosed(JediTermWidget terminal) {
    for (TabListener l : myTabListeners) {
      l.tabClosed(terminal);
    }
  }

  public interface TabListener {
    void tabClosed(JediTermWidget terminal);
  }
}
