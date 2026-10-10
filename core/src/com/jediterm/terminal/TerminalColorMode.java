package com.jediterm.terminal;

/**
 * Color scheme reported in response to a DEC-specific Device Status Report
 * query for the terminal's light/dark preference (host sends {@code CSI ?996n},
 * terminal replies {@code CSI ?997;<code>n}). Values match that reply code.
 */
public enum TerminalColorMode {
  UNKNOWN(0),
  LIGHT(1),
  DARK(2);

  private final int reportCode;

  TerminalColorMode(int reportCode) {
    this.reportCode = reportCode;
  }

  public int getReportCode() {
    return reportCode;
  }
}
