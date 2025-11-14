package org.jetbrains.jediterm.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jediterm.compose.api.TerminalControllerImpl
import org.jetbrains.jediterm.compose.api.TerminalStateImpl
import org.jetbrains.jediterm.compose.integration.rememberTerminalOrchestrator
import org.jetbrains.jediterm.compose.stubs.*

import org.jetbrains.jediterm.compose.selection.SelectionManager
import org.jetbrains.jediterm.compose.selection.SelectionOverlay
import org.jetbrains.jediterm.compose.selection.SelectionRenderer
import org.jetbrains.jediterm.core.TerminalCoordinates

/**
 * Desktop implementation of Terminal composable.
 * Integrates all components for full terminal functionality.
 */
@Composable
actual fun Terminal(
    state: TerminalState,
    controller: TerminalController,
    modifier: Modifier,
    onTitleChange: ((String?) -> Unit)?,
    onBell: (() -> Unit)?
) {
    // Setup callbacks
    if (controller is TerminalControllerImpl) {
        controller.setBellCallback(onBell)
    }

    // Create component instances
    val renderer = remember { StubTerminalRenderer() }
    val inputHandler = remember { StubInputHandler() }
    val mouseInputHandler = remember { StubMouseInputHandler() }
    val cursorRenderer = remember { StubCursorRenderer() }
    val selectionRenderer = remember(state) { SelectionRenderer(state) }
    val selectionManager = remember { SelectionManager() }

    // Initialize renderer with font configuration
    LaunchedEffect(Unit) {
        renderer.initialize(
            TerminalRenderer.FontConfig(
                family = FontFamily.Monospace,
                size = 14f,
                lineSpacing = 1.0f
            )
        )
    }

    // Create orchestrator
    val orchestrator = rememberTerminalOrchestrator(
        state = state,
        controller = controller,
        renderer = renderer,
        inputHandler = inputHandler,
        mouseInputHandler = mouseInputHandler,
        cursorRenderer = cursorRenderer,
        selectionRenderer = selectionRenderer
    )

    // Focus management
    val focusRequester = remember { FocusRequester() }

    // Monitor title changes
    LaunchedEffect(state.title) {
        state.title.collect { title ->
            onTitleChange?.invoke(title)
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(state.theme.value.defaultBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                (state as? TerminalStateImpl)?.setFocused(focusState.isFocused)
            }
            .onKeyEvent { keyEvent ->
                orchestrator.handleKeyEvent(keyEvent)
            }
            .onSizeChanged { size ->
                componentSize = size
                if (size.width > 0 && size.height > 0) {
                    val (cols, rows) = orchestrator.calculateGridDimensions(
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                    if (cols > 0 && rows > 0) {
                        state.setDimensions(cols, rows)
                    }
                }
            }
    ) {
        // Terminal rendering canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val (col, row) = orchestrator.pixelToGrid(
                                offset.x,
                                offset.y
                            )
                            selectionManager.startSelection(TerminalCoordinates(col, row))
                        },
                        onDrag = { change, _ ->
                            val (col, row) = orchestrator.pixelToGrid(
                                change.position.x,
                                change.position.y
                            )
                            selectionManager.extendSelection(TerminalCoordinates(col, row))
                        }
                    )
                }
        ) {
            orchestrator.renderFrame()
        }

        SelectionOverlay(
            selectionManager = selectionManager,
            terminalState = state,
            charSize = renderer.getCellDimensions().let { androidx.compose.ui.geometry.Size(it.width, it.height) },
            modifier = Modifier.fillMaxSize()
        )

        // Disposal
        DisposableEffect(orchestrator) {
            onDispose {
                orchestrator.dispose()
            }
        }
    }
}

/**
 * Simplified Terminal composable for Desktop.
 */
@Composable
actual fun Terminal(
    command: String,
    arguments: List<String>,
    environment: Map<String, String>,
    config: TerminalState.TerminalConfig,
    modifier: Modifier,
    onTitleChange: ((String?) -> Unit)?,
    onBell: (() -> Unit)?,
    onProcessExit: ((Int) -> Unit)?
) {
    val state = rememberTerminalState(config)
    val controller = rememberTerminalController(state)

    LaunchedEffect(command, arguments, environment) {
        controller.connect(command, arguments, environment)
    }

    Terminal(
        state = state,
        controller = controller,
        modifier = modifier,
        onTitleChange = onTitleChange,
        onBell = onBell
    )
}

/**
 * Remember terminal state for Desktop.
 */
@Composable
actual fun rememberTerminalState(config: TerminalState.TerminalConfig): TerminalState {
    return remember(config) {
        TerminalStateImpl(config)
    }
}

/**
 * Remember terminal controller for Desktop.
 */
@Composable
actual fun rememberTerminalController(state: TerminalState): TerminalController {
    return remember(state) {
        TerminalControllerImpl.create(state)
    }
}
