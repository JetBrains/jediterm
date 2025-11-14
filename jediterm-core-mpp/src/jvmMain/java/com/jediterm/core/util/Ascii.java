package com.jediterm.core.util;

/**
 * ASCII control characters and utilities
 */
public class Ascii {
    // Control characters
    public static final char NUL = 0x00;    // Null
    public static final char SOH = 0x01;    // Start of Heading
    public static final char STX = 0x02;    // Start of Text
    public static final char ETX = 0x03;    // End of Text
    public static final char EOT = 0x04;    // End of Transmission
    public static final char ENQ = 0x05;    // Enquiry
    public static final char ACK = 0x06;    // Acknowledge
    public static final char BEL = 0x07;    // Bell
    public static final char BS = 0x08;     // Backspace
    public static final char HT = 0x09;     // Horizontal Tab
    public static final char LF = 0x0A;     // Line Feed
    public static final char VT = 0x0B;     // Vertical Tab
    public static final char FF = 0x0C;     // Form Feed
    public static final char CR = 0x0D;     // Carriage Return
    public static final char SO = 0x0E;     // Shift Out
    public static final char SI = 0x0F;     // Shift In
    public static final char DLE = 0x10;    // Data Link Escape
    public static final char DC1 = 0x11;    // Device Control 1
    public static final char DC2 = 0x12;    // Device Control 2
    public static final char DC3 = 0x13;    // Device Control 3
    public static final char DC4 = 0x14;    // Device Control 4
    public static final char NAK = 0x15;    // Negative Acknowledge
    public static final char SYN = 0x16;    // Synchronous Idle
    public static final char ETB = 0x17;    // End of Transmission Block
    public static final char CAN = 0x18;    // Cancel
    public static final char EM = 0x19;     // End of Medium
    public static final char SUB = 0x1A;    // Substitute
    public static final char ESC = 0x1B;    // Escape
    public static final char FS = 0x1C;     // File Separator
    public static final char GS = 0x1D;     // Group Separator
    public static final char RS = 0x1E;     // Record Separator
    public static final char US = 0x1F;     // Unit Separator
    public static final char DEL = 0x7F;    // Delete

    /**
     * Check if a character is printable ASCII (0x20-0x7E)
     */
    public static boolean isPrintable(char ch) {
        return ch >= 0x20 && ch <= 0x7E;
    }
}
