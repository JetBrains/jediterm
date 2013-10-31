package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.TtyConnectorWaitFor;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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

  public TabbedTerminalWidget(@NotNull TabbedSettingsProvider settingsProvider, @NotNull Predicate<TerminalWidget> createNewSessionAction) {
    super(new BorderLayout());
    mySettingsProvider = settingsProvider;
    myCreateNewSessionAction = createNewSessionAction;

    setFocusTraversalPolicy(new DefaultFocusTraversalPolicy());
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
    tabs.addTab(generateUniqueName(mySettingsProvider.tabName(terminal.getTtyConnector(), terminal.getSessionName()), tabs),
                terminal);

    tabs.setTabComponentAt(tabs.getTabCount() - 1, new TabComponent(tabs, terminal));
    tabs.setSelectedComponent(terminal);
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

    tabs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
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
      doRequestFocus(session.getTerminalPanel());
    }
  }

  protected void doRequestFocus(JComponent component) {
    component.requestFocusInWindow();
  }

  protected TerminalTabs createTabbedPane() {
    return new TerminalTabsImpl();
  }

  private JPopupMenu createPopup() {
    JPopupMenu popupMenu = new JPopupMenu();

    TerminalAction.addToMenu(popupMenu, this);

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
    if (myTabs != null) {
      final int selectedIndex = myTabs.getSelectedIndex();
      final Component component = myTabs.getTabComponentAt(selectedIndex);
      final JTextField jTextField = new JTextField(myTabs.getTitleAt(selectedIndex));
      final FocusAdapter focusAdapter = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent focusEvent) {
          finishRename(selectedIndex, component, jTextField.getText());
        }
      };
      jTextField.addFocusListener(focusAdapter);
      jTextField.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent keyEvent) {
          if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            jTextField.removeFocusListener(focusAdapter);
            finishRename(selectedIndex, component, null);
          }
          else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
            jTextField.removeFocusListener(focusAdapter);
            finishRename(selectedIndex, component, jTextField.getText());
          }
          else {
            super.keyPressed(keyEvent);
          }
        }
      });
      myTabs.setTabComponentAt(myTabs.getSelectedIndex(), jTextField);
      jTextField.requestFocus();
      jTextField.selectAll();
    }
  }

  private void finishRename(int selectedIndex, Component component, String newName) {
    myTabs.setTabComponentAt(selectedIndex, component);
    if (newName != null) {
      myTabs.setTitleAt(selectedIndex, newName);
    }
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
        if (myTabs.getTabCount() == 1) {
          myTermWidget = getTerminalPanel(0);
          myTabs.removeAll();
          remove(myTabs.getComponent());
          myTabs = null;
          add(myTermWidget.getComponent(), BorderLayout.CENTER);
        }
      }
      onSessionChanged();
    }
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
      }).withMnemonicKey(KeyEvent.VK_S)
    );
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

  private class TabComponent extends JPanel implements FocusListener {

    private JediTermWidget myTerminal;

    private TabComponent(final @NotNull TerminalTabs pane, final JediTermWidget terminal) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      myTerminal = terminal;
      setOpaque(false);

      setFocusable(false);

      addFocusListener(this);

      //make JLabel read titles from JTabbedPane
      JLabel label = new JLabel() {
        public String getText() {
          int i = pane.indexOfTabComponent(TabComponent.this);
          if (i != -1) {
            return pane.getTitleAt(i);
          }
          return null;
        }
      };

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
          handleMouse(event);
        }

        private void handleMouse(MouseEvent event) {
          if (event.isPopupTrigger()) {
            JPopupMenu menu = createPopup();
            menu.show(event.getComponent(), event.getX(), event.getY());
          }
          else {
            pane.setSelectedComponent(terminal);
            if (event.getClickCount() == 2 && !event.isConsumed()) {
              event.consume();
              renameTab();
            }
          }
        }
      });


      add(label);
    }

    @Override
    public void focusGained(FocusEvent e) {
      doRequestFocus(myTerminal.getComponent());
    }

    @Override
    public void focusLost(FocusEvent e) {

    }
  }


  @Override
  public JComponent getComponent() {
    return this;
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
