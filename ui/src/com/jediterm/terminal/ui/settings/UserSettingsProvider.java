package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.model.TerminalTypeAheadSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

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

  default boolean shouldDisableLineSpacingForAlternateScreenBuffer() {
    return false;
  }

  default boolean shouldFillCharacterBackgroundIncludingLineSpacing() {
    return true;
  }

  default @NotNull TerminalColor getDefaultForeground() {
    return Objects.requireNonNull(getDefaultStyle().getForeground());
  }

  default @NotNull TerminalColor getDefaultBackground() {
    return Objects.requireNonNull(getDefaultStyle().getBackground());
  }

  /**
   * @deprecated override {@link UserSettingsProvider#getDefaultForeground()} and
   * {@link UserSettingsProvider#getDefaultBackground()} instead
   */
  @Deprecated
  default @NotNull TextStyle getDefaultStyle() {
    return new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
  }

  @NotNull TextStyle getSelectionColor();

  @NotNull TextStyle getFoundPatternColor();

  TextStyle getHyperlinkColor();

  HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode();

  default boolean enableTextBlinking() { return false; }

  default int slowTextBlinkMs() { return 1000; }

  default int rapidTextBlinkMs() { return 500; }

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

  boolean sendArrowKeysInAlternativeMode();
}
