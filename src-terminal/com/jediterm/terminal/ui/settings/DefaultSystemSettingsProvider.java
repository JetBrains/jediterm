package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.ui.UIUtil;

import javax.swing.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class DefaultSystemSettingsProvider implements SystemSettingsProvider {
  @Override
  public KeyStroke[] getNewSessionKeyStrokes() {
    return new KeyStroke[]{UIUtil.isMac
        ? KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.META_DOWN_MASK)
        : KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)};
  }

  @Override
  public KeyStroke[] getCloseSessionKeyStrokes() {
    return new KeyStroke[]{UIUtil.isMac
        ? KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.META_DOWN_MASK)
        : KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)};
  }

  @Override
  public KeyStroke[] getCopyKeyStrokes() {
    return new KeyStroke[]{UIUtil.isMac
        ? KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK)
        // CTRL + C is used for signal; use CTRL + SHIFT + C instead
        : KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)};
  }

  @Override
  public KeyStroke[] getPasteKeyStrokes() {
    return new KeyStroke[]{UIUtil.isMac
        ? KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK)
        // CTRL + V is used for signal; use CTRL + SHIFT + V instead
        : KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)};
  }

}
