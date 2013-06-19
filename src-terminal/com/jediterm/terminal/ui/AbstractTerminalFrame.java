package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.debug.BufferPanel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public abstract class AbstractTerminalFrame {


  public static final Logger LOG = Logger.getLogger(AbstractTerminalFrame.class);

  JFrame myBufferFrame;

  private SwingTerminalPanel myTerminalPanel;

  private SwingJediTerminal myTerminal;

  private AbstractAction myOpenAction = new AbstractAction("Open SHELL Session...") {
    public void actionPerformed(final ActionEvent e) {
      openSession();
    }
  };

  private AbstractAction myResetDamage = new AbstractAction("Reset damage") {
    public void actionPerformed(final ActionEvent e) {
      if (myTerminalPanel != null) {
        myTerminalPanel.getBackBuffer().resetDamage();
      }
    }
  };

  private AbstractAction myDrawDamage = new AbstractAction("Draw from damage") {
    public void actionPerformed(final ActionEvent e) {
      if (myTerminalPanel != null) {
        myTerminalPanel.redraw();
      }
    }
  };

  private AbstractAction myShowBuffersAction = new AbstractAction("Show buffers") {
    public void actionPerformed(final ActionEvent e) {
      if (myBufferFrame == null) {
        showBuffers();
      }
    }
  };

  private final JMenuBar getJMenuBar() {
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
    if (!myTerminal.getSessionRunning()) {
      openSession(myTerminal);
    }
  }

  public abstract void openSession(SwingJediTerminal terminal);

  protected AbstractTerminalFrame() {
    myTerminal = new SwingJediTerminal();
    myTerminalPanel = myTerminal.getTerminalPanel();
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
    frame.getContentPane().add("Center", myTerminal);

    frame.pack();
    myTerminalPanel.setVisible(true);
    frame.setVisible(true);

    frame.setResizable(true);

    myTerminalPanel.setResizePanelDelegate(new ResizePanelDelegate() {
      public void resizedPanel(final Dimension pixelDimension, final RequestOrigin origin) {
        if (origin == RequestOrigin.Remote) {
          sizeFrameForTerm(frame);
        }
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
        final JPanel panel = new BufferPanel(myTerminal);

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
