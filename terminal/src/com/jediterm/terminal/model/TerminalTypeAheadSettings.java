package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class TerminalTypeAheadSettings {

  public static final TerminalTypeAheadSettings DEFAULT = new TerminalTypeAheadSettings(
    true,
    TimeUnit.MILLISECONDS.toNanos(100),
    new TextStyle(TerminalColor.rgb(150, 150, 150), null)
  );

  private final boolean myEnabled;
  private final long myLatencyThreshold;
  private final TextStyle myTextStyle;

  public TerminalTypeAheadSettings(boolean enabled, long latencyThreshold, @NotNull TextStyle textStyle) {
    myEnabled = enabled;
    myLatencyThreshold = latencyThreshold;
    myTextStyle = textStyle;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public long getLatencyThreshold() {
    return myLatencyThreshold;
  }

  public @NotNull TextStyle getTextStyle() {
    return myTextStyle;
  }
}
