package ai.rever.bossterm.core.typeahead

import java.util.*

interface TypeAheadTerminalModel {
    fun insertCharacter(ch: Char, index: Int)

    fun removeCharacters(from: Int, count: Int)

    fun moveCursor(index: Int)

    fun forceRedraw()

    fun clearPredictions()

    fun lock()

    fun unlock()

    val isUsingAlternateBuffer: Boolean

    val currentLineWithCursor: LineWithCursorX

    val terminalWidth: Int

    val isTypeAheadEnabled: Boolean

    val latencyThreshold: Long

    val shellType: ShellType?

    enum class ShellType {
        Bash,
        Zsh,
        Unknown
    }

    class LineWithCursorX(val myLineText: StringBuffer, var myCursorX: Int) {
        fun copy(): LineWithCursorX {
            return LineWithCursorX(StringBuffer(myLineText), myCursorX)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LineWithCursorX) return false
            return myCursorX == other.myCursorX
                    && myLineText.toString().trimEnd() == other.myLineText.toString().trimEnd()
        }

        override fun hashCode(): Int {
            return Objects.hash(myLineText, myCursorX)
        }

        fun moveToWordBoundary(isDirectionRight: Boolean, shellType: ShellType) {
            when (shellType) {
                ShellType.Bash -> moveToWordBoundaryBash(isDirectionRight)
                ShellType.Zsh -> moveToWordBoundaryZsh(isDirectionRight)
                else -> moveToWordBoundaryBash(isDirectionRight)
            }
        }

        private fun moveToWordBoundaryZsh(isDirectionRight: Boolean) {
            val text = myLineText.toString()

            // https://github.com/zsh-users/zsh/blob/00d20ed15e18f5af682f0daec140d6b8383c479a/Src/zsh_system.h#L452
            val defaultWordChars = "*?_-.[]~=/&;!#$%^(){}<>"
            if (isDirectionRight) {
                while (myCursorX < text.length
                    && (Character.isLetterOrDigit(text.get(myCursorX)) || defaultWordChars.indexOf(text.get(myCursorX)) != -1)
                ) {
                    myCursorX++
                }
                while (myCursorX < text.length
                    && !(Character.isLetterOrDigit(text.get(myCursorX)) || defaultWordChars.indexOf(text.get(myCursorX)) != -1)
                ) {
                    myCursorX++
                }
            } else {
                myCursorX--
                while (myCursorX >= 0
                    && !(Character.isLetterOrDigit(text.get(myCursorX)) || defaultWordChars.indexOf(text.get(myCursorX)) != -1)
                ) {
                    myCursorX--
                }
                while (myCursorX >= 0
                    && (Character.isLetterOrDigit(text.get(myCursorX)) || defaultWordChars.indexOf(text.get(myCursorX)) != -1)
                ) {
                    myCursorX--
                }
                myCursorX++
            }
        }

        private fun moveToWordBoundaryBash(isDirectionRight: Boolean) {
            val text = myLineText.toString()

            if (!isDirectionRight) {
                myCursorX -= 1
            }

            var ateLeadingWhitespace = false
            while (myCursorX >= 0) {
                if (myCursorX >= text.length) {
                    return
                }

                val currentChar = text.get(myCursorX)
                if (Character.isLetterOrDigit(currentChar)) {
                    ateLeadingWhitespace = true
                } else if (ateLeadingWhitespace) {
                    break
                }

                myCursorX += if (isDirectionRight) 1 else -1
            }

            if (!isDirectionRight) {
                myCursorX += 1
            }
        }
    }

    companion object {
        fun commandLineToShellType(commandLine: MutableList<String>?): ShellType {
            if (commandLine == null || commandLine.isEmpty()) return ShellType.Unknown
            val command = commandLine.get(0)

            if (command.endsWith("bash")) return ShellType.Bash
            else if (command.endsWith("zsh")) return ShellType.Zsh
            else return ShellType.Unknown
        }
    }
}
