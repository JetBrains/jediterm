package ai.rever.bossterm.core

import kotlin.math.max

class Color {
    val rGB: Int

    @JvmOverloads
    constructor(r: Int, g: Int, b: Int, a: Int = 255) {
        this.rGB = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    constructor(rgb: Int) : this(rgb, false)

    constructor(rgba: Int, hasAlpha: Boolean) {
        if (hasAlpha) {
            this.rGB = rgba
        } else {
            this.rGB = -0x1000000 or rgba
        }
    }

    val red: Int
        get() = (this.rGB shr 16) and 0xFF

    val green: Int
        get() = (this.rGB shr 8) and 0xFF

    val blue: Int
        get() = this.rGB and 0xFF

    val alpha: Int
        get() = (this.rGB shr 24) and 0xff

    /**
     * Returns [XParseColor](https://linux.die.net/man/3/xparsecolor) representation of TerminalColor
     */
    fun toXParseColor(): String {
        return "rgb:" + toHexString16()
    }

    private fun toHexString16(): String {
        // (n * 0x101) converts the 8-bit number to 16 bits.
        val red = padStart(Integer.toHexString(this.red * 0x101), 4, '0')
        val green = padStart(Integer.toHexString(this.green * 0x101), 4, '0')
        val blue = padStart(Integer.toHexString(this.blue * 0x101), 4, '0')

        return red + "/" + green + "/" + blue
    }

    private fun padStart(str: String, totalLength: Int, ch: Char): String {
        return ch.toString().repeat(max(0, totalLength - str.length)) + str
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Color && other.rGB == this.rGB
    }

    override fun hashCode(): Int {
        return this.rGB
    }

    override fun toString(): String {
        return javaClass.getName() + "[r=" + this.red + ",g=" + this.green + ",b=" + this.blue + ", alpha=" + this.alpha + "]"
    }
}
