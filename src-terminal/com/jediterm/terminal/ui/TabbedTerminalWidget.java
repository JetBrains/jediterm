package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.TtyConnectorWaitFor;
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
  private TerminalPanelListener myTerminalPanelListener = null;

  private JediTermWidget myTermWidget = null;

  private JTabbedPane myTabbedPane;

  private SystemSettingsProvider mySettingsProvider;
  
  private List<TabListener> myTabListeners = Lists.newArrayList();
  private TerminalActionProvider myNextActionProvider;

  public TabbedTerminalWidget(@NotNull SystemSettingsProvider settingsProvider) {
    super(new BorderLayout());
    mySettingsProvider = settingsProvider;
  }

  @Override
  public TerminalSession createTerminalSession(final TtyConnector ttyConnector) {
    final JediTermWidget terminal = createInnerTerminalWidget();
    terminal.setTtyConnector(ttyConnector);
    terminal.setNextProvider(this);

    new TtyConnectorWaitFor(ttyConnector, Executors.newSingleThreadExecutor()).setTerminationCallback(new Predicate<Integer>() {
      @Override
      public boolean apply(Integer integer) {
        if (mySettingsProvider.shouldCloseTabOnLogout(ttyConnector)) {
          if (myTabbedPane != null) {
            removeTab(terminal);
          }
        }
        return true;
      }
    });

    if (myTerminalPanelListener != null) {
      terminal.setTerminalPanelListener(myTerminalPanelListener);
    }

    if (myTermWidget == null && myTabbedPane == null) {
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
      if (myTabbedPane == null) {
        myTabbedPane = setupTabbedPane();
      }

      addTab(terminal, myTabbedPane);
    }
    return terminal;
  }

  protected JediTermWidget createInnerTerminalWidget() {
    return new JediTermWidget(mySettingsProvider);
  }

  private void addTab(JediTermWidget terminal, JTabbedPane tabbedPane) {
    tabbedPane.addTab(generateUniqueName(terminal.getSessionName(), tabbedPane), null, terminal);

    tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, new TabComponent(tabbedPane, terminal));
    tabbedPane.setSelectedComponent(terminal);
  }

  private String generateUniqueName(String suggestedName, JTabbedPane tabbedPane) {
    final Set<String> names = Sets.newHashSet();
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      names.add(tabbedPane.getTitleAt(i));
    }
    String newSdkName = suggestedName;
    int i = 0;
    while (names.contains(newSdkName)) {
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }

  private JTabbedPane setupTabbedPane() {
    final JTabbedPane tabbedPane = createTabbedPane();

    tabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
        onSessionChanged();
      }
    });

    remove(myTermWidget);

    addTab(myTermWidget, tabbedPane);

    myTermWidget = null;

    add(tabbedPane, BorderLayout.CENTER);

    return tabbedPane;
  }

  public boolean isNoActiveSessions() {
    return myTabbedPane == null && myTermWidget == null;
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

  protected JTabbedPane createTabbedPane() {
    return new JTabbedPane();
  }

  private JPopupMenu createPopup(final JediTermWidget terminal) {
    JPopupMenu popupMenu = new JPopupMenu();

    JMenuItem newSession = new JMenuItem(mySettingsProvider.getNewSessionAction());
    if (mySettingsProvider.getNewSessionKeyStrokes().length > 0) {
      newSession.setAccelerator(mySettingsProvider.getNewSessionKeyStrokes()[0]);
    }
    popupMenu.add(newSession);

    JMenuItem rename = new JMenuItem("Rename");
    rename.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        renameTab();
      }
    });
    JMenuItem close = new JMenuItem("Close");
    close.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        close(terminal);
      }
    });
    if (mySettingsProvider.getCloseSessionKeyStrokes().length > 0) {
      close.setAccelerator(mySettingsProvider.getCloseSessionKeyStrokes()[0]);
    }

    popupMenu.add(rename);
    popupMenu.add(close);

    return popupMenu;
  }

  private void renameTab() {
    if (myTabbedPane != null) {
      final int selectedIndex = myTabbedPane.getSelectedIndex();
      final Component component = myTabbedPane.getTabComponentAt(selectedIndex);
      final JTextField jTextField = new JTextField(myTabbedPane.getTitleAt(selectedIndex));
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
      myTabbedPane.setTabComponentAt(myTabbedPane.getSelectedIndex(), jTextField);
      jTextField.requestFocus();
      jTextField.selectAll();
    }
  }

  private void finishRename(int selectedIndex, Component component, String newName) {
    myTabbedPane.setTabComponentAt(selectedIndex, component);
    if (newName != null) {
      myTabbedPane.setTitleAt(selectedIndex, newName);
    }
  }

  private void close(JediTermWidget terminal) {
    if (terminal != null) {
      terminal.close();
      if (myTabbedPane != null) {
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

  private void removeTab(JediTermWidget terminal) {
    myTabbedPane.remove(terminal);
    if (myTabbedPane.getTabCount() == 1) {
      myTermWidget = getTerminalPanel(0);
      myTabbedPane.removeAll();
      remove(myTabbedPane);
      myTabbedPane = null;
      add(myTermWidget.getComponent(), BorderLayout.CENTER);
    }

    onSessionChanged();
  }

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList(
      new TerminalAction(mySettingsProvider.getNewSessionKeyStrokes(), new Runnable() {
        @Override
        public void run() {
          handleNewSession();
        }
      }),
      new TerminalAction(mySettingsProvider.getCloseSessionKeyStrokes(), new Runnable() {
        @Override
        public void run() {
          handleCloseSession();
        }
      })
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
    mySettingsProvider.getNewSessionAction().actionPerformed(null);
  }

  private class TabComponent extends JPanel implements FocusListener {

    private JediTermWidget myTerminal;

    private TabComponent(final @NotNull JTabbedPane pane, final JediTermWidget terminal) {
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
            JPopupMenu menu = createPopup(terminal);
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
    if (myTabbedPane != null) {
      for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
        getTerminalPanel(i).setTerminalPanelListener(terminalPanelListener);
      }
    }
    myTerminalPanelListener = terminalPanelListener;
  }

  @Override
  @Nullable
  public JediTermWidget getCurrentSession() {
    if (myTabbedPane != null) {
      return getTerminalPanel(myTabbedPane.getSelectedIndex());
    }
    else {
      return myTermWidget;
    }
  }

  @Nullable
  private JediTermWidget getTerminalPanel(int index) {
    if (index < myTabbedPane.getTabCount() && index >= 0) {
      return (JediTermWidget)myTabbedPane.getComponentAt(index);
    }
    else {
      return null;
    }
  }

  public SystemSettingsProvider getSystemSettingsProvider() {
    return mySettingsProvider;
  }

  
  public void addTabListener(TabListener listener) {
    myTabListeners.add(listener);
  }
  
  public void removeTabListener(TabListener listener) {
    myTabListeners.remove(listener);
  }
  
  private void fireTabClosed(JediTermWidget terminal) {
    for (TabListener l: myTabListeners) {
      l.tabClosed(terminal);
    }
  }
  
  public interface TabListener {
    void tabClosed(JediTermWidget terminal);
  }
}
