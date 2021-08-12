package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class TerminalTypeAheadSettings {

  public static final TerminalTypeAheadSettings DEFAULT = new TerminalTypeAheadSettings(
    true,
    TimeUnit.MILLISECONDS.toNanos(100)
  );

  private final boolean myEnabled;
  private final long myLatencyThreshold;

  public TerminalTypeAheadSettings(boolean enabled, long latencyThreshold) {
    myEnabled = enabled;
    myLatencyThreshold = latencyThreshold;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public long getLatencyThreshold() {
    return myLatencyThreshold;
  }

}
