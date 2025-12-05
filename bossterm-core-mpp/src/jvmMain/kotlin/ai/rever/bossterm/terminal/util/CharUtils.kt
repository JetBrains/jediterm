package ai.rever.bossterm.terminal.util

import ai.rever.bossterm.core.util.Ascii
import ai.rever.bossterm.terminal.emulator.charset.CharacterSets
import ai.rever.bossterm.terminal.model.CharBuffer
import java.util.*
import kotlin.math.min

/**
 * @author traff
 */
object CharUtils {
    private val ESC = Ascii.ESC.code
    private val DEL = Ascii.DEL.code

    // NUL can only be at the end of the line
    val NUL_CHAR: Char = 0x0.toChar()
    const val EMPTY_CHAR: Char = ' '

    //BossTerm Unicode private use area U+100000–U+10FFFD
    const val DWC: Char = '\uE000' //Second part of double-width character

    private val NONPRINTING_NAMES = arrayOf<String?>(
        "NUL", "SOH", "STX", "ETX", "EOT", "ENQ",
        "ACK", "BEL", "BS", "TAB", "LF", "VT", "FF", "CR", "S0", "S1",
        "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN",
        "EM", "SUB", "ESC", "FS", "GS", "RS", "US"
    )

    var VT102_RESPONSE: ByteArray = makeCode(ESC, '['.code, '?'.code, '6'.code, 'c'.code)

    // VT220 with color support for better TUI compatibility (Neovim, vim, less)
    // CSI ? 62 ; 1 c = VT220 terminal with 132 column mode (Primary DA)
    var VT220_RESPONSE: ByteArray = makeCode(ESC, '['.code, '?'.code, '6'.code, '2'.code, ';'.code, '1'.code, 'c'.code)

    // Secondary DA response: CSI > 1 ; 10 ; 0 c
    // Format: >Pp;Pv;Pc c where Pp=terminal type (1=VT220), Pv=version, Pc=ROM cartridge (0=none)
    // This is required for tmux and other apps that query terminal capabilities
    var VT220_SECONDARY_RESPONSE: ByteArray = makeCode(ESC, '['.code, '>'.code, '1'.code, ';'.code, '1'.code, '0'.code, ';'.code, '0'.code, 'c'.code)

    fun getNonControlCharacters(maxChars: Int, buf: CharArray, offset: Int, charsLength: Int): String {
        var offset = offset
        var len = min(maxChars, charsLength)

        val origLen = len
        var tmp: Char
        while (len > 0) {
            tmp = buf[offset++]
            if (0x20 <= tmp.code) { //stop when we reach control chars
                len--
                continue
            }
            offset--
            break
        }

        val length = origLen - len

        return String(buf, offset - length, length)
    }

    /**
     * Counts double-width characters in a buffer.
     *
     * FIXED: Now properly handles surrogate pairs by iterating by code point,
     * not by char. Previous implementation would miscount surrogate pairs because
     * it incremented by 1 after calling codePointAt(), which skipped the low surrogate.
     *
     * @param buf Character buffer
     * @param start Starting index in buffer
     * @param length Number of characters to process
     * @param ambiguousIsDWC Whether ambiguous-width characters are double-width
     * @return Count of double-width characters (in terms of visual cells)
     */
    fun countDoubleWidthCharacters(buf: CharArray, start: Int, length: Int, ambiguousIsDWC: Boolean): Int {
        var cnt = 0
        var offset = 0

        while (offset < length) {
            val index = start + offset
            if (index >= buf.size) break

            val ucs = Character.codePointAt(buf, index)
            if (isDoubleWidthCharacter(ucs, ambiguousIsDWC)) {
                cnt++
            }

            // CRITICAL: Increment by charCount to skip both surrogates for supplementary chars
            offset += Character.charCount(ucs)
        }

        return cnt
    }

    fun appendChar(sb: StringBuilder, last: CharacterType?, c: Char): CharacterType {
        if (c.code <= 0x1F) {
            sb.append(EMPTY_CHAR)
            sb.append(NONPRINTING_NAMES[c.code])
            return CharacterType.NONPRINTING
        } else if (c.code == DEL) {
            sb.append(" DEL")
            return CharacterType.NONPRINTING
        } else if (c.code > 0x1F && c.code <= 0x7E) {
            if (last != CharacterType.PRINTING) sb.append(EMPTY_CHAR)
            sb.append(c)
            return CharacterType.PRINTING
        } else {
            sb.append(" 0x").append(Integer.toHexString(c.code))
            return CharacterType.NONASCII
        }
    }

