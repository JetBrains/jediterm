package ai.rever.bossterm.compose.splits

import ai.rever.bossterm.compose.TerminalSession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import java.util.UUID

/**
 * Manages the state of split views within a single tab.
 *
 * This class handles:
 * - The split tree structure (rootNode)
 * - Focus tracking (which pane is active)
 * - Pane bounds tracking for spatial navigation
 * - Session lifecycle management
 */
class SplitViewState(
    initialSession: TerminalSession,
    private val sessionFactory: (() -> TerminalSession)? = null
) {
    /**
     * The root of the split tree. Initially a single pane.
     */
    var rootNode: SplitNode by mutableStateOf(
        SplitNode.Pane(session = initialSession)
    )
        private set

    /**
     * ID of the currently focused pane.
     */
    var focusedPaneId: String by mutableStateOf(
        (rootNode as SplitNode.Pane).id
    )
        private set

    /**
     * Bounds of each pane, updated during composition.
     * Used for spatial navigation between panes.
     */
    val paneBounds = mutableStateMapOf<String, Rect>()

    /**
     * Check if there's only one pane (no splits).
     */
    val isSinglePane: Boolean
        get() = rootNode is SplitNode.Pane

    /**
     * Get the currently focused pane.
     */
    fun getFocusedPane(): SplitNode.Pane? {
        return rootNode.findPane(focusedPaneId)
    }

    /**
     * Get the focused session.
     */
    fun getFocusedSession(): TerminalSession? {
        return getFocusedPane()?.session
    }

    /**
     * Get all sessions in the split tree.
     */
    fun getAllSessions(): List<TerminalSession> {
        return rootNode.getAllSessions()
    }

    /**
     * Get all panes in the split tree.
     */
    fun getAllPanes(): List<SplitNode.Pane> {
        return rootNode.getAllPanes()
    }

    /**
     * Set focus to a specific pane.
     */
    fun setFocusedPane(paneId: String) {
        if (rootNode.findPane(paneId) != null) {
            focusedPaneId = paneId
        }
    }

    /**
     * Update the bounds of a pane (called from composition).
     */
    fun updatePaneBounds(paneId: String, bounds: Rect) {
        paneBounds[paneId] = bounds
    }

    /**
     * Split the focused pane in the given orientation.
     * Returns the ID of the new pane, or null if split failed.
     *
     * @param orientation Whether to split horizontally (top/bottom) or vertically (left/right)
     * @param newSession The session for the new pane
     * @param ratio The initial split ratio (0.0 to 1.0, default 0.5)
     */
    fun splitFocusedPane(orientation: SplitOrientation, newSession: TerminalSession, ratio: Float = 0.5f): String? {
        val currentPane = getFocusedPane() ?: return null
        val newPaneId = UUID.randomUUID().toString()
        val newPane = SplitNode.Pane(id = newPaneId, session = newSession)

        rootNode = rootNode.replaceNode(currentPane.id) { pane ->
            when (orientation) {
                SplitOrientation.HORIZONTAL -> SplitNode.HorizontalSplit(
                    top = pane,
                    bottom = newPane,
                    ratio = ratio
                )
                SplitOrientation.VERTICAL -> SplitNode.VerticalSplit(
                    left = pane,
                    right = newPane,
                    ratio = ratio
                )
            }
        }

        // Focus the new pane
        focusedPaneId = newPaneId
        return newPaneId
    }

    /**
     * Close the focused pane.
     * Returns true if closed, false if it's the last pane.
     */
    fun closeFocusedPane(): Boolean {
        return closePane(focusedPaneId)
    }

    /**
     * Close a specific pane.
     * Returns true if closed, false if it's the last pane.
     */
    fun closePane(paneId: String): Boolean {
        // Can't close the last pane
        if (isSinglePane) return false

        val paneToClose = rootNode.findPane(paneId) ?: return false

        // Dispose the session
        paneToClose.session.dispose()

        // Remove from bounds tracking
        paneBounds.remove(paneId)

        // Find a new pane to focus before removal
        val allPanes = getAllPanes()
        val newFocusPane = allPanes.firstOrNull { it.id != paneId }

        // Remove the pane from the tree
        val newRoot = rootNode.removePane(paneId)
        if (newRoot != null) {
            rootNode = newRoot

            // Update focus
            if (focusedPaneId == paneId && newFocusPane != null) {
                focusedPaneId = newFocusPane.id
            }
            return true
        }
        return false
    }

    /**
     * Extract the focused pane's session and remove the pane from the tree.
     *
     * This is used when moving a pane to a new tab. Unlike closePane(),
     * this method does NOT dispose the session - it returns the session
     * so it can be reused in a new tab.
     *
     * Returns the extracted session, or null if:
     * - It's the last pane (can't extract)
     * - The focused pane doesn't exist
     */
    fun extractFocusedPaneSession(): TerminalSession? {
        // Can't extract the last pane - would leave tab empty
        if (isSinglePane) return null

        val paneToExtract = getFocusedPane() ?: return null
        val session = paneToExtract.session
        val paneId = paneToExtract.id

        // Remove from bounds tracking
        paneBounds.remove(paneId)

        // Find a new pane to focus before removal
        val allPanes = getAllPanes()
        val newFocusPane = allPanes.firstOrNull { it.id != paneId }

        // Remove the pane from the tree (but DON'T dispose the session)
        val newRoot = rootNode.removePane(paneId)
        if (newRoot != null) {
            rootNode = newRoot

            // Update focus
            if (newFocusPane != null) {
                focusedPaneId = newFocusPane.id
            }
            return session
        }
        return null
    }

    /**
     * Update the ratio of a split.
     * @param minRatio Minimum allowed ratio (default 0.1f). Max ratio is 1 - minRatio.
     */
    fun updateSplitRatio(splitId: String, newRatio: Float, minRatio: Float = 0.1f) {
        rootNode = rootNode.updateRatio(splitId, newRatio, minRatio)
    }

    /**
     * Navigate focus to the next pane in the given direction.
     * Uses spatial navigation based on pane bounds.
     */
    fun navigateFocus(direction: NavigationDirection) {
        val currentBounds = paneBounds[focusedPaneId] ?: return
        val candidates = findPanesInDirection(currentBounds, direction)
        candidates.firstOrNull()?.let { (paneId, _) ->
            focusedPaneId = paneId
        }
    }

    /**
     * Navigate to the next pane (cycles through all panes).
     */
    fun navigateToNextPane() {
        val allPanes = getAllPanes()
        if (allPanes.size <= 1) return

        val currentIndex = allPanes.indexOfFirst { it.id == focusedPaneId }
        val nextIndex = (currentIndex + 1) % allPanes.size
        focusedPaneId = allPanes[nextIndex].id
    }

    /**
     * Navigate to the previous pane (cycles through all panes).
     */
    fun navigateToPreviousPane() {
        val allPanes = getAllPanes()
        if (allPanes.size <= 1) return

        val currentIndex = allPanes.indexOfFirst { it.id == focusedPaneId }
        val prevIndex = if (currentIndex <= 0) allPanes.size - 1 else currentIndex - 1
        focusedPaneId = allPanes[prevIndex].id
    }

    /**
     * Find panes in a given direction from the current bounds.
     * Returns list of (paneId, bounds) sorted by distance.
     */
    private fun findPanesInDirection(
        currentBounds: Rect,
        direction: NavigationDirection
    ): List<Pair<String, Rect>> {
        return paneBounds.entries
            .filter { (id, _) -> id != focusedPaneId }
            .filter { (_, bounds) -> isInDirection(currentBounds, bounds, direction) }
            .map { (id, bounds) -> id to bounds }
            .sortedBy { (_, bounds) -> distanceInDirection(currentBounds, bounds, direction) }
    }

    /**
     * Check if targetBounds is in the given direction from currentBounds.
     */
    private fun isInDirection(current: Rect, target: Rect, direction: NavigationDirection): Boolean {
        return when (direction) {
            NavigationDirection.UP -> target.bottom <= current.top && hasHorizontalOverlap(current, target)
            NavigationDirection.DOWN -> target.top >= current.bottom && hasHorizontalOverlap(current, target)
            NavigationDirection.LEFT -> target.right <= current.left && hasVerticalOverlap(current, target)
            NavigationDirection.RIGHT -> target.left >= current.right && hasVerticalOverlap(current, target)
        }
    }

    /**
     * Calculate distance in the given direction.
     */
    private fun distanceInDirection(current: Rect, target: Rect, direction: NavigationDirection): Float {
        return when (direction) {
            NavigationDirection.UP -> current.top - target.bottom
            NavigationDirection.DOWN -> target.top - current.bottom
            NavigationDirection.LEFT -> current.left - target.right
            NavigationDirection.RIGHT -> target.left - current.right
        }
    }

    /**
     * Check if two rectangles have horizontal overlap (for up/down navigation).
     */
    private fun hasHorizontalOverlap(a: Rect, b: Rect): Boolean {
        return a.left < b.right && a.right > b.left
    }

    /**
     * Check if two rectangles have vertical overlap (for left/right navigation).
     */
    private fun hasVerticalOverlap(a: Rect, b: Rect): Boolean {
        return a.top < b.bottom && a.bottom > b.top
    }

    /**
     * Dispose all sessions in the split tree.
     */
    fun dispose() {
        getAllSessions().forEach { session ->
            try {
                session.dispose()
            } catch (e: Exception) {
                println("Error disposing session: ${e.message}")
            }
        }
        paneBounds.clear()
    }
}
