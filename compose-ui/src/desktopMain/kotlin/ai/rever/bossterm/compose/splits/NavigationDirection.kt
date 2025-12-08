package ai.rever.bossterm.compose.splits

/**
 * Direction for navigating between split panes.
 */
enum class NavigationDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    /**
     * Get the opposite direction.
     */
    fun opposite(): NavigationDirection = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }

    /**
     * Check if this direction is horizontal (left/right).
     */
    fun isHorizontal(): Boolean = this == LEFT || this == RIGHT

    /**
     * Check if this direction is vertical (up/down).
     */
    fun isVertical(): Boolean = this == UP || this == DOWN
}
