package com.jediterm.terminal.ui;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author traff
 */
public abstract class AbstractSystemSettingsProvider implements SystemSettingsProvider {
  @Override
  public KeyStroke[] getNewSessionKeyStrokes() {
    return new KeyStroke[]{UIUtil.isMac
                           ? KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK)
                           : KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)};
  }
}
