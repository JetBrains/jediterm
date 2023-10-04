package com.jediterm.ui.debug

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

class TeseqVisualizer {

  fun apply(chunks: List<String>): List<String> {
    return chunks.map { apply(it) }
  }

  private fun apply(text: String): String {
    return try {
      val file = writeTextToTempFile(text)
      readOutput(listOf("teseq", file.absolutePath))
    }
    catch (e: IOException) {
      """
   (!) Control sequence visualizer `teseq` is not installed (http://www.gnu.org/software/teseq/).
   Printing characters as is:

   """.trimIndent() + text
    }
  }

  private fun createTempFile(): File {
    return File.createTempFile("jediterm-data", ".txt").also {
      it.deleteOnExit()
    }
  }

  @Throws(IOException::class)
  private fun writeTextToTempFile(text: String): File {
    val file = createTempFile()
    file.writeText(text, Charsets.UTF_8)
    return file
  }

  @Throws(IOException::class)
  private fun readOutput(command: List<String>): String {
    val process = ProcessBuilder(command).start()
    try {
      process.waitFor()
    }
    catch (e: InterruptedException) {
      throw IOException(e)
    }
    val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
    return reader.use {
      it.readText()
    }
  }
}