    fun appendBuf(sb: StringBuilder, bs: CharArray, begin: Int, length: Int) {
        var last = CharacterType.NONPRINTING
        val end = begin + length
        for (i in begin..<end) {
            val c = bs[i]
            last = appendChar(sb, last, c)
        }
    }


    fun makeCode(vararg bytesAsInt: Int): ByteArray {
        val bytes = ByteArray(bytesAsInt.size)
        var i = 0
        for (byteAsInt in bytesAsInt) {
            bytes[i] = byteAsInt.toByte()
            i++
        }
        return bytes
    }

    /**
     * Computes text length as sum of characters length, treating double-width(full-width) characters as 2, normal-width(half-width) as 1
     * (Read http://en.wikipedia.org/wiki/Halfwidth_and_fullwidth_forms)
     *
     * NOTE: This method uses char-by-char iteration and may not handle grapheme clusters correctly.
     * Consider using getTextLengthGraphemeAware() for proper Unicode support.
     */
    fun getTextLengthDoubleWidthAware(buffer: CharArray, start: Int, length: Int, ambiguousIsDWC: Boolean): Int {
        var result = 0
        for (i in start..<start + length) {
            result += if ((buffer[i] != DWC) && isDoubleWidthCharacter(
                    buffer[i].code,
                    ambiguousIsDWC
                ) && !((i + 1 < start + length) && (buffer[i + 1] == DWC))
            ) 2 else 1
        }
        return result
    }

    /**
     * Computes text length using grapheme-aware width calculation.
     *
     * This is the CORRECT way to measure text with:
     * - Surrogate pairs (characters outside BMP, U+10000+)
     * - Emoji with ZWJ sequences (family emoji, etc.)
     * - Emoji with variation selectors (☁️, ⭐, etc.)
     * - Emoji with skin tone modifiers (👍🏽, etc.)
     * - Combining characters (á = a + combining acute)
     *
     * Uses GraphemeUtils for segmentation and width calculation.
     *
     * @param text The string to measure
     * @param ambiguousIsDWC Whether ambiguous-width characters are double-width
     * @return Visual width in character cells
     */
    fun getTextLengthGraphemeAware(text: String, ambiguousIsDWC: Boolean): Int {
        if (text.isEmpty()) return 0

        // Fast path: simple ASCII without DWC markers
        if (text.all { it.code < 128 && it != DWC }) {
            return text.length
        }

        // Segment into graphemes and sum widths
        val graphemes = GraphemeUtils.segmentIntoGraphemes(text)
        var visualLength = 0

        for (grapheme in graphemes) {
            // Skip DWC markers (they don't add to visual length)
            if (grapheme.text == DWC.toString()) continue

            // Use grapheme's pre-calculated width (already considers ambiguousIsDWC)
            val width = GraphemeUtils.getGraphemeWidth(grapheme.text, ambiguousIsDWC)
            visualLength += width
        }

        return visualLength
    }

    fun isDoubleWidthCharacter(c: Int, ambiguousIsDWC: Boolean): Boolean {
        if (c == DWC.code || c <= 0xa0 || (c > 0x452 && c < 0x1100)) {
            return false
        }

        return mk_wcwidth(c, ambiguousIsDWC) == 2
    }


    fun heavyDecCompatibleBuffer(buf: CharBuffer): CharBuffer {
        val c = Arrays.copyOfRange(buf.buf, 0, buf.buf.size)
        for (i in c.indices) {
            c[i] = CharacterSets.getHeavyDecBoxChar(c[i])
        }
        return CharBuffer(c, buf.start, buf.length)
    }


