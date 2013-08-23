package com.jediterm.terminal.ui;

import com.google.common.collect.Lists;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 * <p/>
 */
public class JediTermWidget extends JPanel implements TerminalSession, TerminalWidget, TerminalActionProvider {
  private static final Logger LOG = Logger.getLogger(JediTermWidget.class);

  protected final TerminalPanel myTerminalPanel;
  protected final JediTerminal myTerminal;
  protected final AtomicBoolean mySessionRunning = new AtomicBoolean();
  protected PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TerminalStarter myTerminalStarter;
  private Thread myEmuThread;
  private final SettingsProvider mySettingsProvider;
  private TerminalActionProvider myNextActionProvider;

  public JediTermWidget(@NotNull SettingsProvider settingsProvider) {
    this(80, 24, settingsProvider);
  }

  public JediTermWidget(Dimension dimension, SettingsProvider settingsProvider) {
    this(dimension.width, dimension.height, settingsProvider);
  }

  public JediTermWidget(int columns, int lines, SettingsProvider settingsProvider) {
    super(new BorderLayout());

    mySettingsProvider = settingsProvider;

    StyleState styleState = createDefaultStyle();
    BackBuffer backBuffer = new BackBuffer(columns, lines, styleState);

    myTerminalPanel = createTerminalPanel(mySettingsProvider, styleState, backBuffer);
    myTerminal = new JediTerminal(myTerminalPanel, backBuffer, styleState);

    myTerminalPanel.addTerminalMouseListener(myTerminal);
    myTerminalPanel.setNextProvider(this);

    myPreConnectHandler = createPreConnectHandler(myTerminal);
    myTerminalPanel.setKeyListener(myPreConnectHandler);
    JScrollBar scrollBar = createScrollBar();

    add(myTerminalPanel, BorderLayout.CENTER);
    add(scrollBar, BorderLayout.EAST);
    scrollBar.setModel(myTerminalPanel.getBoundedRangeModel());
    mySessionRunning.set(false);

    myTerminalPanel.init();

    myTerminalPanel.setVisible(true);
  }


  protected JScrollBar createScrollBar() {
    return new JScrollBar();
  }

  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());
    return styleState;
  }

  protected TerminalPanel createTerminalPanel(SettingsProvider settingsProvider, StyleState styleState, BackBuffer backBuffer) {
    return new TerminalPanel(settingsProvider, backBuffer, styleState);
  }

  protected PreConnectHandler createPreConnectHandler(JediTerminal terminal) {
    return new PreConnectHandler(terminal);
  }

  public TerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;

    myTerminalStarter = createTerminalStarter(myTerminal, myTtyConnector);
    myTerminalPanel.setTerminalStarter(myTerminalStarter);
  }

  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new TerminalStarter(terminal, connector);
  }

  public TtyConnector getTtyConnector() {
    return myTtyConnector;
  }

  @Override
  public String getSessionName() {
    if (myTtyConnector != null) {
      return myTtyConnector.getName();
    } else {
      return "Session";
    }
  }

  @Override
  public void redraw() {
    myTerminalPanel.redraw();
  }

  public void start() {
    if (!mySessionRunning.get()) {
      myEmuThread = new Thread(new EmulatorTask());
      myEmuThread.start();
    } else {
      LOG.error("Should not try to start session again at this point... ");
    }
  }

  public void stop() {
    if (mySessionRunning.get() && myEmuThread != null) {
      myEmuThread.interrupt();
    }
  }

  public boolean isSessionRunning() {
    return mySessionRunning.get();
  }

  public String getBufferText(DebugBufferType type) {
    return type.getValue(this);
  }

  @Override
  public BackBuffer getBackBuffer() {
    return myTerminalPanel.getBackBuffer();
  }

  @Override
  public boolean requestFocusInWindow() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTerminalPanel.requestFocusInWindow();
      }
    });
    return super.requestFocusInWindow();
  }

  public boolean canOpenSession() {
    return !isSessionRunning();
  }

  @Override
  public void setTerminalPanelListener(TerminalPanelListener terminalPanelListener) {
    myTerminalPanel.setTerminalPanelListener(terminalPanelListener);
  }

  @Override
  public TerminalSession getCurrentSession() {
    return this;
  }

  @Override
  public TerminalSession createTerminalSession(TtyConnector ttyConnector) {
    setTtyConnector(ttyConnector);
    return this;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  public void close() {
    myTerminalStarter.close();
  }

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList();
  }

  @Override
  public TerminalActionProvider getNextProvider() {
    return myNextActionProvider;
  }

  public void setNextProvider(TerminalActionProvider actionProvider) {
    this.myNextActionProvider = actionProvider;
  }

  class EmulatorTask implements Runnable {
    public void run() {
      try {
        mySessionRunning.set(true);
        Thread.currentThread().setName("Connector-" + myTtyConnector.getName());
        if (myTtyConnector.init(myPreConnectHandler)) {
          myTerminalPanel.initKeyHandler();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTerminalPanel.requestFocusInWindow();
            }
          });
          myTerminalStarter.start();
        }
      } catch (Exception e) {
        LOG.error("Exception running terminal", e);
      } finally {
        try {
          myTtyConnector.close();
        } catch (Exception e) {
        }
        mySessionRunning.set(false);
        myTerminalPanel.setKeyListener(myPreConnectHandler);
      }
    }
  }

  public TerminalStarter getTerminalStarter() {
    return myTerminalStarter;
  }
}
