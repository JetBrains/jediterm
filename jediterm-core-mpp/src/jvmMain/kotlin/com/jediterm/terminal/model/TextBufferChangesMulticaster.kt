package com.jediterm.terminal.model

import java.util.concurrent.CopyOnWriteArrayList

internal class TextBufferChangesMulticaster : TextBufferChangesListener {
  private var listeners: MutableList<TextBufferChangesListener> = CopyOnWriteArrayList()

  fun addListener(listener: TextBufferChangesListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: TextBufferChangesListener) {
    listeners.remove(listener)
  }

  override fun linesChanged(fromIndex: Int) {
    forEachListeners {
      it.linesChanged(fromIndex)
    }
  }

  override fun linesDiscardedFromHistory(lines: List<TerminalLine>) {
    forEachListeners {
      it.linesDiscardedFromHistory(lines)
    }
  }

  override fun historyCleared() {
    forEachListeners {
      it.historyCleared()
    }
  }

  override fun widthResized() {
    forEachListeners {
      it.widthResized()
    }
  }

  private inline fun forEachListeners(action: (TextBufferChangesListener) -> Unit) {
    for (listener in listeners) {
      action(listener)
    }
  }
}
