package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class DefaultSettingsProvider implements SettingsProvider {
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

  @Override
  public ColorPalette getTerminalColorPalette() {
    return UIUtil.isWindows ? ColorPalette.WINDOWS_PALETTE : ColorPalette.XTERM_PALETTE;
  }

  @Override
  public Font getTerminalFont() {
    String fontName;
    if (UIUtil.isWindows) {
      fontName = "Consolas";
    } else if (UIUtil.isMac) {
      fontName = "Menlo";
    } else {
      fontName = "Monospaced";
    }
    return Font.decode(fontName).deriveFont(getTerminalFontSize());
  }

  @Override
  public float getTerminalFontSize() {
    return 14;
  }

  @Override
  public float getLineSpace() {
    return 0;
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
    // return new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(24, 24, 24));
  }

  @Override
  public TextStyle getSelectionColor() {
    return new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(82, 109, 165));
  }

  @Override
  public boolean useInverseSelectionColor() {
    return true;
  }

  @Override
  public boolean copyOnSelect() {
    return emulateX11CopyPaste();
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return emulateX11CopyPaste();
  }

  @Override
  public boolean emulateX11CopyPaste() {
    return true;
  }

  @Override
  public boolean useAntialiasing() {
    return true;
  }

  @Override
  public int maxRefreshRate() {
    return 50;
  }

  @Override
  public boolean audibleBell() {
    return true;
  }

  @Override
  public boolean enableMouseReporting() {
    return true;
  }

  @Override
  public int caretBlinkingMs() {
    return 505;
  }

  @Override
  public boolean scrollToBottomOnTyping() {
    return true;
  }

  @Override
  public boolean DECCompatibilityMode() {
    return true;
  }

  @Override
  public boolean forceActionOnMouseReporting() {
    return false;
  }
}
