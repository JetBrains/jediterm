package com.jediterm.ui.debug

import com.jediterm.terminal.util.CharUtils

internal object ControlSequenceVisualizer {
  fun getVisualizedString(logStart: Int,
                          arrayChunks: List<CharArray>,
                          settings: ControlSequenceSettings): String {
    val originalChunks: List<String> = arrayChunks.map { String(it) }
    var chunks: List<String> = originalChunks
    if (settings.useTeseq) {
      chunks = TeseqVisualizer().apply(chunks)
    }
    if (settings.showInvisibleCharacters) {
      chunks = chunks.map { CharUtils.toHumanReadableText(it) }
    }
    if (settings.showChunkId) {
      chunks = withChunkId(logStart, chunks, originalChunks)
    }
    return chunks.joinToString("")
  }

  private fun withChunkId(logStart: Int, chunks: List<String>, originalChunks: List<String>): List<String> {
    check(chunks.size == originalChunks.size)
    val result = ArrayList<String>()
    for ((id, chunk) in chunks.withIndex()) {
      val label = "--- # ${id + 1 + logStart} of ${originalChunks[id].length} chars ---\n"
      result.add(if (id == 0) label else "\n$label")
      result.add(chunk)
    }
    return result
  }
}
