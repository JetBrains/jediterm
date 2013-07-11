package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.JediTerminal;
import com.jediterm.terminal.display.StyleState;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 * <p/>
 */
public class JediTermWidget extends JPanel implements TerminalSession, TerminalWidget {
  private static final Logger LOG = Logger.getLogger(JediTermWidget.class);

  protected final TerminalPanel myTerminalPanel;
  protected final JediTerminal myTerminal;
  protected final AtomicBoolean mySessionRunning = new AtomicBoolean();
  protected PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TerminalStarter myTerminalStarter;
  private Thread myEmuThread;
  private final SystemSettingsProvider mySettingsProvider;

  public JediTermWidget(@NotNull SystemSettingsProvider settingsProvider) {
    this(80, 24, settingsProvider);
  }

  public JediTermWidget(Dimension dimension, SystemSettingsProvider settingsProvider) {
    this(dimension.width, dimension.height, settingsProvider);
  }

  public JediTermWidget(int columns, int lines, SystemSettingsProvider settingsProvider) {
    super(new BorderLayout());

    StyleState styleState = createDefaultStyle();

    BackBuffer backBuffer = new BackBuffer(columns, lines, styleState);

    mySettingsProvider = settingsProvider;
    myTerminalPanel = createTerminalPanel(mySettingsProvider, styleState, backBuffer);
    myTerminal = new JediTerminal(myTerminalPanel, backBuffer, styleState);
    
    myTerminalPanel.addTerminalMouseListener(myTerminal);
    
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
    styleState.setDefaultStyle(new TextStyle(TextStyle.FOREGROUND, TextStyle.BACKGROUND));
    return styleState;
  }

  protected TerminalPanel createTerminalPanel(SystemSettingsProvider settingsProvider, StyleState styleState, BackBuffer backBuffer) {
    return new TerminalPanel(settingsProvider, backBuffer, styleState);
  }

  protected PreConnectHandler createPreConnectHandler(JediTerminal writer) {
    return new PreConnectHandler(writer);
  }

  public TerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;

    myTerminalStarter = new TerminalStarter(myTerminal, myTtyConnector);
    myTerminalPanel.setTerminalStarter(myTerminalStarter);
  }

  public TtyConnector getTtyConnector() {
    return myTtyConnector;
  }

  @Override
  public String getSessionName() {
    return myTerminalPanel.getWindowTitle();
  }

  @Override
  public void redraw() {
    myTerminalPanel.redraw();
  }

  public void start() {
    if (!mySessionRunning.get()) {
      myEmuThread = new Thread(new EmulatorTask());
      myEmuThread.start();
    }
    else {
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
  public TerminalSession createTerminalSession() {
    return this;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  public void close() {
    myTerminalStarter.close();
  }

  class EmulatorTask implements Runnable {
    public void run() {
      try {
        mySessionRunning.set(true);
        Thread.currentThread().setName(myTtyConnector.getName());
        if (myTtyConnector.init(myPreConnectHandler)) {
          Thread.currentThread().setName(myTtyConnector.getName());
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTerminalPanel.initKeyHandler();
              myTerminalPanel.requestFocusInWindow();
            }
          });
          myTerminalStarter.start();
        }
      }
      catch (Exception e) {
        LOG.error("Exception running terminal", e);
      }
      finally {
        try {
          myTtyConnector.close();
        }
        catch (Exception e) {
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
