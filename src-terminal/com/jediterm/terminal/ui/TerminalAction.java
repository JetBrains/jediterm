package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author traff
 */
public class TerminalAction {
  private final KeyStroke[] myKeyStrokes;
  private final Predicate<KeyEvent> myRunnable;

  public TerminalAction(KeyStroke[] keyStrokes, Predicate<KeyEvent> runnable) {
    myKeyStrokes = keyStrokes;
    myRunnable = runnable;
  }

  public boolean matches(KeyEvent e) {
    for (KeyStroke ks : myKeyStrokes) {
      if (ks.equals(KeyStroke.getKeyStrokeForEvent(e))) {
        return true;
      }
    }
    return false;  
  }

  public void perform(KeyEvent e) {
    myRunnable.apply(e);
  }

  public static boolean processEvent(TerminalActionProvider actionProvider, final KeyEvent e) {
    for (TerminalAction a: actionProvider.getActions()) {
      if (a.matches(e)) {
        a.perform(e);
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