    // The following code and data in converted from the https://www.cl.cam.ac.uk/~mgk25/ucs/wcwidth.c
    // which can be treated a standard way to determine the width of a character
    private val COMBINING = arrayOf<CharArray?>(
        charArrayOf(0x0300.toChar(), 0x036F.toChar()),
        charArrayOf(0x0483.toChar(), 0x0486.toChar()),
        charArrayOf(0x0488.toChar(), 0x0489.toChar()),
        charArrayOf(0x0591.toChar(), 0x05BD.toChar()),
        charArrayOf(0x05BF.toChar(), 0x05BF.toChar()),
        charArrayOf(0x05C1.toChar(), 0x05C2.toChar()),
        charArrayOf(0x05C4.toChar(), 0x05C5.toChar()),
        charArrayOf(0x05C7.toChar(), 0x05C7.toChar()),
        charArrayOf(0x0600.toChar(), 0x0603.toChar()),
        charArrayOf(0x0610.toChar(), 0x0615.toChar()),
        charArrayOf(0x064B.toChar(), 0x065E.toChar()),
        charArrayOf(0x0670.toChar(), 0x0670.toChar()),
        charArrayOf(0x06D6.toChar(), 0x06E4.toChar()),
        charArrayOf(0x06E7.toChar(), 0x06E8.toChar()),
        charArrayOf(0x06EA.toChar(), 0x06ED.toChar()),
        charArrayOf(0x070F.toChar(), 0x070F.toChar()),
        charArrayOf(0x0711.toChar(), 0x0711.toChar()),
        charArrayOf(0x0730.toChar(), 0x074A.toChar()),
        charArrayOf(0x07A6.toChar(), 0x07B0.toChar()),
        charArrayOf(0x07EB.toChar(), 0x07F3.toChar()),
        charArrayOf(0x0901.toChar(), 0x0902.toChar()),
        charArrayOf(0x093C.toChar(), 0x093C.toChar()),
        charArrayOf(0x0941.toChar(), 0x0948.toChar()),
        charArrayOf(0x094D.toChar(), 0x094D.toChar()),
        charArrayOf(0x0951.toChar(), 0x0954.toChar()),
        charArrayOf(0x0962.toChar(), 0x0963.toChar()),
        charArrayOf(0x0981.toChar(), 0x0981.toChar()),
        charArrayOf(0x09BC.toChar(), 0x09BC.toChar()),
        charArrayOf(0x09C1.toChar(), 0x09C4.toChar()),
        charArrayOf(0x09CD.toChar(), 0x09CD.toChar()),
        charArrayOf(0x09E2.toChar(), 0x09E3.toChar()),
        charArrayOf(0x0A01.toChar(), 0x0A02.toChar()),
        charArrayOf(0x0A3C.toChar(), 0x0A3C.toChar()),
        charArrayOf(0x0A41.toChar(), 0x0A42.toChar()),
        charArrayOf(0x0A47.toChar(), 0x0A48.toChar()),
        charArrayOf(0x0A4B.toChar(), 0x0A4D.toChar()),
        charArrayOf(0x0A70.toChar(), 0x0A71.toChar()),
        charArrayOf(0x0A81.toChar(), 0x0A82.toChar()),
        charArrayOf(0x0ABC.toChar(), 0x0ABC.toChar()),
        charArrayOf(0x0AC1.toChar(), 0x0AC5.toChar()),
        charArrayOf(0x0AC7.toChar(), 0x0AC8.toChar()),
        charArrayOf(0x0ACD.toChar(), 0x0ACD.toChar()),
        charArrayOf(0x0AE2.toChar(), 0x0AE3.toChar()),
        charArrayOf(0x0B01.toChar(), 0x0B01.toChar()),
        charArrayOf(0x0B3C.toChar(), 0x0B3C.toChar()),
        charArrayOf(0x0B3F.toChar(), 0x0B3F.toChar()),
        charArrayOf(0x0B41.toChar(), 0x0B43.toChar()),
        charArrayOf(0x0B4D.toChar(), 0x0B4D.toChar()),
        charArrayOf(0x0B56.toChar(), 0x0B56.toChar()),
        charArrayOf(0x0B82.toChar(), 0x0B82.toChar()),
        charArrayOf(0x0BC0.toChar(), 0x0BC0.toChar()),
        charArrayOf(0x0BCD.toChar(), 0x0BCD.toChar()),
        charArrayOf(0x0C3E.toChar(), 0x0C40.toChar()),
        charArrayOf(0x0C46.toChar(), 0x0C48.toChar()),
        charArrayOf(0x0C4A.toChar(), 0x0C4D.toChar()),
        charArrayOf(0x0C55.toChar(), 0x0C56.toChar()),
        charArrayOf(0x0CBC.toChar(), 0x0CBC.toChar()),
        charArrayOf(0x0CBF.toChar(), 0x0CBF.toChar()),
        charArrayOf(0x0CC6.toChar(), 0x0CC6.toChar()),
        charArrayOf(0x0CCC.toChar(), 0x0CCD.toChar()),
        charArrayOf(0x0CE2.toChar(), 0x0CE3.toChar()),
        charArrayOf(0x0D41.toChar(), 0x0D43.toChar()),
        charArrayOf(0x0D4D.toChar(), 0x0D4D.toChar()),
        charArrayOf(0x0DCA.toChar(), 0x0DCA.toChar()),
        charArrayOf(0x0DD2.toChar(), 0x0DD4.toChar()),
        charArrayOf(0x0DD6.toChar(), 0x0DD6.toChar()),
        charArrayOf(0x0E31.toChar(), 0x0E31.toChar()),
        charArrayOf(0x0E34.toChar(), 0x0E3A.toChar()),
        charArrayOf(0x0E47.toChar(), 0x0E4E.toChar()),
        charArrayOf(0x0EB1.toChar(), 0x0EB1.toChar()),
        charArrayOf(0x0EB4.toChar(), 0x0EB9.toChar()),
        charArrayOf(0x0EBB.toChar(), 0x0EBC.toChar()),
        charArrayOf(0x0EC8.toChar(), 0x0ECD.toChar()),
        charArrayOf(0x0F18.toChar(), 0x0F19.toChar()),
        charArrayOf(0x0F35.toChar(), 0x0F35.toChar()),
        charArrayOf(0x0F37.toChar(), 0x0F37.toChar()),
        charArrayOf(0x0F39.toChar(), 0x0F39.toChar()),
        charArrayOf(0x0F71.toChar(), 0x0F7E.toChar()),
        charArrayOf(0x0F80.toChar(), 0x0F84.toChar()),
        charArrayOf(0x0F86.toChar(), 0x0F87.toChar()),
        charArrayOf(0x0F90.toChar(), 0x0F97.toChar()),
        charArrayOf(0x0F99.toChar(), 0x0FBC.toChar()),
        charArrayOf(0x0FC6.toChar(), 0x0FC6.toChar()),
        charArrayOf(0x102D.toChar(), 0x1030.toChar()),
        charArrayOf(0x1032.toChar(), 0x1032.toChar()),
        charArrayOf(0x1036.toChar(), 0x1037.toChar()),
        charArrayOf(0x1039.toChar(), 0x1039.toChar()),
        charArrayOf(0x1058.toChar(), 0x1059.toChar()),
        charArrayOf(0x1160.toChar(), 0x11FF.toChar()),
        charArrayOf(0x135F.toChar(), 0x135F.toChar()),
        charArrayOf(0x1712.toChar(), 0x1714.toChar()),
        charArrayOf(0x1732.toChar(), 0x1734.toChar()),
        charArrayOf(0x1752.toChar(), 0x1753.toChar()),
        charArrayOf(0x1772.toChar(), 0x1773.toChar()),
        charArrayOf(0x17B4.toChar(), 0x17B5.toChar()),
        charArrayOf(0x17B7.toChar(), 0x17BD.toChar()),
        charArrayOf(0x17C6.toChar(), 0x17C6.toChar()),
        charArrayOf(0x17C9.toChar(), 0x17D3.toChar()),
        charArrayOf(0x17DD.toChar(), 0x17DD.toChar()),
        charArrayOf(0x180B.toChar(), 0x180D.toChar()),
        charArrayOf(0x18A9.toChar(), 0x18A9.toChar()),
        charArrayOf(0x1920.toChar(), 0x1922.toChar()),
        charArrayOf(0x1927.toChar(), 0x1928.toChar()),
        charArrayOf(0x1932.toChar(), 0x1932.toChar()),
        charArrayOf(0x1939.toChar(), 0x193B.toChar()),
        charArrayOf(0x1A17.toChar(), 0x1A18.toChar()),
        charArrayOf(0x1B00.toChar(), 0x1B03.toChar()),
        charArrayOf(0x1B34.toChar(), 0x1B34.toChar()),
        charArrayOf(0x1B36.toChar(), 0x1B3A.toChar()),
        charArrayOf(0x1B3C.toChar(), 0x1B3C.toChar()),
        charArrayOf(0x1B42.toChar(), 0x1B42.toChar()),
        charArrayOf(0x1B6B.toChar(), 0x1B73.toChar()),
        charArrayOf(0x1DC0.toChar(), 0x1DCA.toChar()),
        charArrayOf(0x1DFE.toChar(), 0x1DFF.toChar()),
        charArrayOf(0x200B.toChar(), 0x200F.toChar()),
        charArrayOf(0x202A.toChar(), 0x202E.toChar()),
        charArrayOf(0x2060.toChar(), 0x2063.toChar()),
        charArrayOf(0x206A.toChar(), 0x206F.toChar()),
        charArrayOf(0x20D0.toChar(), 0x20EF.toChar()),
        charArrayOf(0x302A.toChar(), 0x302F.toChar()),
        charArrayOf(0x3099.toChar(), 0x309A.toChar()),
        charArrayOf(0xA806.toChar(), 0xA806.toChar()),
        charArrayOf(0xA80B.toChar(), 0xA80B.toChar()),
        charArrayOf(0xA825.toChar(), 0xA826.toChar()),
        charArrayOf(0xFB1E.toChar(), 0xFB1E.toChar()),
        charArrayOf(0xFE00.toChar(), 0xFE0F.toChar()),
        charArrayOf(0xFE20.toChar(), 0xFE23.toChar()),
        charArrayOf(0xFEFF.toChar(), 0xFEFF.toChar()),
        charArrayOf(0xFFF9.toChar(), 0xFFFB.toChar())
    )

