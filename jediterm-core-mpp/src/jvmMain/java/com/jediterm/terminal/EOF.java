package com.jediterm.terminal;

import java.io.IOException;

/**
 * Exception thrown when end of terminal data stream is reached.
 */
public class EOF extends IOException {
    public EOF() {
        super("End of terminal data stream");
    }

    public EOF(String message) {
        super(message);
    }
}
