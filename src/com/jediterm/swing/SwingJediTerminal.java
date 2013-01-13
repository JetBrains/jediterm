package com.jediterm.swing;

import com.jediterm.*;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwingJediTerminal extends JPanel {
  private static final Logger logger = Logger.getLogger(SwingJediTerminal.class);
  private static final long serialVersionUID = -8213232075937432833L;
  protected SwingTerminalPanel termPanel;
  protected BackBufferTerminalWriter terminalWriter;
  protected AtomicBoolean sessionRunning = new AtomicBoolean();
  protected PreConnectHandler preconnectHandler;
  private Tty tty;
  private TtyChannel ttyChannel;
  private Emulator emulator;
  private Thread emuThread;

  public SwingJediTerminal() {
    super(new BorderLayout());

    StyleState styleState = createDefaultStyle();

    TextBuffer scrollBuffer = new TextBuffer();
    BackBuffer backBuffer = new BackBuffer(80, 24, styleState);


    termPanel = createTerminalPanel(styleState, backBuffer, scrollBuffer);
    terminalWriter = new BackBufferTerminalWriter(termPanel, backBuffer, styleState);
    preconnectHandler = new PreConnectHandler(terminalWriter);
    termPanel.setKeyHandler(preconnectHandler);
    JScrollBar scrollBar = new JScrollBar();

    add(termPanel, BorderLayout.CENTER);
    add(scrollBar, BorderLayout.EAST);
    scrollBar.setModel(termPanel.getBoundedRangeModel());
    sessionRunning.set(false);

    termPanel.init();
  }


  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(new TextStyle(TextStyle.FOREGROUND, TextStyle.BACKGROUND));
    return styleState;
  }

  protected SwingTerminalPanel createTerminalPanel(StyleState styleState, BackBuffer backBuffer, TextBuffer scrollBuffer) {
    return new SwingTerminalPanel(backBuffer, scrollBuffer, styleState);
  }

  public SwingTerminalPanel getTermPanel() {
    return termPanel;
  }

  public void setTty(Tty tty) {
    this.tty = tty;
    ttyChannel = new TtyChannel(tty);

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
        Thread.currentThread().setName(tty.getName());
        if (tty.init(preconnectHandler)) {
          Thread.currentThread().setName(tty.getName());
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              termPanel.setKeyHandler(new ConnectedKeyHandler(emulator));
              termPanel.requestFocusInWindow();
            }
          });
          emulator.start();
        }
      }
      finally {
        try {
          tty.close();
        }
        catch (Exception e) {
        }
        sessionRunning.set(false);
        termPanel.setKeyHandler(preconnectHandler);
      }
    }
  }
}
