package com.jediterm.terminal.ui.settings;

import com.jediterm.core.HyperlinkStyle;
import com.jediterm.core.TextStyle;
import com.jediterm.core.emulator.ColorPalette;
import com.jediterm.core.model.TerminalTypeAheadSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface UserSettingsProvider {
  ColorPalette getTerminalColorPalette();

  Font getTerminalFont();

  float getTerminalFontSize();

  /**
   * @return vertical scaling factor
   */
  default float getLineSpacing() {
    return 1.0f;
  }

  TextStyle getDefaultStyle();

  TextStyle getSelectionColor();

  TextStyle getFoundPatternColor();

  TextStyle getHyperlinkColor();

  HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode();

  boolean useInverseSelectionColor();

  boolean copyOnSelect();

  boolean pasteOnMiddleMouseClick();

  boolean emulateX11CopyPaste();

  boolean useAntialiasing();

  int maxRefreshRate();

  boolean audibleBell();

  boolean enableMouseReporting();

  int caretBlinkingMs();

  boolean scrollToBottomOnTyping();

  boolean DECCompatibilityMode();

  boolean forceActionOnMouseReporting();

  int getBufferMaxLinesCount();
  
  boolean altSendsEscape();

  boolean ambiguousCharsAreDoubleWidth();

  @NotNull TerminalTypeAheadSettings getTypeAheadSettings();
}