    private val AMBIGUOUS = arrayOf<CharArray?>(
        charArrayOf(0x00A1.toChar(), 0x00A1.toChar()),
        charArrayOf(0x00A4.toChar(), 0x00A4.toChar()),
        charArrayOf(0x00A7.toChar(), 0x00A8.toChar()),
        charArrayOf(0x00AA.toChar(), 0x00AA.toChar()),
        charArrayOf(0x00AE.toChar(), 0x00AE.toChar()),
        charArrayOf(0x00B0.toChar(), 0x00B4.toChar()),
        charArrayOf(0x00B6.toChar(), 0x00BA.toChar()),
        charArrayOf(0x00BC.toChar(), 0x00BF.toChar()),
        charArrayOf(0x00C6.toChar(), 0x00C6.toChar()),
        charArrayOf(0x00D0.toChar(), 0x00D0.toChar()),
        charArrayOf(0x00D7.toChar(), 0x00D8.toChar()),
        charArrayOf(0x00DE.toChar(), 0x00E1.toChar()),
        charArrayOf(0x00E6.toChar(), 0x00E6.toChar()),
        charArrayOf(0x00E8.toChar(), 0x00EA.toChar()),
        charArrayOf(0x00EC.toChar(), 0x00ED.toChar()),
        charArrayOf(0x00F0.toChar(), 0x00F0.toChar()),
        charArrayOf(0x00F2.toChar(), 0x00F3.toChar()),
        charArrayOf(0x00F7.toChar(), 0x00FA.toChar()),
        charArrayOf(0x00FC.toChar(), 0x00FC.toChar()),
        charArrayOf(0x00FE.toChar(), 0x00FE.toChar()),
        charArrayOf(0x0101.toChar(), 0x0101.toChar()),
        charArrayOf(0x0111.toChar(), 0x0111.toChar()),
        charArrayOf(0x0113.toChar(), 0x0113.toChar()),
        charArrayOf(0x011B.toChar(), 0x011B.toChar()),
        charArrayOf(0x0126.toChar(), 0x0127.toChar()),
        charArrayOf(0x012B.toChar(), 0x012B.toChar()),
        charArrayOf(0x0131.toChar(), 0x0133.toChar()),
        charArrayOf(0x0138.toChar(), 0x0138.toChar()),
        charArrayOf(0x013F.toChar(), 0x0142.toChar()),
        charArrayOf(0x0144.toChar(), 0x0144.toChar()),
        charArrayOf(0x0148.toChar(), 0x014B.toChar()),
        charArrayOf(0x014D.toChar(), 0x014D.toChar()),
        charArrayOf(0x0152.toChar(), 0x0153.toChar()),
        charArrayOf(0x0166.toChar(), 0x0167.toChar()),
        charArrayOf(0x016B.toChar(), 0x016B.toChar()),
        charArrayOf(0x01CE.toChar(), 0x01CE.toChar()),
        charArrayOf(0x01D0.toChar(), 0x01D0.toChar()),
        charArrayOf(0x01D2.toChar(), 0x01D2.toChar()),
        charArrayOf(0x01D4.toChar(), 0x01D4.toChar()),
        charArrayOf(0x01D6.toChar(), 0x01D6.toChar()),
        charArrayOf(0x01D8.toChar(), 0x01D8.toChar()),
        charArrayOf(0x01DA.toChar(), 0x01DA.toChar()),
        charArrayOf(0x01DC.toChar(), 0x01DC.toChar()),
        charArrayOf(0x0251.toChar(), 0x0251.toChar()),
        charArrayOf(0x0261.toChar(), 0x0261.toChar()),
        charArrayOf(0x02C4.toChar(), 0x02C4.toChar()),
        charArrayOf(0x02C7.toChar(), 0x02C7.toChar()),
        charArrayOf(0x02C9.toChar(), 0x02CB.toChar()),
        charArrayOf(0x02CD.toChar(), 0x02CD.toChar()),
        charArrayOf(0x02D0.toChar(), 0x02D0.toChar()),
        charArrayOf(0x02D8.toChar(), 0x02DB.toChar()),
        charArrayOf(0x02DD.toChar(), 0x02DD.toChar()),
        charArrayOf(0x02DF.toChar(), 0x02DF.toChar()),
        charArrayOf(0x0391.toChar(), 0x03A1.toChar()),
        charArrayOf(0x03A3.toChar(), 0x03A9.toChar()),
        charArrayOf(0x03B1.toChar(), 0x03C1.toChar()),
        charArrayOf(0x03C3.toChar(), 0x03C9.toChar()),
        charArrayOf(0x0401.toChar(), 0x0401.toChar()),
        charArrayOf(0x0410.toChar(), 0x044F.toChar()),
        charArrayOf(0x0451.toChar(), 0x0451.toChar()),
        charArrayOf(0x2010.toChar(), 0x2010.toChar()),
        charArrayOf(0x2013.toChar(), 0x2016.toChar()),
        charArrayOf(0x2018.toChar(), 0x2019.toChar()),
        charArrayOf(0x201C.toChar(), 0x201D.toChar()),
        charArrayOf(0x2020.toChar(), 0x2022.toChar()),
        charArrayOf(0x2024.toChar(), 0x2027.toChar()),
        charArrayOf(0x2030.toChar(), 0x2030.toChar()),
        charArrayOf(0x2032.toChar(), 0x2033.toChar()),
        charArrayOf(0x2035.toChar(), 0x2035.toChar()),
        charArrayOf(0x203B.toChar(), 0x203B.toChar()),
        charArrayOf(0x203E.toChar(), 0x203E.toChar()),
        charArrayOf(0x2074.toChar(), 0x2074.toChar()),
        charArrayOf(0x207F.toChar(), 0x207F.toChar()),
        charArrayOf(0x2081.toChar(), 0x2084.toChar()),
        charArrayOf(0x20AC.toChar(), 0x20AC.toChar()),
        charArrayOf(0x2103.toChar(), 0x2103.toChar()),
        charArrayOf(0x2105.toChar(), 0x2105.toChar()),
        charArrayOf(0x2109.toChar(), 0x2109.toChar()),
        charArrayOf(0x2113.toChar(), 0x2113.toChar()),
        charArrayOf(0x2116.toChar(), 0x2116.toChar()),
        charArrayOf(0x2121.toChar(), 0x2122.toChar()),
        charArrayOf(0x2126.toChar(), 0x2126.toChar()),
        charArrayOf(0x212B.toChar(), 0x212B.toChar()),
        charArrayOf(0x2153.toChar(), 0x2154.toChar()),
        charArrayOf(0x215B.toChar(), 0x215E.toChar()),
        charArrayOf(0x2160.toChar(), 0x216B.toChar()),
        charArrayOf(0x2170.toChar(), 0x2179.toChar()),
        charArrayOf(0x2190.toChar(), 0x2199.toChar()),
        charArrayOf(0x21B8.toChar(), 0x21B9.toChar()),
        charArrayOf(0x21D2.toChar(), 0x21D2.toChar()),
        charArrayOf(0x21D4.toChar(), 0x21D4.toChar()),
        charArrayOf(0x21E7.toChar(), 0x21E7.toChar()),
        charArrayOf(0x2200.toChar(), 0x2200.toChar()),
        charArrayOf(0x2202.toChar(), 0x2203.toChar()),
        charArrayOf(0x2207.toChar(), 0x2208.toChar()),
        charArrayOf(0x220B.toChar(), 0x220B.toChar()),
        charArrayOf(0x220F.toChar(), 0x220F.toChar()),
        charArrayOf(0x2211.toChar(), 0x2211.toChar()),
        charArrayOf(0x2215.toChar(), 0x2215.toChar()),
        charArrayOf(0x221A.toChar(), 0x221A.toChar()),
        charArrayOf(0x221D.toChar(), 0x2220.toChar()),
        charArrayOf(0x2223.toChar(), 0x2223.toChar()),
        charArrayOf(0x2225.toChar(), 0x2225.toChar()),
        charArrayOf(0x2227.toChar(), 0x222C.toChar()),
        charArrayOf(0x222E.toChar(), 0x222E.toChar()),
        charArrayOf(0x2234.toChar(), 0x2237.toChar()),
        charArrayOf(0x223C.toChar(), 0x223D.toChar()),
        charArrayOf(0x2248.toChar(), 0x2248.toChar()),
        charArrayOf(0x224C.toChar(), 0x224C.toChar()),
        charArrayOf(0x2252.toChar(), 0x2252.toChar()),
        charArrayOf(0x2260.toChar(), 0x2261.toChar()),
        charArrayOf(0x2264.toChar(), 0x2267.toChar()),
        charArrayOf(0x226A.toChar(), 0x226B.toChar()),
        charArrayOf(0x226E.toChar(), 0x226F.toChar()),
        charArrayOf(0x2282.toChar(), 0x2283.toChar()),
        charArrayOf(0x2286.toChar(), 0x2287.toChar()),
        charArrayOf(0x2295.toChar(), 0x2295.toChar()),
        charArrayOf(0x2299.toChar(), 0x2299.toChar()),
        charArrayOf(0x22A5.toChar(), 0x22A5.toChar()),
        charArrayOf(0x22BF.toChar(), 0x22BF.toChar()),
        charArrayOf(0x2312.toChar(), 0x2312.toChar()),
        charArrayOf(0x2460.toChar(), 0x24E9.toChar()),
        charArrayOf(0x24EB.toChar(), 0x254B.toChar()),
        charArrayOf(0x2550.toChar(), 0x2573.toChar()),
        charArrayOf(0x2580.toChar(), 0x258F.toChar()),
        charArrayOf(0x2592.toChar(), 0x2595.toChar()),
        charArrayOf(0x25A0.toChar(), 0x25A1.toChar()),
        charArrayOf(0x25A3.toChar(), 0x25A9.toChar()),
        charArrayOf(0x25B2.toChar(), 0x25B3.toChar()),
        charArrayOf(0x25B6.toChar(), 0x25B7.toChar()),
        charArrayOf(0x25BC.toChar(), 0x25BD.toChar()),
        charArrayOf(0x25C0.toChar(), 0x25C1.toChar()),
        charArrayOf(0x25C6.toChar(), 0x25C8.toChar()),
        charArrayOf(0x25CB.toChar(), 0x25CB.toChar()),
        charArrayOf(0x25CE.toChar(), 0x25D1.toChar()),
        charArrayOf(0x25E2.toChar(), 0x25E5.toChar()),
        charArrayOf(0x25EF.toChar(), 0x25EF.toChar()),
        charArrayOf(0x2605.toChar(), 0x2606.toChar()),
        charArrayOf(0x2609.toChar(), 0x2609.toChar()),
        charArrayOf(0x260E.toChar(), 0x260F.toChar()),
        charArrayOf(0x2614.toChar(), 0x2615.toChar()),
        charArrayOf(0x261C.toChar(), 0x261C.toChar()),
        charArrayOf(0x261E.toChar(), 0x261E.toChar()),
        charArrayOf(0x2640.toChar(), 0x2640.toChar()),
        charArrayOf(0x2642.toChar(), 0x2642.toChar()),
        charArrayOf(0x2660.toChar(), 0x2661.toChar()),
        charArrayOf(0x2663.toChar(), 0x2665.toChar()),
        charArrayOf(0x2667.toChar(), 0x266A.toChar()),
        charArrayOf(0x266C.toChar(), 0x266D.toChar()),
        charArrayOf(0x266F.toChar(), 0x266F.toChar()),
        charArrayOf(0x273D.toChar(), 0x273D.toChar()),
        charArrayOf(0x2776.toChar(), 0x277F.toChar()),
        charArrayOf(0xE000.toChar(), 0xF8FF.toChar()),
        charArrayOf(0xFFFD.toChar(), 0xFFFD.toChar())
    )


