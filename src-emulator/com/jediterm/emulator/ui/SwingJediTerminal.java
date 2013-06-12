package com.jediterm.emulator.ui;

import com.jediterm.emulator.Emulator;
import com.jediterm.emulator.TextStyle;
import com.jediterm.emulator.TtyChannel;
import com.jediterm.emulator.TtyConnector;
import com.jediterm.emulator.display.BackBuffer;
import com.jediterm.emulator.display.BufferedTerminalWriter;
import com.jediterm.emulator.display.LinesBuffer;
import com.jediterm.emulator.display.StyleState;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwingJediTerminal extends JPanel {
  private static final Logger LOG = Logger.getLogger(SwingJediTerminal.class);
  private static final long SERIAL_VERSION_UID = -8213232075937432833L;

  protected SwingTerminalPanel myTerminalPanel;
  protected BufferedTerminalWriter myTerminalWriter;
  protected AtomicBoolean mySessionRunning = new AtomicBoolean();
  protected PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TtyChannel myTtyChannel;
  private Emulator myEmulator;
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

    LinesBuffer scrollBuffer = new LinesBuffer();
    BackBuffer backBuffer = new BackBuffer(columns, lines, styleState, scrollBuffer);

    myTerminalPanel = createTerminalPanel(styleState, backBuffer, scrollBuffer);
    myTerminalWriter = new BufferedTerminalWriter(myTerminalPanel, backBuffer, styleState);
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

  protected SwingTerminalPanel createTerminalPanel(StyleState styleState, BackBuffer backBuffer, LinesBuffer scrollBuffer) {
    return new SwingTerminalPanel(backBuffer, scrollBuffer, styleState);
  }

  protected PreConnectHandler createPreConnectHandler(BufferedTerminalWriter writer) {
    return new PreConnectHandler(writer);
  }

  public SwingTerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public void setTtyConnectorAndInitEmulator(TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;
    myTtyChannel = new TtyChannel(ttyConnector);

    myEmulator = new Emulator(myTerminalWriter, myTtyChannel);
    myTerminalPanel.setEmulator(myEmulator);
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
          myEmulator.start();
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
    return new TerminalEmulatorKeyHandler(myEmulator);
  }

  public Emulator getEmulator() {
    return myEmulator;
  }
}
