package org.jetbrains.jediterm.compose.selection

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.jediterm.compose.SelectionRenderer
import org.jetbrains.jediterm.compose.TerminalRenderer

class SelectionManager {
    enum class SelectionMode { CHARACTER, WORD, LINE, BLOCK }

    private val _hasSelection = MutableStateFlow(false)
    val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()

    private val _selection = MutableStateFlow<SelectionRenderer.Selection?>(null)
    val selection: StateFlow<SelectionRenderer.Selection?> = _selection.asStateFlow()

    private var selectionMode = SelectionMode.CHARACTER
    private var selectionStart: Pair<Int, Int>? = null
    private var selectionEnd: Pair<Int, Int>? = null

    fun startSelection(col: Int, row: Int, mode: SelectionMode = SelectionMode.CHARACTER) {
        selectionMode = mode
        selectionStart = Pair(col, row)
        selectionEnd = Pair(col, row)
        updateSelection()
    }

    fun updateSelection(col: Int, row: Int) {
        if (selectionStart == null) return
        selectionEnd = Pair(col, row)
        updateSelection()
    }

    fun endSelection() {
        if (selectionStart != null && selectionEnd != null) {
            _hasSelection.value = true
        }
    }

    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
        _selection.value = null
        _hasSelection.value = false
    }

    fun selectAll(columns: Int, rows: Int) {
        selectionStart = Pair(0, 0)
        selectionEnd = Pair(columns - 1, rows - 1)
        selectionMode = SelectionMode.CHARACTER
        updateSelection()
        _hasSelection.value = true
    }

    private fun updateSelection() {
        val start = selectionStart ?: return
        val end = selectionEnd ?: return

        val (startRow, startCol, endRow, endCol) = if (start.second < end.second ||
            (start.second == end.second && start.first <= end.first)) {
            Pair(start.second, start.first) to Pair(end.second, end.first)
        } else {
            Pair(end.second, end.first) to Pair(start.second, start.first)
        }.let { (s, e) -> listOf(s.first, s.second, e.first, e.second) }

        _selection.value = SelectionRenderer.Selection(
            startCol = startCol,
            startRow = startRow,
            endCol = endCol,
            endRow = endRow
        )
    }
}

class ComposeSelectionRenderer : SelectionRenderer {
    override fun renderSelection(
        selection: SelectionRenderer.Selection,
        selectionColor: Color,
        cellDimensions: TerminalRenderer.CellDimensions
    ) {
        // Selection rendering integrated into main Canvas
    }
}
