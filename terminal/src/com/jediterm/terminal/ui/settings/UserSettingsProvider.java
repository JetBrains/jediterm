package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;

import java.awt.*;

public interface UserSettingsProvider {
  ColorPalette getTerminalColorPalette();

  Font getTerminalFont();

  float getTerminalFontSize();

  /**
   * @return vertical scaling factor
   */
  default float getLineSpacing() {
    return getLineSpace();
  }

  /**
   * @deprecated use {@link #getLineSpacing()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default float getLineSpace() {
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

  boolean isTypeAheadEnabled();

  long getTypeaheadLatencyThreshold();
}
