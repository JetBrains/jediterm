package com.jediterm.terminal.model

interface TerminalSelectionChangesListener {
  fun selectionChanged(selection: TerminalSelection?)
}