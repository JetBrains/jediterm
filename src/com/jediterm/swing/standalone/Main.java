package com.jediterm.swing.standalone;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jediterm.swing.SwingJediTerminal;
import com.jediterm.swing.SwingTerminalPanel;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.jediterm.RequestOrigin;
import com.jediterm.ResizePanelDelegate;
import com.jediterm.jsch.JSchTty;
import com.jediterm.swing.GrittyTerminal;


public class Main {


  public static final Logger logger = Logger.getLogger(Main.class);
  JFrame bufferFrame;

  private SwingTerminalPanel termPanel;

  private SwingJediTerminal terminal;

  private AbstractAction openAction = new AbstractAction("Open SHELL Session...") {
    public void actionPerformed(final ActionEvent e) {
      openSession();
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
        termPanel.redrawFromDamage();
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

  public void openSession() {
    if (!terminal.isSessionRunning()) {
      terminal.setTty(new JSchTty());
      terminal.start();
    }
  }

  Main() {
    terminal = new SwingJediTerminal();
    termPanel = terminal.getTermPanel();
    final JFrame frame = new JFrame("Gritty");

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

  public static void main(final String[] arg) {
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.INFO);
    new Main();
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
