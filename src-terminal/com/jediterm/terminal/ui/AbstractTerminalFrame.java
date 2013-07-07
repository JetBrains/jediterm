package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.debug.BufferPanel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public abstract class AbstractTerminalFrame {
  public static final Logger LOG = Logger.getLogger(AbstractTerminalFrame.class);

  private JFrame myBufferFrame;

  private TerminalWidget myTerminal;

  private AbstractAction myOpenAction = new AbstractAction("New Session...") {
    public void actionPerformed(final ActionEvent e) {
      openSession();
    }
  };

  private AbstractAction myResetDamage = new AbstractAction("Reset damage") {
    public void actionPerformed(final ActionEvent e) {
      myTerminal.getCurrentSession().getBackBuffer().resetDamage();
    }
  };

  private AbstractAction myDrawDamage = new AbstractAction("Draw from damage") {
    public void actionPerformed(final ActionEvent e) {
      myTerminal.getCurrentSession().redraw();
    }
  };

  private AbstractAction myShowBuffersAction = new AbstractAction("Show buffers") {
    public void actionPerformed(final ActionEvent e) {
      if (myBufferFrame == null) {
        showBuffers();
      }
    }
  };

  private JMenuBar getJMenuBar() {
    final JMenuBar mb = new JMenuBar();
    final JMenu m = new JMenu("File");

    m.add(myOpenAction);
    mb.add(m);
    final JMenu dm = new JMenu("Debug");

    dm.add(myShowBuffersAction);
    dm.add(myResetDamage);
    dm.add(myDrawDamage);
    mb.add(dm);

    return mb;
  }

  private void openSession() {
    if (myTerminal.canOpenSession()) {
      openSession(myTerminal, createTtyConnector());
    }
  }

  public void openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession();
    session.setTtyConnector(ttyConnector);
    session.start();
  }

  public abstract TtyConnector createTtyConnector();

  protected AbstractTerminalFrame() {
    myTerminal = new TabbedTerminalWidget();
    
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
    frame.getContentPane().add("Center", myTerminal.getComponent());

    frame.pack();
    frame.setVisible(true);

    frame.setResizable(true);

    myTerminal.setResizePanelDelegate(new ResizePanelDelegate() {
      public void onPanelResize(final Dimension pixelDimension, final RequestOrigin origin) {
        if (origin == RequestOrigin.Remote) {
          sizeFrameForTerm(frame);
        }
        frame.pack();
      }
    });

    openSession();
  }

  private void sizeFrameForTerm(final JFrame frame) {
    Dimension d = myTerminal.getPreferredSize();

    d.width += frame.getWidth() - frame.getContentPane().getWidth();
    d.height += frame.getHeight() - frame.getContentPane().getHeight();
    frame.setSize(d);
  }

  private void showBuffers() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myBufferFrame = new JFrame("buffers");
        final JPanel panel = new BufferPanel(myTerminal.getCurrentSession());

        myBufferFrame.getContentPane().add(panel);
        myBufferFrame.pack();
        myBufferFrame.setVisible(true);
        myBufferFrame.setSize(800, 600);

        myBufferFrame.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(final WindowEvent e) {
            myBufferFrame = null;
          }
        });
      }
    });
  }
}
