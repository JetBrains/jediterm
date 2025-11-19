package com.jediterm.core.util

/**
 * ASCII control characters and utilities
 */
object Ascii {
    // Control characters
    val NUL: Char = 0x00.toChar() // Null
    @JvmField
    val SOH: Char = 0x01.toChar() // Start of Heading
    val STX: Char = 0x02.toChar() // Start of Text
    val ETX: Char = 0x03.toChar() // End of Text
    val EOT: Char = 0x04.toChar() // End of Transmission
    @JvmField
    val ENQ: Char = 0x05.toChar() // Enquiry
    val ACK: Char = 0x06.toChar() // Acknowledge
    @JvmField
    val BEL: Char = 0x07.toChar() // Bell
    @JvmField
    val BS: Char = 0x08.toChar() // Backspace
    @JvmField
    val HT: Char = 0x09.toChar() // Horizontal Tab
    @JvmField
    val LF: Char = 0x0A.toChar() // Line Feed
    @JvmField
    val VT: Char = 0x0B.toChar() // Vertical Tab
    @JvmField
    val FF: Char = 0x0C.toChar() // Form Feed
    @JvmField
    val CR: Char = 0x0D.toChar() // Carriage Return
    @JvmField
    val SO: Char = 0x0E.toChar() // Shift Out
    @JvmField
    val SI: Char = 0x0F.toChar() // Shift In
    val DLE: Char = 0x10.toChar() // Data Link Escape
    val DC1: Char = 0x11.toChar() // Device Control 1
    val DC2: Char = 0x12.toChar() // Device Control 2
    val DC3: Char = 0x13.toChar() // Device Control 3
    val DC4: Char = 0x14.toChar() // Device Control 4
    val NAK: Char = 0x15.toChar() // Negative Acknowledge
    val SYN: Char = 0x16.toChar() // Synchronous Idle
    val ETB: Char = 0x17.toChar() // End of Transmission Block
    val CAN: Char = 0x18.toChar() // Cancel
    val EM: Char = 0x19.toChar() // End of Medium
    val SUB: Char = 0x1A.toChar() // Substitute
    @JvmField
    val ESC: Char = 0x1B.toChar() // Escape
    val FS: Char = 0x1C.toChar() // File Separator
    val GS: Char = 0x1D.toChar() // Group Separator
    val RS: Char = 0x1E.toChar() // Record Separator
    @JvmField
    val US: Char = 0x1F.toChar() // Unit Separator
    @JvmField
    val DEL: Char = 0x7F.toChar() // Delete

    /**
     * Check if a character is printable ASCII (0x20-0x7E)
     */
    fun isPrintable(ch: Char): Boolean {
        return ch.code >= 0x20 && ch.code <= 0x7E
    }
}
