package ai.rever.bossterm.compose.splits

import ai.rever.bossterm.compose.TerminalSession
import ai.rever.bossterm.compose.menu.MenuActions
import ai.rever.bossterm.compose.ui.ProperTerminal
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Container that renders the split view tree.
 *
 * This composable recursively renders the SplitNode tree, handling:
 * - Pane rendering with ProperTerminal
 * - Vertical splits (left/right) with Row
 * - Horizontal splits (top/bottom) with Column
 * - Draggable dividers for resizing
 * - Focus indication for the active pane
 */
@Composable
fun SplitContainer(
    splitState: SplitViewState,
    sharedFont: FontFamily,
    isActiveTab: Boolean,
    onTabTitleChange: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onShowSettings: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClosePane: () -> Unit,
    onNavigatePane: (NavigationDirection) -> Unit,
    onNavigateNextPane: () -> Unit = {},
    onNavigatePreviousPane: () -> Unit = {},
    onMoveToNewTab: (() -> Unit)? = null,
    menuActions: MenuActions?,
    // Split pane settings
    splitFocusBorderEnabled: Boolean = true,
    splitFocusBorderColor: Color = Color(0xFF4A90E2),
    splitMinimumSize: Float = 0.1f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        RenderSplitNode(
            node = splitState.rootNode,
            splitState = splitState,
            sharedFont = sharedFont,
            isActiveTab = isActiveTab,
            onTabTitleChange = onTabTitleChange,
            onNewTab = onNewTab,
            onCloseTab = onCloseTab,
            onNextTab = onNextTab,
            onPreviousTab = onPreviousTab,
            onSwitchToTab = onSwitchToTab,
            onNewWindow = onNewWindow,
            onShowSettings = onShowSettings,
            onSplitHorizontal = onSplitHorizontal,
            onSplitVertical = onSplitVertical,
            onClosePane = onClosePane,
            onNavigatePane = onNavigatePane,
            onNavigateNextPane = onNavigateNextPane,
            onNavigatePreviousPane = onNavigatePreviousPane,
            onMoveToNewTab = onMoveToNewTab,
            menuActions = menuActions,
            splitFocusBorderEnabled = splitFocusBorderEnabled,
            splitFocusBorderColor = splitFocusBorderColor,
            splitMinimumSize = splitMinimumSize,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Recursively render a split node.
 */
@Composable
private fun RenderSplitNode(
    node: SplitNode,
    splitState: SplitViewState,
    sharedFont: FontFamily,
    isActiveTab: Boolean,
    onTabTitleChange: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onShowSettings: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClosePane: () -> Unit,
    onNavigatePane: (NavigationDirection) -> Unit,
    onNavigateNextPane: () -> Unit,
    onNavigatePreviousPane: () -> Unit,
    onMoveToNewTab: (() -> Unit)?,
    menuActions: MenuActions?,
    splitFocusBorderEnabled: Boolean,
    splitFocusBorderColor: Color,
    splitMinimumSize: Float,
    modifier: Modifier = Modifier
) {
    when (node) {
        is SplitNode.Pane -> {
            RenderPane(
                pane = node,
                splitState = splitState,
                sharedFont = sharedFont,
                isActiveTab = isActiveTab,
                isFocusedPane = node.id == splitState.focusedPaneId,
                onTabTitleChange = onTabTitleChange,
                onNewTab = onNewTab,
                onCloseTab = onCloseTab,
                onNextTab = onNextTab,
                onPreviousTab = onPreviousTab,
                onSwitchToTab = onSwitchToTab,
                onNewWindow = onNewWindow,
                onShowSettings = onShowSettings,
                onSplitHorizontal = onSplitHorizontal,
                onSplitVertical = onSplitVertical,
                onClosePane = onClosePane,
                onNavigatePane = onNavigatePane,
                onNavigateNextPane = onNavigateNextPane,
                onNavigatePreviousPane = onNavigatePreviousPane,
                onMoveToNewTab = onMoveToNewTab,
                menuActions = menuActions,
                splitFocusBorderEnabled = splitFocusBorderEnabled,
                splitFocusBorderColor = splitFocusBorderColor,
                modifier = modifier
            )
        }

        is SplitNode.VerticalSplit -> {
            RenderVerticalSplit(
                split = node,
                splitState = splitState,
                sharedFont = sharedFont,
                isActiveTab = isActiveTab,
                onTabTitleChange = onTabTitleChange,
                onNewTab = onNewTab,
                onCloseTab = onCloseTab,
                onNextTab = onNextTab,
                onPreviousTab = onPreviousTab,
                onSwitchToTab = onSwitchToTab,
                onNewWindow = onNewWindow,
                onShowSettings = onShowSettings,
                onSplitHorizontal = onSplitHorizontal,
                onSplitVertical = onSplitVertical,
                onClosePane = onClosePane,
                onNavigatePane = onNavigatePane,
                onNavigateNextPane = onNavigateNextPane,
                onNavigatePreviousPane = onNavigatePreviousPane,
                onMoveToNewTab = onMoveToNewTab,
                menuActions = menuActions,
                splitFocusBorderEnabled = splitFocusBorderEnabled,
                splitFocusBorderColor = splitFocusBorderColor,
                splitMinimumSize = splitMinimumSize,
                modifier = modifier
            )
        }

        is SplitNode.HorizontalSplit -> {
            RenderHorizontalSplit(
                split = node,
                splitState = splitState,
                sharedFont = sharedFont,
                isActiveTab = isActiveTab,
                onTabTitleChange = onTabTitleChange,
                onNewTab = onNewTab,
                onCloseTab = onCloseTab,
                onNextTab = onNextTab,
                onPreviousTab = onPreviousTab,
                onSwitchToTab = onSwitchToTab,
                onNewWindow = onNewWindow,
                onShowSettings = onShowSettings,
                onSplitHorizontal = onSplitHorizontal,
                onSplitVertical = onSplitVertical,
                onClosePane = onClosePane,
                onNavigatePane = onNavigatePane,
                onNavigateNextPane = onNavigateNextPane,
                onNavigatePreviousPane = onNavigatePreviousPane,
                onMoveToNewTab = onMoveToNewTab,
                menuActions = menuActions,
                splitFocusBorderEnabled = splitFocusBorderEnabled,
                splitFocusBorderColor = splitFocusBorderColor,
                splitMinimumSize = splitMinimumSize,
                modifier = modifier
            )
        }
    }
}

/**
 * Render a single pane with focus indication.
 */
@Composable
private fun RenderPane(
    pane: SplitNode.Pane,
    splitState: SplitViewState,
    sharedFont: FontFamily,
    isActiveTab: Boolean,
    isFocusedPane: Boolean,
    onTabTitleChange: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onShowSettings: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClosePane: () -> Unit,
    onNavigatePane: (NavigationDirection) -> Unit,
    onNavigateNextPane: () -> Unit,
    onNavigatePreviousPane: () -> Unit,
    onMoveToNewTab: (() -> Unit)?,
    menuActions: MenuActions?,
    splitFocusBorderEnabled: Boolean,
    splitFocusBorderColor: Color,
    modifier: Modifier = Modifier
) {
    // Focus border for active pane (only show when there are multiple panes and enabled)
    val borderModifier = if (!splitState.isSinglePane && splitFocusBorderEnabled) {
        if (isFocusedPane) {
            Modifier.border(2.dp, splitFocusBorderColor)
        } else {
            Modifier.border(1.dp, Color(0xFF404040))
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(borderModifier)
            .onGloballyPositioned { coordinates ->
                splitState.updatePaneBounds(pane.id, coordinates.boundsInWindow())
            }
            .pointerInput(pane.id) {
                detectTapGestures(
                    onPress = {
                        // Set focus when pane is pressed
                        splitState.setFocusedPane(pane.id)
                        // Allow the event to propagate to terminal for selection handling
                        tryAwaitRelease()
                    }
                )
            }
    ) {
        ProperTerminal(
            tab = pane.session,
            isActiveTab = isActiveTab && isFocusedPane,
            sharedFont = sharedFont,
            onTabTitleChange = { title ->
                // Only update window title if this is the focused pane
                if (isFocusedPane) {
                    onTabTitleChange(title)
                }
            },
            onNewTab = onNewTab,
            onCloseTab = {
                // If there are splits, close the pane; otherwise close the tab
                if (!splitState.isSinglePane) {
                    onClosePane()
                } else {
                    onCloseTab()
                }
            },
            onNextTab = onNextTab,
            onPreviousTab = onPreviousTab,
            onSwitchToTab = onSwitchToTab,
            onNewWindow = onNewWindow,
            onShowSettings = onShowSettings,
            onSplitHorizontal = onSplitHorizontal,
            onSplitVertical = onSplitVertical,
            onClosePane = onClosePane,
            onNavigatePane = onNavigatePane,
            onNavigateNextPane = onNavigateNextPane,
            onNavigatePreviousPane = onNavigatePreviousPane,
            onMoveToNewTab = onMoveToNewTab,
            menuActions = menuActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Render a vertical split (left/right).
 */
@Composable
private fun RenderVerticalSplit(
    split: SplitNode.VerticalSplit,
    splitState: SplitViewState,
    sharedFont: FontFamily,
    isActiveTab: Boolean,
    onTabTitleChange: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onShowSettings: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClosePane: () -> Unit,
    onNavigatePane: (NavigationDirection) -> Unit,
    onNavigateNextPane: () -> Unit,
    onNavigatePreviousPane: () -> Unit,
    onMoveToNewTab: (() -> Unit)?,
    menuActions: MenuActions?,
    splitFocusBorderEnabled: Boolean,
    splitFocusBorderColor: Color,
    splitMinimumSize: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val totalWidth = constraints.maxWidth.toFloat()
        val dividerWidthPx = with(density) { DIVIDER_THICKNESS.toPx() }
        val availableWidth = (totalWidth - dividerWidthPx).coerceAtLeast(1f)

        // Calculate left pane width in pixels for drag handle positioning
        val leftWidth = availableWidth * split.ratio

        Row(modifier = Modifier.fillMaxSize()) {
            // Left child
            Box(modifier = Modifier.weight(split.ratio)) {
                RenderSplitNode(
                    node = split.left,
                    splitState = splitState,
                    sharedFont = sharedFont,
                    isActiveTab = isActiveTab,
                    onTabTitleChange = onTabTitleChange,
                    onNewTab = onNewTab,
                    onCloseTab = onCloseTab,
                    onNextTab = onNextTab,
                    onPreviousTab = onPreviousTab,
                    onSwitchToTab = onSwitchToTab,
                    onNewWindow = onNewWindow,
                    onShowSettings = onShowSettings,
                    onSplitHorizontal = onSplitHorizontal,
                    onSplitVertical = onSplitVertical,
                    onClosePane = onClosePane,
                    onNavigatePane = onNavigatePane,
                    onNavigateNextPane = onNavigateNextPane,
                    onNavigatePreviousPane = onNavigatePreviousPane,
                    onMoveToNewTab = onMoveToNewTab,
                    menuActions = menuActions,
                    splitFocusBorderEnabled = splitFocusBorderEnabled,
                    splitFocusBorderColor = splitFocusBorderColor,
                    splitMinimumSize = splitMinimumSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Thin visible divider line (1dp)
            SplitDividerLine(orientation = SplitOrientation.VERTICAL)

            // Right child
            Box(modifier = Modifier.weight(1f - split.ratio)) {
                RenderSplitNode(
                    node = split.right,
                    splitState = splitState,
                    sharedFont = sharedFont,
                    isActiveTab = isActiveTab,
                    onTabTitleChange = onTabTitleChange,
                    onNewTab = onNewTab,
                    onCloseTab = onCloseTab,
                    onNextTab = onNextTab,
                    onPreviousTab = onPreviousTab,
                    onSwitchToTab = onSwitchToTab,
                    onNewWindow = onNewWindow,
                    onShowSettings = onShowSettings,
                    onSplitHorizontal = onSplitHorizontal,
                    onSplitVertical = onSplitVertical,
                    onClosePane = onClosePane,
                    onNavigatePane = onNavigatePane,
                    onNavigateNextPane = onNavigateNextPane,
                    onNavigatePreviousPane = onNavigatePreviousPane,
                    onMoveToNewTab = onMoveToNewTab,
                    menuActions = menuActions,
                    splitFocusBorderEnabled = splitFocusBorderEnabled,
                    splitFocusBorderColor = splitFocusBorderColor,
                    splitMinimumSize = splitMinimumSize,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Invisible drag handle overlay (16dp wide, centered on divider)
        SplitDragHandle(
            orientation = SplitOrientation.VERTICAL,
            currentRatio = split.ratio,
            availableSize = availableWidth,
            minRatio = splitMinimumSize,
            onRatioChange = { newRatio ->
                splitState.updateSplitRatio(split.id, newRatio)
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(leftWidth.toInt() - with(density) { 8.dp.toPx() }.toInt(), 0) }
        )
    }
}

/**
 * Render a horizontal split (top/bottom).
 */
@Composable
private fun RenderHorizontalSplit(
    split: SplitNode.HorizontalSplit,
    splitState: SplitViewState,
    sharedFont: FontFamily,
    isActiveTab: Boolean,
    onTabTitleChange: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: () -> Unit,
    onNextTab: () -> Unit,
    onPreviousTab: () -> Unit,
    onSwitchToTab: (Int) -> Unit,
    onNewWindow: () -> Unit,
    onShowSettings: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClosePane: () -> Unit,
    onNavigatePane: (NavigationDirection) -> Unit,
    onNavigateNextPane: () -> Unit,
    onNavigatePreviousPane: () -> Unit,
    onMoveToNewTab: (() -> Unit)?,
    menuActions: MenuActions?,
    splitFocusBorderEnabled: Boolean,
    splitFocusBorderColor: Color,
    splitMinimumSize: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val totalHeight = constraints.maxHeight.toFloat()
        val dividerHeightPx = with(density) { DIVIDER_THICKNESS.toPx() }
        val availableHeight = (totalHeight - dividerHeightPx).coerceAtLeast(1f)

        // Calculate top pane height in pixels for drag handle positioning
        val topHeight = availableHeight * split.ratio

        Column(modifier = Modifier.fillMaxSize()) {
            // Top child
            Box(modifier = Modifier.weight(split.ratio)) {
                RenderSplitNode(
                    node = split.top,
                    splitState = splitState,
                    sharedFont = sharedFont,
                    isActiveTab = isActiveTab,
                    onTabTitleChange = onTabTitleChange,
                    onNewTab = onNewTab,
                    onCloseTab = onCloseTab,
                    onNextTab = onNextTab,
                    onPreviousTab = onPreviousTab,
                    onSwitchToTab = onSwitchToTab,
                    onNewWindow = onNewWindow,
                    onShowSettings = onShowSettings,
                    onSplitHorizontal = onSplitHorizontal,
                    onSplitVertical = onSplitVertical,
                    onClosePane = onClosePane,
                    onNavigatePane = onNavigatePane,
                    onNavigateNextPane = onNavigateNextPane,
                    onNavigatePreviousPane = onNavigatePreviousPane,
                    onMoveToNewTab = onMoveToNewTab,
                    menuActions = menuActions,
                    splitFocusBorderEnabled = splitFocusBorderEnabled,
                    splitFocusBorderColor = splitFocusBorderColor,
                    splitMinimumSize = splitMinimumSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Thin visible divider line (1dp)
            SplitDividerLine(orientation = SplitOrientation.HORIZONTAL)

            // Bottom child
            Box(modifier = Modifier.weight(1f - split.ratio)) {
                RenderSplitNode(
                    node = split.bottom,
                    splitState = splitState,
                    sharedFont = sharedFont,
                    isActiveTab = isActiveTab,
                    onTabTitleChange = onTabTitleChange,
                    onNewTab = onNewTab,
                    onCloseTab = onCloseTab,
                    onNextTab = onNextTab,
                    onPreviousTab = onPreviousTab,
                    onSwitchToTab = onSwitchToTab,
                    onNewWindow = onNewWindow,
                    onShowSettings = onShowSettings,
                    onSplitHorizontal = onSplitHorizontal,
                    onSplitVertical = onSplitVertical,
                    onClosePane = onClosePane,
                    onNavigatePane = onNavigatePane,
                    onNavigateNextPane = onNavigateNextPane,
                    onNavigatePreviousPane = onNavigatePreviousPane,
                    onMoveToNewTab = onMoveToNewTab,
                    menuActions = menuActions,
                    splitFocusBorderEnabled = splitFocusBorderEnabled,
                    splitFocusBorderColor = splitFocusBorderColor,
                    splitMinimumSize = splitMinimumSize,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Invisible drag handle overlay (16dp tall, centered on divider)
        SplitDragHandle(
            orientation = SplitOrientation.HORIZONTAL,
            currentRatio = split.ratio,
            availableSize = availableHeight,
            minRatio = splitMinimumSize,
            onRatioChange = { newRatio ->
                splitState.updateSplitRatio(split.id, newRatio)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, topHeight.toInt() - with(density) { 8.dp.toPx() }.toInt()) }
        )
    }
}
