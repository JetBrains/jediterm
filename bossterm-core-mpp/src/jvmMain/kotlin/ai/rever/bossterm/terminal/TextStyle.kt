package ai.rever.bossterm.terminal

import java.util.*

open class TextStyle @JvmOverloads constructor(
    val foreground: TerminalColor? = null,
    val background: TerminalColor? = null,
    options: EnumSet<Option> = NO_OPTIONS
) {
    private val myOptions: EnumSet<Option>

    init {
        myOptions = options.clone()
    }

    fun createEmptyWithColors(): TextStyle {
        return TextStyle(this.foreground, this.background)
    }

    fun hasOption(option: Option?): Boolean {
        return myOptions.contains(option)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val textStyle = other as TextStyle
        return this.foreground == textStyle.foreground &&
                this.background == textStyle.background &&
                myOptions == textStyle.myOptions
    }

    override fun hashCode(): Int {
        return Objects.hash(this.foreground, this.background, myOptions)
    }

    open fun toBuilder(): Builder {
        return Builder(this)
    }

    enum class Option {
        BOLD,
        ITALIC,
        SLOW_BLINK,
        RAPID_BLINK,
        DIM,
        INVERSE,
        UNDERLINED,
        HIDDEN,
        PROTECTED;

        fun set(options: EnumSet<Option>, `val`: Boolean) {
            if (`val`) {
                options.add(this)
            } else {
                options.remove(this)
            }
        }
    }

    open class Builder {
        private var myForeground: TerminalColor?
        private var myBackground: TerminalColor?
        private val myOptions: EnumSet<Option>

        constructor(textStyle: TextStyle) {
            myForeground = textStyle.foreground
            myBackground = textStyle.background
            myOptions = textStyle.myOptions.clone()
        }

        constructor() {
            myForeground = null
            myBackground = null
            myOptions = EnumSet.noneOf(Option::class.java)
        }

        fun setForeground(foreground: TerminalColor?): Builder {
            myForeground = foreground
            return this
        }

        fun setBackground(background: TerminalColor?): Builder {
            myBackground = background
            return this
        }

        fun setOption(option: Option, `val`: Boolean): Builder {
            option.set(myOptions, `val`)
            return this
        }

        open fun build(): TextStyle {
            return TextStyle(myForeground, myBackground, myOptions)
        }
    }

    companion object {
        private val NO_OPTIONS: EnumSet<Option> = EnumSet.noneOf(Option::class.java)

        val EMPTY: TextStyle = TextStyle()
    }
}
