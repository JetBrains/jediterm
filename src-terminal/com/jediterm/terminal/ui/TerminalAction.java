package com.jediterm.terminal.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author traff
 */
public class TerminalAction {
  private final KeyStroke[] myKeyStrokes;
  private final Runnable myRunnable;

  public TerminalAction(KeyStroke[] keyStrokes, Runnable runnable) {
    myKeyStrokes = keyStrokes;
    myRunnable = runnable;
  }

  public KeyStroke[] getKeyStrokes() {
    return myKeyStrokes;
  }

  public Runnable getRunnable() {
    return myRunnable;
  }

  public boolean matches(KeyEvent e) {
    for (KeyStroke ks : myKeyStrokes) {
      if (ks.equals(KeyStroke.getKeyStrokeForEvent(e))) {
        return true;
      }
    }
    return false;  
  }

  public void perform() {
    myRunnable.run();
  }

  public static boolean processEvent(TerminalActionProvider actionProvider, final KeyEvent e) {
    for (TerminalAction a: actionProvider.getActions()) {
      if (a.matches(e)) {
        a.perform();
        return true;
      }
    }

    if (actionProvider.getNextProvider() != null) {
      if (processEvent(actionProvider.getNextProvider(), e)) {
        return true;
      }
    }

    return false;
  }


  public int getKeyCode() {
    for (KeyStroke ks: myKeyStrokes) {
      return ks.getKeyCode();
    }
    return 0;
  }

  public int getModifiers() {
    for (KeyStroke ks: myKeyStrokes) {
      return ks.getModifiers();
    }
    return 0;
  }
}
