package com.jediterm.terminal.model;

import com.jediterm.terminal.TextStyle;
import org.jetbrains.annotations.NotNull;

public class TerminalTypeAheadSettings {
    final public boolean myIsTypeAheadEnabled;
    final public long myTypeAheadLatencyThreshold;
    final public TextStyle myTypeAheadTextStyle;

    public TerminalTypeAheadSettings(boolean isTypeAheadEnabled,
                              long typeAheadLatencyThreshold,
                              @NotNull TextStyle typeAheadTextStyle) {
        myIsTypeAheadEnabled = isTypeAheadEnabled;
        myTypeAheadLatencyThreshold = typeAheadLatencyThreshold;
        myTypeAheadTextStyle = typeAheadTextStyle;
    }
}
