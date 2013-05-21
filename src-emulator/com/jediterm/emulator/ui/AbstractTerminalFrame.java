package com.jediterm.emulator.ui;

import com.jediterm.emulator.RequestOrigin;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public abstract class AbstractTerminalFrame {


  public static final Logger LOG = Logger.getLogger(AbstractTerminalFrame.class);

  JFrame bufferFrame;

  private SwingTerminalPanel termPanel;

  private SwingJediTerminal terminal;

  private AbstractAction openAction = new AbstractAction("Open SHELL Session...") {
    public void actionPerformed(final ActionEvent e) {
      openSession(terminal);
    }
  };

  private AbstractAction showBuffersAction = new AbstractAction("Show buffers") {
    public void actionPerformed(final ActionEvent e) {
      if (bufferFrame == null) {
        showBuffers();
      }
    }
  };

  private AbstractAction resetDamage = new AbstractAction("Reset damage") {
    public void actionPerformed(final ActionEvent e) {
      if (termPanel != null) {
        termPanel.getBackBuffer().resetDamage();
      }
    }
  };

  private AbstractAction drawDamage = new AbstractAction("Draw from damage") {
    public void actionPerformed(final ActionEvent e) {
      if (termPanel != null) {
        termPanel.redraw();
      }
    }
  };

  private final JMenuBar getJMenuBar() {
    final JMenuBar mb = new JMenuBar();
    final JMenu m = new JMenu("File");

    m.add(openAction);
    mb.add(m);
    final JMenu dm = new JMenu("Debug");

    dm.add(showBuffersAction);
    dm.add(resetDamage);
    dm.add(drawDamage);
    mb.add(dm);

    return mb;
  }

  public abstract void openSession(SwingJediTerminal terminal);

  protected AbstractTerminalFrame() {
    terminal = new SwingJediTerminal();
    termPanel = terminal.getTermPanel();
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
    frame.getContentPane().add("Center", terminal);

    frame.pack();
    termPanel.setVisible(true);
    frame.setVisible(true);

    frame.setResizable(true);

    termPanel.setResizePanelDelegate(new ResizePanelDelegate() {
      public void resizedPanel(final Dimension pixelDimension, final RequestOrigin origin) {
        if (origin == RequestOrigin.Remote) {
          sizeFrameForTerm(frame);
        }
      }
    });
  }

  private void sizeFrameForTerm(final JFrame frame) {
    Dimension d = terminal.getPreferredSize();

    d.width += frame.getWidth() - frame.getContentPane().getWidth();
    d.height += frame.getHeight() - frame.getContentPane().getHeight();
    frame.setSize(d);
  }

private void showBuffers() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        bufferFrame = new JFrame("buffers");
        final JPanel panel = new BufferPanel(terminal);

        bufferFrame.getContentPane().add(panel);
        bufferFrame.pack();
        bufferFrame.setVisible(true);
        bufferFrame.setSize(800, 600);

        bufferFrame.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(final WindowEvent e) {
            bufferFrame = null;
          }
        });
      }
    });
  }
}
