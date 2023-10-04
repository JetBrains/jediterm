package com.jediterm.ui;

import com.jediterm.app.JediTerm;
import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.ui.debug.TerminalDebugView;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import com.jediterm.terminal.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.Level;

public abstract class AbstractTerminalFrame {
  public static final Logger LOG = LoggerFactory.getLogger(AbstractTerminalFrame.class);
  private JFrame myBufferFrame;

  private final AbstractTabbedTerminalWidget<JediTermWidget> myTerminal;
  private final JFrame myFrame;

  private final AbstractAction myOpenAction = new AbstractAction("New Session") {
    public void actionPerformed(final ActionEvent e) {
      openSession(myTerminal);
    }
  };

  private final AbstractAction myShowBuffersAction = new AbstractAction("Show buffers") {
    public void actionPerformed(final ActionEvent e) {
      showBuffers();
    }
  };

  private final AbstractAction myDumpDimension = new AbstractAction("Dump terminal dimension") {
    public void actionPerformed(final ActionEvent e) {
      Terminal terminal = myTerminal.getCurrentSession().getTerminal();
      LOG.info(terminal.getTerminalWidth() + "x" + terminal.getTerminalHeight());
    }
  };

  private final AbstractAction myDumpSelection = new AbstractAction("Dump selection") {
    public void actionPerformed(final ActionEvent e) {
      JediTermWidget widget = myTerminal.getCurrentSession();
      TerminalPanel terminalPanel = widget.getTerminalPanel();
      TerminalSelection selection = terminalPanel.getSelection();
      if (selection != null) {
        Pair<Point, Point> points = selection.pointsForRun(widget.getTerminal().getTerminalWidth());
        LOG.info(selection + " : '"
          + SelectionUtil.getSelectionText(points.first, points.second, terminalPanel.getTerminalTextBuffer()) + "'");
      }
      else {
        LOG.info("No selection");
      }
    }
  };

  private final AbstractAction myDumpCursorPosition = new AbstractAction("Dump cursor position") {
    public void actionPerformed(final ActionEvent e) {
      LOG.info(myTerminal.getCurrentSession().getTerminal().getCursorX() +
        "x" + myTerminal.getCurrentSession().getTerminal().getCursorY());
    }
  };

  private final AbstractAction myCursor0x0 = new AbstractAction("1x1") {
    public void actionPerformed(final ActionEvent e) {
      myTerminal.getCurrentSession().getTerminal().cursorPosition(1, 1);
    }
  };

  private final AbstractAction myCursor10x10 = new AbstractAction("10x10") {
    public void actionPerformed(final ActionEvent e) {
      myTerminal.getCurrentSession().getTerminal().cursorPosition(10, 10);
    }
  };

  private final AbstractAction myCursor80x24 = new AbstractAction("80x24") {
    public void actionPerformed(final ActionEvent e) {
      myTerminal.getCurrentSession().getTerminal().cursorPosition(80, 24);
    }
  };

  private JMenuBar getJMenuBar() {
    final JMenuBar mb = new JMenuBar();
    final JMenu m = new JMenu("File");

    m.add(myOpenAction);
    mb.add(m);
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

  @Nullable
  protected JediTermWidget openSession(TerminalWidget terminal) {
    if (terminal.canOpenSession()) {
      return openSession(terminal, createTtyConnector());
    }
    return null;
  }

  public JediTermWidget openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
    JediTermWidget session = terminal.createTerminalSession(ttyConnector);
    if (ttyConnector instanceof JediTerm.LoggingPtyProcessTtyConnector) {
      ((JediTerm.LoggingPtyProcessTtyConnector) ttyConnector).setWidget(session);
    }
    session.start();
    return session;
  }

  public abstract TtyConnector createTtyConnector();

  protected AbstractTerminalFrame() {
    AbstractTabbedTerminalWidget<? extends JediTermWidget> tabbedTerminalWidget = createTabbedTerminalWidget();
    //noinspection unchecked
    myTerminal = (AbstractTabbedTerminalWidget<JediTermWidget>) tabbedTerminalWidget;

    final JFrame frame = new JFrame("JediTerm");
    myFrame = frame;

    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(final WindowEvent e) {
        System.exit(0);
      }
    });

    final JMenuBar mb = getJMenuBar();
    frame.setJMenuBar(mb);
    sizeFrameForTerm(frame);
    frame.getContentPane().add("Center", myTerminal.getComponent());

    frame.pack();
    frame.setLocationByPlatform(true);
    frame.setVisible(true);

    frame.setResizable(true);

    tabbedTerminalWidget.addTabListener(new MyTabListener<>());
    myTerminal.setTerminalTitleListener(frame::setTitle);

    openSession(myTerminal);
  }

  @NotNull
  protected AbstractTabbedTerminalWidget<? extends JediTermWidget> createTabbedTerminalWidget() {
    return new TabbedTerminalWidget(new DefaultTabbedSettingsProvider(), this::openSession) {
      @Override
      public JediTermWidget createInnerTerminalWidget() {
        return createTerminalWidget(getSettingsProvider());
      }
    };
  }

  protected JediTermWidget createTerminalWidget(@NotNull TabbedSettingsProvider settingsProvider) {
    return new JediTermWidget(settingsProvider);
  }

  private void sizeFrameForTerm(final JFrame frame) {
    SwingUtilities.invokeLater(() -> {
      Dimension d = myTerminal.getPreferredSize();

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
    TerminalDebugView debugView = new TerminalDebugView(myTerminal.getCurrentSession());
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

  private class MyTabListener<T extends JediTermWidget> implements AbstractTabbedTerminalWidget.TabListener<T> {
    @Override
    public void tabClosed(@NotNull T terminal) {
      AbstractTabs<?> tabs = myTerminal.getTerminalTabs();
      if (tabs == null || tabs.getTabCount() == 0) {
        System.exit(0);
      }
    }

    @Override
    public void onSelectedTabChanged(@NotNull T terminal) {
      myFrame.setTitle(myTerminal.getTabName(terminal));
    }
  }
}