    /* auxiliary function for binary search in interval table */
    fun bisearch(ucs: Char, table: Array<CharArray?>, max: Int): Int {
        var max = max
        var min = 0
        var mid: Int

        val first = table[0] ?: return 0
        val last = table[max] ?: return 0
        if (ucs < first[0] || ucs > last[1]) return 0
        while (max >= min) {
            mid = (min + max) / 2
            val midEntry = table[mid] ?: return 0
            if (ucs > midEntry[1]) min = mid + 1
            else if (ucs < midEntry[0]) max = mid - 1
            else return 1
        }

        return 0
    }

    internal fun mk_wcwidth(ucs: Int, ambiguousIsDoubleWidth: Boolean): Int {
        /* sorted list of non-overlapping intervals of non-spacing characters */
        /* generated by "uniset +cat=Me +cat=Mn +cat=Cf -00AD +1160-11FF +200B c" */

        /* test for8-bnew char[]it control characters */

        if (ucs == 0) return 0
        if (ucs < 32 || (ucs >= 0x7f && ucs < 0xa0)) return -1

        if (ambiguousIsDoubleWidth) {
            if (bisearch(ucs.toChar(), AMBIGUOUS, AMBIGUOUS.size - 1) > 0) {
                return 2
            }
        }


        /* binary search in table of non-spacing characters */
        if (bisearch(ucs.toChar(), COMBINING, COMBINING.size - 1) > 0) {
            return 0
        }

        /* if we arrive here, ucs is not a combining or C0/C1 control character */
        return 1 +
                (if (ucs >= 0x1100 &&
                    (ucs <= 0x115f ||  /* Hangul Jamo init. consonants */ucs == 0x2329 || ucs == 0x232a ||
                            (ucs >= 0x2e80 && ucs <= 0xa4cf && ucs != 0x303f) ||  /* CJK ... Yi */
                            (ucs >= 0xac00 && ucs <= 0xd7a3) ||  /* Hangul Syllables */
                            (ucs >= 0xf900 && ucs <= 0xfaff) ||  /* CJK Compatibility Ideographs */
                            (ucs >= 0xfe10 && ucs <= 0xfe19) ||  /* Vertical forms */
                            (ucs >= 0xfe30 && ucs <= 0xfe6f) ||  /* CJK Compatibility Forms */
                            (ucs >= 0xff00 && ucs <= 0xff60) ||  /* Fullwidth Forms */
                            (ucs >= 0xffe0 && ucs <= 0xffe6) ||
                            (ucs >= 0x20000 && ucs <= 0x2fffd) ||
                            (ucs >= 0x30000 && ucs <= 0x3fffd))
                ) 1 else 0)
    }

    fun toHumanReadableText(escapeSequence: String): String {
        return escapeSequence.replace("\u001b", "ESC")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u0007", "BEL")
            .replace(" ", "<S>")
            .replace("\t", "TAB")
            .replace("\b", "\\b")
    }

    enum class CharacterType {
        NONPRINTING,
        PRINTING,
        NONASCII, NONE
    }
}
