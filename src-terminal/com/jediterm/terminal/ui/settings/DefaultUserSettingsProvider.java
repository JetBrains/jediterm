package com.jediterm.terminal.ui.settings;

import java.awt.Font;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.UIUtil;
import com.sun.jna.Platform;

public class DefaultUserSettingsProvider implements UserSettingsProvider {

  @Override
  public ColorPalette getTerminalColorPalette() {
    return Platform.isWindows() ? ColorPalette.WINDOWS_PALETTE : ColorPalette.XTERM_PALETTE;
  }

  @Override
  public Font getTerminalFont() {
    String fontName;
    if (UIUtil.isWindows) {
      fontName = "Consolas";
    }
    else if (UIUtil.isMac) {
      fontName = "Menlo";
    }
    else {
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
  public boolean useAntialiasing() {
    return true;
  }

  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return true;
  }

}
