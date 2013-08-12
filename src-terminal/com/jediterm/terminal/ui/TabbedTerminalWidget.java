package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

/**
 * @author traff
 */
public class TabbedTerminalWidget extends JPanel implements TerminalWidget {
  private static final int FIRST_TAB_NUMBER = 1;
  public static final String TAB_DEFAULT_NAME = "Session ";

  private int myTabNumber = FIRST_TAB_NUMBER;

  private TerminalPanelListener myTerminalPanelListener = null;

  private JediTermWidget myTermWidget = null;

  private JTabbedPane myTabbedPane;

  private SystemSettingsProvider mySystemSettingsProvider;

  public TabbedTerminalWidget(@NotNull SystemSettingsProvider settingsProvider) {
    super(new BorderLayout());
    mySystemSettingsProvider = settingsProvider;
  }

  @Override
  public TerminalSession createTerminalSession(TtyConnector ttyConnector) {
    final JediTermWidget terminal = createInnerTerminalWidget();
    terminal.setTtyConnector(ttyConnector);
    
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
    return new JediTermWidget(mySystemSettingsProvider);
  }

  private void addTab(JediTermWidget terminal, JTabbedPane tabbedPane) {
    //TODO: add number when we have the same names
    tabbedPane.addTab(terminal.getSessionName(), null, terminal);

    tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, new TabComponent(tabbedPane, terminal));
    tabbedPane.setSelectedComponent(terminal);
  }

  private JTabbedPane setupTabbedPane() {
    final JTabbedPane tabbedPane = createTabbedPane();

    tabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
        onSessionChanged();
      }
    });

    myTabNumber = FIRST_TAB_NUMBER;

    remove(myTermWidget);

    addTab(myTermWidget, tabbedPane);

    myTermWidget = null;

    add(tabbedPane, BorderLayout.CENTER);

    return tabbedPane;
  }

  public boolean isSingleSession() {
    return myTabbedPane == null && myTermWidget != null;
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

    JMenuItem newSession = new JMenuItem(mySystemSettingsProvider.getNewSessionAction());
    if (mySystemSettingsProvider.getNewSessionKeyStrokes().length > 0) {
      newSession.setAccelerator(mySystemSettingsProvider.getNewSessionKeyStrokes()[0]);
    }
    popupMenu.add(newSession);

    JMenuItem close = new JMenuItem("Close");
    close.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        close(terminal);
      }
    });

    popupMenu.add(close);

    return popupMenu;
  }

  private void close(JediTermWidget terminal) {
    terminal.close();
    if (myTabbedPane != null) {
      removeTab(terminal);
    } else {
      myTermWidget = null;
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
    return mySystemSettingsProvider;
  }
}
