package com.jediterm.terminal

import com.jediterm.terminal.TextStyle.Builder
import com.jediterm.terminal.model.hyperlinks.LinkInfo

/**
 * @author traff
 */
class HyperlinkStyle private constructor(
    keepColors: Boolean,
    foreground: TerminalColor?,
    background: TerminalColor?,
    val linkInfo: LinkInfo,
    val highlightMode: HighlightMode,
    val prevTextStyle: TextStyle?
) : TextStyle(if (keepColors) foreground else null, if (keepColors) background else null) {
    val highlightStyle: TextStyle

    constructor(prevTextStyle: TextStyle, hyperlinkInfo: LinkInfo) : this(
        prevTextStyle.foreground,
        prevTextStyle.background,
        hyperlinkInfo,
        HighlightMode.HOVER,
        prevTextStyle
    )

    constructor(
        foreground: TerminalColor?,
        background: TerminalColor?,
        hyperlinkInfo: LinkInfo,
        mode: HighlightMode,
        prevTextStyle: TextStyle?
    ) : this(false, foreground, background, hyperlinkInfo, mode, prevTextStyle)

    init {
        this.highlightStyle = Builder()
            .setBackground(background)
            .setForeground(foreground)
            .setOption(Option.UNDERLINED, true)
            .build()
    }

    override fun toBuilder(): Builder {
        return Builder(this)
    }

    enum class HighlightMode {
        ALWAYS, NEVER, HOVER
    }

    class Builder internal constructor(style: HyperlinkStyle) : TextStyle.Builder() {
        private val myLinkInfo: LinkInfo

        private val myHighlightStyle: TextStyle

        private val myPrevTextStyle: TextStyle?

        private val myHighlightMode: HighlightMode

        init {
            myLinkInfo = style.linkInfo
            myHighlightStyle = style.highlightStyle
            myPrevTextStyle = style.prevTextStyle
            myHighlightMode = style.highlightMode
        }

        override fun build(): HyperlinkStyle {
            return build(false)
        }

        fun build(keepColors: Boolean): HyperlinkStyle {
            var foreground = myHighlightStyle.foreground
            var background = myHighlightStyle.background
            if (keepColors) {
                val style = super.build()
                foreground =
                    if (style.foreground != null) style.foreground else myHighlightStyle.foreground
                background =
                    if (style.background != null) style.background else myHighlightStyle.background
            }
            return HyperlinkStyle(keepColors, foreground, background, myLinkInfo, myHighlightMode, myPrevTextStyle)
        }
    }
}
