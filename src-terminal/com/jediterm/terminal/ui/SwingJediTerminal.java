package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyChannel;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.BufferedDisplayTerminal;
import com.jediterm.terminal.display.StyleState;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 *
 * TODO: needs to be renamed to JediTermWidget or JediTermComponent
 */
public class SwingJediTerminal extends JPanel {
  private static final Logger LOG = Logger.getLogger(SwingJediTerminal.class);
  private static final long SERIAL_VERSION_UID = -8213232075937432833L;

  protected SwingTerminalPanel myTerminalPanel;
  protected BufferedDisplayTerminal myTerminalWriter;
  protected AtomicBoolean mySessionRunning = new AtomicBoolean();
  protected PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TtyChannel myTtyChannel;
  private TerminalStarter myTerminalStarter;
  private Thread myEmuThread;

  public SwingJediTerminal() {
    this(80, 24);
  }

  public SwingJediTerminal(Dimension dimension) {
    this(dimension.width, dimension.height);
  }

  public SwingJediTerminal(int columns, int lines) {
    super(new BorderLayout());

    StyleState styleState = createDefaultStyle();

    BackBuffer backBuffer = new BackBuffer(columns, lines, styleState);

    myTerminalPanel = createTerminalPanel(styleState, backBuffer);
    myTerminalWriter = new BufferedDisplayTerminal(myTerminalPanel, backBuffer, styleState);
    myPreConnectHandler = createPreConnectHandler(myTerminalWriter);
    myTerminalPanel.setKeyListener(myPreConnectHandler);
    JScrollBar scrollBar = createScrollBar();

    add(myTerminalPanel, BorderLayout.CENTER);
    add(scrollBar, BorderLayout.EAST);
    scrollBar.setModel(myTerminalPanel.getBoundedRangeModel());
    mySessionRunning.set(false);

    myTerminalPanel.init();
  }

  protected JScrollBar createScrollBar() {
    return new JScrollBar();
  }

  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(new TextStyle(TextStyle.FOREGROUND, TextStyle.BACKGROUND));
    return styleState;
  }

  protected SwingTerminalPanel createTerminalPanel(StyleState styleState, BackBuffer backBuffer) {
    return new SwingTerminalPanel(backBuffer, styleState);
  }

  protected PreConnectHandler createPreConnectHandler(BufferedDisplayTerminal writer) {
    return new PreConnectHandler(writer);
  }

  public SwingTerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public void setTtyConnector(TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;
    myTtyChannel = new TtyChannel(ttyConnector);

    myTerminalStarter = new TerminalStarter(myTerminalWriter, myTtyChannel);
    myTerminalPanel.setTerminalStarter(myTerminalStarter);
  }

  public TtyConnector getTtyConnector() {
    return myTtyConnector;
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

  public boolean getSessionRunning() {
    return mySessionRunning.get();
  }

  public String getBufferText(DebugBufferType type) {
    return type.getValue(this);
  }

  public void sendCommand(String string) throws IOException {
    if (mySessionRunning.get()) {
      myTtyChannel.sendBytes(string.getBytes());
    }
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

  class EmulatorTask implements Runnable {
    public void run() {
      try {
        mySessionRunning.set(true);
        Thread.currentThread().setName(myTtyConnector.getName());
        if (myTtyConnector.init(myPreConnectHandler)) {
          Thread.currentThread().setName(myTtyConnector.getName());
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTerminalPanel.setKeyListener(createEmulatorKeyHandler());
              myTerminalPanel.requestFocusInWindow();
            }
          });
          myTerminalStarter.start();
        }
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

  protected KeyListener createEmulatorKeyHandler() {
    return new TerminalEmulatorKeyHandler(myTerminalStarter);
  }

  public TerminalStarter getTerminalStarter() {
    return myTerminalStarter;
  }
}
