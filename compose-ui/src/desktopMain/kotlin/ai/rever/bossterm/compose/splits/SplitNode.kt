package ai.rever.bossterm.compose.splits

import ai.rever.bossterm.compose.TerminalSession
import java.util.UUID

/**
 * Represents a node in the split view tree.
 *
 * The split view uses a binary tree structure where:
 * - Leaf nodes (Pane) contain a terminal session
 * - Internal nodes (VerticalSplit, HorizontalSplit) contain two children
 */
sealed class SplitNode {
    abstract val id: String

    /**
     * A leaf node containing a single terminal session.
     */
    data class Pane(
        override val id: String = UUID.randomUUID().toString(),
        val session: TerminalSession
    ) : SplitNode()

    /**
     * A vertical split with left and right children.
     * The divider runs vertically (children are side by side).
     */
    data class VerticalSplit(
        override val id: String = UUID.randomUUID().toString(),
        val left: SplitNode,
        val right: SplitNode,
        val ratio: Float = 0.5f  // 0.0 to 1.0, proportion of left child
    ) : SplitNode() {
        init {
            require(ratio in 0.1f..0.9f) { "Split ratio must be between 0.1 and 0.9" }
        }
    }

    /**
     * A horizontal split with top and bottom children.
     * The divider runs horizontally (children are stacked).
     */
    data class HorizontalSplit(
        override val id: String = UUID.randomUUID().toString(),
        val top: SplitNode,
        val bottom: SplitNode,
        val ratio: Float = 0.5f  // 0.0 to 1.0, proportion of top child
    ) : SplitNode() {
        init {
            require(ratio in 0.1f..0.9f) { "Split ratio must be between 0.1 and 0.9" }
        }
    }
}

/**
 * Find a node by ID in the tree.
 */
fun SplitNode.findNode(targetId: String): SplitNode? {
    if (this.id == targetId) return this
    return when (this) {
        is SplitNode.Pane -> null
        is SplitNode.VerticalSplit -> left.findNode(targetId) ?: right.findNode(targetId)
        is SplitNode.HorizontalSplit -> top.findNode(targetId) ?: bottom.findNode(targetId)
    }
}

/**
 * Find a pane by ID in the tree.
 */
fun SplitNode.findPane(targetId: String): SplitNode.Pane? {
    return when (this) {
        is SplitNode.Pane -> if (this.id == targetId) this else null
        is SplitNode.VerticalSplit -> left.findPane(targetId) ?: right.findPane(targetId)
        is SplitNode.HorizontalSplit -> top.findPane(targetId) ?: bottom.findPane(targetId)
    }
}

/**
 * Get all panes in the tree (leaf nodes).
 */
fun SplitNode.getAllPanes(): List<SplitNode.Pane> {
    return when (this) {
        is SplitNode.Pane -> listOf(this)
        is SplitNode.VerticalSplit -> left.getAllPanes() + right.getAllPanes()
        is SplitNode.HorizontalSplit -> top.getAllPanes() + bottom.getAllPanes()
    }
}

/**
 * Get all sessions in the tree.
 */
fun SplitNode.getAllSessions(): List<TerminalSession> {
    return getAllPanes().map { it.session }
}

/**
 * Replace a node in the tree with a new node.
 * Returns the new tree root, or the original if targetId not found.
 */
fun SplitNode.replaceNode(targetId: String, transform: (SplitNode) -> SplitNode): SplitNode {
    if (this.id == targetId) return transform(this)
    return when (this) {
        is SplitNode.Pane -> this
        is SplitNode.VerticalSplit -> copy(
            left = left.replaceNode(targetId, transform),
            right = right.replaceNode(targetId, transform)
        )
        is SplitNode.HorizontalSplit -> copy(
            top = top.replaceNode(targetId, transform),
            bottom = bottom.replaceNode(targetId, transform)
        )
    }
}

/**
 * Remove a pane from the tree, collapsing its parent split.
 * Returns null if the pane is the root (can't remove last pane).
 */
fun SplitNode.removePane(targetId: String): SplitNode? {
    // Can't remove if this is the target and it's a pane (would leave empty tree)
    if (this is SplitNode.Pane && this.id == targetId) return null

    return when (this) {
        is SplitNode.Pane -> this
        is SplitNode.VerticalSplit -> {
            when {
                left.id == targetId -> right
                right.id == targetId -> left
                else -> {
                    val newLeft = left.removePane(targetId)
                    val newRight = right.removePane(targetId)
                    when {
                        newLeft == null -> right
                        newRight == null -> left
                        newLeft != left || newRight != right -> copy(left = newLeft, right = newRight)
                        else -> this
                    }
                }
            }
        }
        is SplitNode.HorizontalSplit -> {
            when {
                top.id == targetId -> bottom
                bottom.id == targetId -> top
                else -> {
                    val newTop = top.removePane(targetId)
                    val newBottom = bottom.removePane(targetId)
                    when {
                        newTop == null -> bottom
                        newBottom == null -> top
                        newTop != top || newBottom != bottom -> copy(top = newTop, bottom = newBottom)
                        else -> this
                    }
                }
            }
        }
    }
}

/**
 * Update the ratio of a split node.
 */
fun SplitNode.updateRatio(targetId: String, newRatio: Float): SplitNode {
    val clampedRatio = newRatio.coerceIn(0.1f, 0.9f)
    return when (this) {
        is SplitNode.Pane -> this
        is SplitNode.VerticalSplit -> {
            if (this.id == targetId) {
                copy(ratio = clampedRatio)
            } else {
                copy(
                    left = left.updateRatio(targetId, newRatio),
                    right = right.updateRatio(targetId, newRatio)
                )
            }
        }
        is SplitNode.HorizontalSplit -> {
            if (this.id == targetId) {
                copy(ratio = clampedRatio)
            } else {
                copy(
                    top = top.updateRatio(targetId, newRatio),
                    bottom = bottom.updateRatio(targetId, newRatio)
                )
            }
        }
    }
}

/**
 * Get the parent split ID for a given node.
 * Returns null if the node is the root or not found.
 */
fun SplitNode.findParentId(targetId: String): String? {
    return when (this) {
        is SplitNode.Pane -> null
        is SplitNode.VerticalSplit -> {
            when {
                left.id == targetId || right.id == targetId -> this.id
                else -> left.findParentId(targetId) ?: right.findParentId(targetId)
            }
        }
        is SplitNode.HorizontalSplit -> {
            when {
                top.id == targetId || bottom.id == targetId -> this.id
                else -> top.findParentId(targetId) ?: bottom.findParentId(targetId)
            }
        }
    }
}
