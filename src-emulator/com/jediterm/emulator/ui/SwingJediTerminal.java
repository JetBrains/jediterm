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
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwingJediTerminal extends JPanel {
  private static final Logger logger = Logger.getLogger(SwingJediTerminal.class);
  private static final long serialVersionUID = -8213232075937432833L;
  protected SwingTerminalPanel termPanel;
  protected BufferedTerminalWriter terminalWriter;
  protected AtomicBoolean sessionRunning = new AtomicBoolean();
  protected PreConnectHandler preconnectHandler;
  private TtyConnector myTtyConnector;
  private TtyChannel ttyChannel;
  private Emulator emulator;
  private Thread emuThread;

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

    termPanel = createTerminalPanel(styleState, backBuffer, scrollBuffer);
    terminalWriter = new BufferedTerminalWriter(termPanel, backBuffer, styleState);
    preconnectHandler = createPreConnectHandler(terminalWriter);
    termPanel.setKeyListener(preconnectHandler);
    JScrollBar scrollBar = createScrollBar();

    add(termPanel, BorderLayout.CENTER);
    add(scrollBar, BorderLayout.EAST);
    scrollBar.setModel(termPanel.getBoundedRangeModel());
    sessionRunning.set(false);

    termPanel.init();
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

  public SwingTerminalPanel getTermPanel() {
    return termPanel;
  }

  public void setTtyConnector(TtyConnector ttyConnector) {
    this.myTtyConnector = ttyConnector;
    ttyChannel = new TtyChannel(ttyConnector);

    emulator = new Emulator(terminalWriter, ttyChannel);
    this.termPanel.setEmulator(emulator);
  }

  public void start() {
    if (!sessionRunning.get()) {
      emuThread = new Thread(new EmulatorTask());
      emuThread.start();
    }
    else {
      logger.error("Should not try to start session again at this point... ");
    }
  }

  public void stop() {
    if (sessionRunning.get() && emuThread != null) {
      emuThread.interrupt();
    }
  }

  public boolean isSessionRunning() {
    return sessionRunning.get();
  }

  public String getBufferText(BufferType type) {
    return type.getValue(this);
  }

  public void sendCommand(String string) throws IOException {
    if (sessionRunning.get()) {
      ttyChannel.sendBytes(string.getBytes());
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        termPanel.requestFocusInWindow();
      }
    });
    return super.requestFocusInWindow();
  }

  public static enum BufferType {
    Back() {
      String getValue(SwingJediTerminal term) {
        return term.getTermPanel().getBackBuffer().getLines();
      }
    },
    BackStyle() {
      String getValue(SwingJediTerminal term) {
        return term.getTermPanel().getBackBuffer().getStyleLines();
      }
    },
    Damage() {
      String getValue(SwingJediTerminal term) {
        return term.getTermPanel().getBackBuffer().getDamageLines();
      }
    },
    Scroll() {
      String getValue(SwingJediTerminal term) {
        return term.getTermPanel().getScrollBuffer().getLines();
      }
    };

    abstract String getValue(SwingJediTerminal term);
  }

  class EmulatorTask implements Runnable {
    public void run() {
      try {
        sessionRunning.set(true);
        Thread.currentThread().setName(myTtyConnector.getName());
        if (myTtyConnector.init(preconnectHandler)) {
          Thread.currentThread().setName(myTtyConnector.getName());
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              termPanel.setKeyListener(new ConnectedKeyHandler(emulator));
              termPanel.requestFocusInWindow();
            }
          });
          emulator.start();
        }
      }
      finally {
        try {
          myTtyConnector.close();
        }
        catch (Exception e) {
        }
        sessionRunning.set(false);
        termPanel.setKeyListener(preconnectHandler);
      }
    }
  }
}
