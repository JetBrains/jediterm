package ai.rever.bossterm.compose.debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Visualizer that uses the external GNU teseq tool for control sequence analysis.
 *
 * teseq is a command-line tool that produces human-readable descriptions of
 * terminal escape sequences. Install via: brew install teseq (macOS) or
 * apt-get install teseq (Debian/Ubuntu).
 *
 * @see <a href="https://www.gnu.org/software/teseq/">GNU teseq</a>
 */
object TeseqVisualizer {

    /**
     * Cache the availability check result (checked once per session).
     */
    private val teseqAvailable: Boolean by lazy {
        checkTeseqAvailable()
    }

    /**
     * Check if the teseq command is available on this system.
     *
     * @return true if teseq is installed and executable
     */
    fun isAvailable(): Boolean = teseqAvailable

    /**
     * Visualize terminal data using the teseq command.
     *
     * @param chunks List of debug chunks to visualize
     * @return Human-readable teseq output, or error message if teseq fails
     */
    suspend fun visualize(chunks: List<DebugChunk>): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "teseq is not available. Install via: brew install teseq (macOS) or apt-get install teseq (Linux)"
        }

        if (chunks.isEmpty()) {
            return@withContext "No data captured"
        }

        // Combine all chunk data into a single byte array
        val combinedData = StringBuilder()
        for (chunk in chunks) {
            combinedData.append(chunk.data)
        }

        visualizeString(combinedData.toString())
    }

    /**
     * Visualize a raw string using the teseq command.
     *
     * @param data The string data to visualize
     * @return Human-readable teseq output
     */
    suspend fun visualizeString(data: String): String = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext "teseq is not available"
        }

        var tempFile: File? = null
        try {
            // Write data to temp file (teseq reads from file)
            tempFile = File.createTempFile("bossterm_debug_", ".bin")
            tempFile.writeText(data, Charsets.UTF_8)

            // Run teseq on the temp file
            val process = ProcessBuilder("teseq", tempFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            // Wait for completion with timeout
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext "teseq timed out after 10 seconds"
            }

            val output = process.inputStream.bufferedReader().readText()

            if (process.exitValue() != 0) {
                return@withContext "teseq error (exit ${process.exitValue()}): $output"
            }

            output.ifEmpty { "(empty output)" }
        } catch (e: Exception) {
            "teseq error: ${e.message}"
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Check if teseq command exists and is executable.
     */
    private fun checkTeseqAvailable(): Boolean {
        return try {
            val whichCommand = if (System.getProperty("os.name").lowercase().contains("win")) {
                ProcessBuilder("where", "teseq")
            } else {
                ProcessBuilder("which", "teseq")
            }

            val process = whichCommand
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
