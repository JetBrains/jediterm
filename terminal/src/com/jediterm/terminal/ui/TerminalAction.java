package com.jediterm.terminal.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author traff
 */
public class TerminalAction {
  private final String myName;
  private final KeyStroke[] myKeyStrokes;
  private final Predicate<KeyEvent> myRunnable;

  private Character myMnemonic = null;
  private Supplier<Boolean> myEnabledSupplier = () -> true;
  private Integer myMnemonicKey = null;
  private boolean mySeparatorBefore = false;
  private boolean myHidden = false;

  public TerminalAction(@NotNull TerminalActionPresentation presentation, @NotNull Predicate<KeyEvent> runnable) {
    this(presentation.getName(), presentation.getKeyStrokes().toArray(new KeyStroke[0]), runnable);
  }

  public TerminalAction(@NotNull TerminalActionPresentation presentation) {
    this(presentation, keyEvent -> true);
  }

  public TerminalAction(@NotNull String name, @NotNull KeyStroke[] keyStrokes, @NotNull Predicate<KeyEvent> runnable) {
    myName = name;
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

  public boolean isEnabled(@Nullable KeyEvent e) {
    return myEnabledSupplier.get();
  }

  public boolean actionPerformed(@Nullable KeyEvent e) {
    return myRunnable.test(e);
  }

  public static boolean processEvent(@NotNull TerminalActionProvider actionProvider, @NotNull KeyEvent e) {
    for (TerminalAction a : actionProvider.getActions()) {
      if (a.matches(e)) {
        return a.isEnabled(e) && a.actionPerformed(e);
      }
    }

    if (actionProvider.getNextProvider() != null) {
      return processEvent(actionProvider.getNextProvider(), e);
    }

    return false;
  }

  public static boolean addToMenu(JPopupMenu menu, TerminalActionProvider actionProvider) {
    boolean added = false;
    if (actionProvider.getNextProvider() != null) {
      added = addToMenu(menu, actionProvider.getNextProvider());
    }
    boolean addSeparator = added;
    for (final TerminalAction a : actionProvider.getActions()) {
      if (a.isHidden()) {
        continue;
      }
      if (!addSeparator) {
        addSeparator = a.isSeparated();
      }
      if (addSeparator) {
        menu.addSeparator();
        addSeparator = false;
      }

      menu.add(a.toMenuItem());

      added = true;
    }

    return added;
  }

  public int getKeyCode() {
    for (KeyStroke ks : myKeyStrokes) {
      return ks.getKeyCode();
    }
    return 0;
  }

  public int getModifiers() {
    for (KeyStroke ks : myKeyStrokes) {
      return ks.getModifiers();
    }
    return 0;
  }

  public String getName() {
    return myName;
  }
  
  public TerminalAction withMnemonic(Character ch) {
    myMnemonic = ch;
    return this;
  }

  public TerminalAction withMnemonicKey(Integer key) {
    myMnemonicKey = key;
    return this;
  }

  public TerminalAction withEnabledSupplier(@NotNull Supplier<Boolean> enabledSupplier) {
    myEnabledSupplier = enabledSupplier;
    return this;
  }

  public TerminalAction separatorBefore(boolean enabled) {
    mySeparatorBefore = enabled;
    return this;
  }
  
  public JMenuItem toMenuItem() {
    JMenuItem menuItem = new JMenuItem(myName);

    if (myMnemonic != null) {
      menuItem.setMnemonic(myMnemonic);
    }
    if (myMnemonicKey != null) {
      menuItem.setMnemonic(myMnemonicKey);
    }

    if (myKeyStrokes.length > 0) {
      menuItem.setAccelerator(myKeyStrokes[0]);
    }

    menuItem.addActionListener(actionEvent -> actionPerformed(null));
    menuItem.setEnabled(isEnabled(null));
    
    return menuItem;
  }

  public boolean isSeparated() {
    return mySeparatorBefore;
  }

  public boolean isHidden() {
    return myHidden;
  }

  public TerminalAction withHidden(boolean hidden) {
    myHidden = hidden;
    return this;
  }

  @Override
  public String toString() {
    return "'" + myName + "'";
  }
}
