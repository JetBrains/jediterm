package ai.rever.bossterm.compose.menu

/**
 * Holds callback functions for menu bar actions.
 * These callbacks are wired up by TabbedTerminal when it initializes.
 */
class MenuActions {
    // File menu actions
    var onNewTab: (() -> Unit)? = null
    var onCloseTab: (() -> Unit)? = null

    // Edit menu actions
    var onCopy: (() -> Unit)? = null
    var onPaste: (() -> Unit)? = null
    var onSelectAll: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onFind: (() -> Unit)? = null

    // View menu actions
    var onToggleDebug: (() -> Unit)? = null

    // Window menu actions
    var onNextTab: (() -> Unit)? = null
    var onPreviousTab: (() -> Unit)? = null
}
