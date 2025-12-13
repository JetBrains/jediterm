package ai.rever.bossterm.terminal.model

import ai.rever.bossterm.core.util.CellPosition

class TerminalResizeResult(
    val newCursor: CellPosition,
    val newSavedCursor: CellPosition? = null
)
