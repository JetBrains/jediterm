package com.jediterm.terminal.ui;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

class BlinkingTextTracker {
  private final BlinkTracker slowBlinkTracker = new BlinkTracker();
  private final BlinkTracker rapidBlinkTracker = new BlinkTracker();

  void updateState(@NotNull SettingsProvider settingsProvider, @NotNull TerminalPanel panel) {
    if (settingsProvider.enableTextBlinking()) {
      long currentTime = System.currentTimeMillis();
      boolean slowBlinkStateChanged = slowBlinkTracker.update(currentTime, settingsProvider.slowTextBlinkMs());
      boolean rapidBlinkStateChanged = rapidBlinkTracker.update(currentTime, settingsProvider.rapidTextBlinkMs());
      if (slowBlinkStateChanged || rapidBlinkStateChanged) {
        panel.repaint();
      }
    }
  }

  boolean shouldBlinkNow(@NotNull TextStyle style) {
    return (style.hasOption(TextStyle.Option.SLOW_BLINK) && slowBlinkTracker.inverse) ||
      (style.hasOption(TextStyle.Option.RAPID_BLINK) && rapidBlinkTracker.inverse);
  }

  private static class BlinkTracker {
    private final long lastBlinkMillis = System.currentTimeMillis();
    private boolean inverse = false;

    private boolean update(long currentTime, int period) {
      if (period <= 0) return false;
      boolean prevInverse = inverse;
      int blinks = (int)(currentTime - lastBlinkMillis) / period;
      inverse = blinks % 2 == 1;
      return prevInverse != inverse;
    }
  }
}
