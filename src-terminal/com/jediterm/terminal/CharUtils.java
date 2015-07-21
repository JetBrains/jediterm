package com.jediterm.terminal;

import com.google.common.base.Ascii;
import com.jediterm.terminal.emulator.charset.CharacterSets;
import com.jediterm.terminal.model.CharBuffer;

/**
 * @author traff
 */
public class CharUtils {

    public static final int ESC = Ascii.ESC;
    public static final int DEL = Ascii.DEL;

    // NUL can only be at the end of the line
    public static final char NUL_CHAR = 0x0;
    public static final char EMPTY_CHAR = ' ';

    private CharUtils() {
    }

    private static final String[] NONPRINTING_NAMES = {"NUL", "SOH", "STX", "ETX", "EOT", "ENQ",
            "ACK", "BEL", "BS", "TAB", "LF", "VT", "FF", "CR", "S0", "S1",
            "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN",
            "EM", "SUB", "ESC", "FS", "GS", "RS", "US"};

    public static byte[] VT102_RESPONSE = makeCode(ESC, '[', '?', '6', 'c');

    public static String getNonControlCharacters(int maxChars, char[] buf, int offset, int charsLength) {
        int len = Math.min(maxChars, charsLength);

        final int origLen = len;
        char tmp;
        while (len > 0) {
            tmp = buf[offset++];
            if (0x20 <= tmp) { //stop when we reach control chars
                len--;
                continue;
            }
            offset--;
            break;
        }

        int length = origLen - len;

        return new String(buf, offset - length, length);
    }

    public enum CharacterType {
        NONPRINTING,
        PRINTING,
        NONASCII, NONE
    }

    public static CharacterType appendChar(final StringBuilder sb, final CharacterType last, final char c) {
        if (c <= 0x1F) {
            sb.append(EMPTY_CHAR);
            sb.append(NONPRINTING_NAMES[c]);
            return CharacterType.NONPRINTING;
        }
        else if (c == DEL) {
            sb.append(" DEL");
            return CharacterType.NONPRINTING;
        }
        else if (c > 0x1F && c <= 0x7E) {
            if (last != CharacterType.PRINTING) sb.append(EMPTY_CHAR);
            sb.append(c);
            return CharacterType.PRINTING;
        }
        else {
            sb.append(" 0x").append(Integer.toHexString(c));
            return CharacterType.NONASCII;
        }
    }

    public static void appendBuf(final StringBuilder sb, final char[] bs, final int begin, final int length) {
        CharacterType last = CharacterType.NONPRINTING;
        final int end = begin + length;
        for (int i = begin; i < end; i++) {
            final char c = (char)bs[i];
            last = appendChar(sb, last, c);
        }
    }


    public static byte[] makeCode(final int... bytesAsInt) {
        final byte[] bytes = new byte[bytesAsInt.length];
        int i = 0;
        for (final int byteAsInt : bytesAsInt) {
            bytes[i] = (byte)byteAsInt;
            i++;
        }
        return bytes;
    }

    /**
     * Computes text length as sum of characters length, treating double-width(full-width) characters as 2, normal-width(half-width) as 1
     * (Read http://en.wikipedia.org/wiki/Halfwidth_and_fullwidth_forms)
     */
    public static int getTextLength(char[] buffer, int start, int length) {
        int result = 0;
        for (int i = start; i < start + length; i++) {
            result += isDoubleWidthCharacter(buffer[i]) ? 2 : 1;
        }
        return result;
    }

    private static boolean isDoubleWidthCharacter(char c) {
        return false; //  TODO: use FontMetrics to determine the correct width after refactoring
        // as for now double width chars aren't fully supported anyway
    }


    public static CharBuffer heavyDecCompatibleBuffer(CharBuffer buf) {
        char[] c = Arrays.copyOfRange(buf.getBuf(), 0, buf.getBuf().length);
        for(int i = 0; i < c.length; i++) {
            c[i] = CharacterSets.getHeavyDecBoxChar(c[i]);
        }
        return new CharBuffer(c, buf.getStart(), buf.getLength());
    }
}
