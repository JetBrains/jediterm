package com.jediterm.terminal.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author traff
 */
public class TerminalAction {
  private final String myName;
  private final KeyStroke[] myKeyStrokes;
  private final Predicate<KeyEvent> myRunnable;

  private Supplier<Boolean> myEnabledSupplier = () -> true;
  private Integer myMnemonicKeyCode = null;
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

  public @Nullable Integer getMnemonicKeyCode() {
    return myMnemonicKeyCode;
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

  public @NotNull String getName() {
    return myName;
  }
  
  public TerminalAction withMnemonicKey(Integer key) {
    myMnemonicKeyCode = key;
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
  
  private @NotNull JMenuItem toMenuItem() {
    JMenuItem menuItem = new JMenuItem(myName);

    if (myMnemonicKeyCode != null) {
      menuItem.setMnemonic(myMnemonicKeyCode);
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

  public static void fillMenu(@NotNull JPopupMenu menu, @NotNull TerminalActionProvider actionProvider) {
    buildMenu(actionProvider, new TerminalActionMenuBuilder() {
      @Override
      public void addAction(@NotNull TerminalAction action) {
        menu.add(action.toMenuItem());
      }

      @Override
      public void addSeparator() {
        menu.addSeparator();
      }
    });
  }

  public static void buildMenu(@NotNull TerminalActionProvider provider, @NotNull TerminalActionMenuBuilder builder) {
    List<TerminalActionProvider> actionProviders = listActionProviders(provider);
    boolean emptyGroup = true;
    for (TerminalActionProvider actionProvider : actionProviders) {
      boolean addSeparator = !emptyGroup;
      emptyGroup = true;
      for (TerminalAction action : actionProvider.getActions()) {
        if (action.isHidden()) continue;
        if (addSeparator || action.isSeparated()) {
          builder.addSeparator();
          addSeparator = false;
        }
        builder.addAction(action);
        emptyGroup = false;
      }
    }
  }

  private static @NotNull List<TerminalActionProvider> listActionProviders(@NotNull TerminalActionProvider provider) {
    var providers = new ArrayList<TerminalActionProvider>();
    for (var p = provider; p != null; p = p.getNextProvider()) {
      providers.add(0, p);
    }
    return providers;
  }
}
