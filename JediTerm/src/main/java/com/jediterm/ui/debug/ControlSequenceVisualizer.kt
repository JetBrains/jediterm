package com.jediterm.ui.debug

import com.jediterm.terminal.util.CharUtils
import java.lang.IllegalStateException
import java.util.regex.Pattern

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
      chunks = chunks.map { CharUtils.toHumanReadableText(toHumanReadableSpace(it)) }
    }
    if (settings.showChunkId) {
      chunks = withChunkId(logStart, chunks, originalChunks)
    }
    return chunks.joinToString("")
  }

  private fun toHumanReadableSpace(escSeq: String): String = makeCharHumanReadable(escSeq, ' ', "S")

  @Suppress("SameParameterValue")
  private fun makeCharHumanReadable(escSeq: String, ch: Char, presentable: String): String {
    val pattern = Pattern.compile(Pattern.quote(ch.toString()) + "+")
    val matcher = pattern.matcher(escSeq)
    val builder = StringBuilder()
    var lastInd = 0
    while (matcher.find()) {
      val startInd = matcher.start()
      val endInd = matcher.end()
      val spaces = escSeq.substring(startInd, endInd)
      if (spaces != ch.toString().repeat(endInd - startInd)) {
        throw IllegalStateException("Not spaces")
      }
      builder.append(escSeq.substring(lastInd, startInd))
      if (spaces.length > 1) {
        builder.append("<$presentable:").append(spaces.length).append(">")
      }
      else {
        builder.append("<$presentable>")
      }
      lastInd = endInd
    }
    return builder.toString()
  }

  private fun withChunkId(logStart: Int, chunks: List<String>, originalChunks: List<String>): List<String> {
    check(chunks.size == originalChunks.size)
    val result = ArrayList<String>()
    for ((id, chunk) in chunks.withIndex()) {
      val label = "--- #${id + 1 + logStart} (received ${originalChunks[id].length} chars) ---\n"
      result.add(if (id == 0) label else "\n$label")
      result.add(chunk)
    }
    return result
  }
}
