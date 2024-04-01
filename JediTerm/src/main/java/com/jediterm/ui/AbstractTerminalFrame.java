package com.jediterm.ui;

import com.jediterm.app.JediTerm;
import com.jediterm.app.TtyConnectorWaitFor;
import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.ui.debug.TerminalDebugView;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public abstract class AbstractTerminalFrame {
  public static final Logger LOG = LoggerFactory.getLogger(AbstractTerminalFrame.class);
  private JFrame myBufferFrame;

  private final JediTermWidget myWidget;

  private final AbstractAction myShowBuffersAction = new AbstractAction("Show buffers") {
    public void actionPerformed(final ActionEvent e) {
      showBuffers();
    }
  };

  private final AbstractAction myDumpDimension = new AbstractAction("Dump terminal dimension") {
    public void actionPerformed(final ActionEvent e) {
      Terminal terminal = myWidget.getTerminal();
      LOG.info(terminal.getTerminalWidth() + "x" + terminal.getTerminalHeight());
    }
  };

  private final AbstractAction myDumpSelection = new AbstractAction("Dump selection") {
    public void actionPerformed(final ActionEvent e) {
      JediTermWidget widget = myWidget;
      TerminalPanel terminalPanel = widget.getTerminalPanel();
      TerminalSelection selection = terminalPanel.getSelection();
      if (selection != null) {
        Pair<Point, Point> points = selection.pointsForRun(widget.getTerminal().getTerminalWidth());
        LOG.info(selection + " : '"
          + SelectionUtil.getSelectionText(points.getFirst(), points.getSecond(), terminalPanel.getTerminalTextBuffer()) + "'");
      }
      else {
        LOG.info("No selection");
      }
    }
  };

  private final AbstractAction myDumpCursorPosition = new AbstractAction("Dump cursor position") {
    public void actionPerformed(final ActionEvent e) {
      LOG.info(myWidget.getTerminal().getCursorX() +
        "x" + myWidget.getTerminal().getCursorY());
    }
  };

  private final AbstractAction myCursor0x0 = new AbstractAction("1x1") {
    public void actionPerformed(final ActionEvent e) {
      myWidget.getTerminal().cursorPosition(1, 1);
    }
  };

  private final AbstractAction myCursor10x10 = new AbstractAction("10x10") {
    public void actionPerformed(final ActionEvent e) {
      myWidget.getTerminal().cursorPosition(10, 10);
    }
  };

  private final AbstractAction myCursor80x24 = new AbstractAction("80x24") {
    public void actionPerformed(final ActionEvent e) {
      myWidget.getTerminal().cursorPosition(80, 24);
    }
  };

  private JMenuBar getJMenuBar() {
    final JMenuBar mb = new JMenuBar();
    final JMenu dm = new JMenu("Debug");

    JMenu logLevel = new JMenu("Set log level ...");
    Level[] levels = new Level[]{Level.ALL, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE, Level.OFF};
    for (final Level l : levels) {
      logLevel.add(new AbstractAction(l.toString()) {
        @Override
        public void actionPerformed(ActionEvent e) {
          java.util.logging.Logger.getLogger("").setLevel(l);
        }
      });
    }
    dm.add(logLevel);
    dm.addSeparator();

    dm.add(myShowBuffersAction);
    dm.addSeparator();
    dm.add(myDumpDimension);
    dm.add(myDumpSelection);
    dm.add(myDumpCursorPosition);

    JMenu cursorPosition = new JMenu("Set cursor position ...");
    cursorPosition.add(myCursor0x0);
    cursorPosition.add(myCursor10x10);
    cursorPosition.add(myCursor80x24);
    dm.add(cursorPosition);
    mb.add(dm);

    return mb;
  }

  protected void openSession(TerminalWidget terminal) {
    if (terminal.canOpenSession()) {
      openSession(terminal, createTtyConnector());
    }
  }

  public void openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
    JediTermWidget session = terminal.createTerminalSession(ttyConnector);
    if (ttyConnector instanceof JediTerm.LoggingPtyProcessTtyConnector) {
      ((JediTerm.LoggingPtyProcessTtyConnector) ttyConnector).setWidget(session);
    }
    session.start();
  }

  public abstract TtyConnector createTtyConnector();

  protected AbstractTerminalFrame() {
    myWidget = createTerminalWidget(new DefaultSettingsProvider());

    final JFrame frame = new JFrame("JediTerm");

    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(final WindowEvent e) {
        System.exit(0);
      }
    });

    final JMenuBar mb = getJMenuBar();
    frame.setJMenuBar(mb);
    sizeFrameForTerm(frame);
    frame.getContentPane().add("Center", myWidget.getComponent());

    frame.pack();
    frame.setLocationByPlatform(true);
    frame.setVisible(true);

    frame.setResizable(true);

    myWidget.getTerminal().addApplicationTitleListener(frame::setTitle);

    openSession(myWidget);
    onTermination(myWidget, exitCode -> {
      myWidget.close();
      frame.dispose();
      System.exit(exitCode); // unneeded, but speeds up the JVM termination
    });
  }

  private static void onTermination(@NotNull JediTermWidget widget, @NotNull IntConsumer terminationCallback) {
    new TtyConnectorWaitFor(widget.getTtyConnector(),
      widget.getExecutorServiceManager().getUnboundedExecutorService(),
      terminationCallback);
  }

  protected JediTermWidget createTerminalWidget(@NotNull SettingsProvider settingsProvider) {
    return new JediTermWidget(settingsProvider);
  }

  private void sizeFrameForTerm(final JFrame frame) {
    SwingUtilities.invokeLater(() -> {
      Dimension d = myWidget.getPreferredSize();

      d.width += frame.getWidth() - frame.getContentPane().getWidth();
      d.height += frame.getHeight() - frame.getContentPane().getHeight();
      frame.setSize(d);
    });
  }

  private void showBuffers() {
    if (myBufferFrame != null) {
      myBufferFrame.requestFocus();
      return;
    }
    myBufferFrame = new JFrame("buffers");
    TerminalDebugView debugView = new TerminalDebugView(myWidget);
    myBufferFrame.getContentPane().add(debugView.getComponent());
    myBufferFrame.pack();
    myBufferFrame.setLocationByPlatform(true);
    myBufferFrame.setVisible(true);
    myBufferFrame.setSize(1600, 800);

    closeOnEscape(myBufferFrame);
    myBufferFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    myBufferFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(final WindowEvent e) {
        myBufferFrame = null;
        debugView.stop();
      }
    });
  }

  private void closeOnEscape(JFrame frame) {
    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "Cancel");
    frame.getRootPane().getActionMap().put("Cancel", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        frame.dispose();
      }
    });
  }
}
