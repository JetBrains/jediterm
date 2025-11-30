package org.jetbrains.jediterm.compose.debug

import org.jetbrains.jediterm.compose.tabs.TerminalTab
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collects and stores terminal I/O data and state snapshots for debugging purposes.
 *
 * This class maintains a circular buffer of data chunks and periodic snapshots of
 * terminal state (screen content, cursor position, styles, etc.) for time-travel
 * debugging and analysis.
 *
 * Thread-safety: All public methods are thread-safe and can be called from any coroutine.
 *
 * @property tab The terminal tab to collect data from (mutable to handle initialization order)
 * @property maxChunks Maximum number of chunks to store (circular buffer)
 * @property maxSnapshots Maximum number of snapshots to store (circular buffer)
 */
class DebugDataCollector(
    private var tab: TerminalTab?,
    private val maxChunks: Int = 1000,
    private val maxSnapshots: Int = 100
) {
    private val chunks = ConcurrentLinkedQueue<DebugChunk>()
    private val snapshots = ConcurrentLinkedQueue<TerminalSnapshot>()
    private val chunkIndex = AtomicInteger(0)

    @Volatile
    private var enabled = true

    /**
     * Set the terminal tab reference (for handling circular dependencies during initialization).
     */
    fun setTab(terminalTab: TerminalTab) {
        this.tab = terminalTab
    }

    /**
     * Record a chunk of data from the terminal stream.
     *
     * @param data The raw character data
     * @param source Where this data came from (PTY, user input, emulator)
     */
    fun recordChunk(data: String, source: ChunkSource) {
        if (!enabled || data.isEmpty()) return

        val chunk = DebugChunk(
            index = chunkIndex.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            data = data.toCharArray(),
            source = source
        )

        chunks.offer(chunk)

        // Trim to maxChunks (circular buffer)
        while (chunks.size > maxChunks) {
            chunks.poll()
        }
    }

    /**
     * Capture a snapshot of the current terminal state.
     *
     * Uses snapshot-based reading to minimize lock contention (reduces lock hold from 5-10ms to <1ms).
     * This allows PTY writers to continue during debug snapshot capture, preventing UI freezes.
     * Should be called periodically (e.g., every 100ms) rather than on every chunk.
     */
    fun captureState() {
        if (!enabled) return

        val currentTab = tab ?: return  // Skip if tab not set yet

        try {
            val textBuffer = currentTab.textBuffer
            val terminal = currentTab.terminal

            // Create immutable snapshot (fast, <1ms with lock, then lock released)
            val bufferSnapshot = textBuffer.createSnapshot()

            // Process snapshot without holding lock (5-10ms processing, no writer blocking)
            val screenLines = buildString {
                for (row in 0 until bufferSnapshot.height) {
                    val line = bufferSnapshot.getLine(row)
                    appendLine(line.text)
                }
            }

            val styleLines = buildString {
                for (row in 0 until bufferSnapshot.height) {
                    val line = bufferSnapshot.getLine(row)
                    appendLine(extractStyleLine(line))
                }
            }

            val historyLines = buildString {
                // Get history lines (negative indices)
                // Limit to available history to avoid requesting non-existent lines
                val historyStart = -minOf(100, bufferSnapshot.historyLinesCount)
                for (row in historyStart until 0) {
                    val line = bufferSnapshot.getLine(row)
                    appendLine(line.text)
                }
            }

            val snapshot = TerminalSnapshot(
                chunkIndex = chunkIndex.get(),
                timestamp = System.currentTimeMillis(),
                screenLines = screenLines,
                styleLines = styleLines,
                historyLines = historyLines,
                cursorX = terminal.cursorX - 1,  // JediTerm uses 1-based indexing
                cursorY = terminal.cursorY - 1,
                styleState = terminal.styleState.current.toString(),
                alternateBufferActive = bufferSnapshot.isUsingAlternateBuffer
            )

            snapshots.offer(snapshot)

            // Trim to maxSnapshots (circular buffer)
            while (snapshots.size > maxSnapshots) {
                snapshots.poll()
            }

        } catch (e: Exception) {
            println("WARN: Failed to capture terminal state: ${e.message}")
        }
    }

    /**
     * Extract style attributes from a terminal line as a human-readable string.
     *
     * Format: "[FG:WHITE BG:BLACK]H[FG:GREEN]e[BOLD]l[BOLD]l[BOLD]o"
     */
    private fun extractStyleLine(line: Any): String {
        // This is a simplified version - the actual implementation would need
        // to inspect TextEntry objects and their styles
        // For now, return a placeholder
        return buildString {
            append("[")
            // Would iterate through line entries and extract style info
            append("FG:DEFAULT BG:DEFAULT")
            append("]")
        }
    }

    /**
     * Get all currently stored chunks.
     *
     * @return Immutable list of chunks in chronological order
     */
    fun getChunks(): List<DebugChunk> {
        return chunks.toList()
    }

    /**
     * Get all currently stored snapshots.
     *
     * @return Immutable list of snapshots in chronological order
     */
    fun getSnapshots(): List<TerminalSnapshot> {
        return snapshots.toList()
    }

    /**
     * Get chunks within a specific index range.
     *
     * @param startIndex Start index (inclusive)
     * @param endIndex End index (inclusive)
     * @return List of chunks in the specified range
     */
    fun getChunksInRange(startIndex: Int, endIndex: Int): List<DebugChunk> {
        return chunks.filter { it.index in startIndex..endIndex }
    }

    /**
     * Get the snapshot closest to a specific chunk index.
     *
     * @param chunkIndex Target chunk index
     * @return The snapshot with the closest chunk index, or null if no snapshots exist
     */
    fun getSnapshotNearIndex(chunkIndex: Int): TerminalSnapshot? {
        val snapshotList = snapshots.toList()
        if (snapshotList.isEmpty()) return null

        return snapshotList.minByOrNull {
            kotlin.math.abs(it.chunkIndex - chunkIndex)
        }
    }

    /**
     * Get the current chunk index (total chunks recorded, including evicted ones).
     */
    fun getCurrentChunkIndex(): Int {
        return chunkIndex.get()
    }

    /**
     * Get the number of chunks currently stored.
     */
    fun getChunkCount(): Int {
        return chunks.size
    }

    /**
     * Get the number of snapshots currently stored.
     */
    fun getSnapshotCount(): Int {
        return snapshots.size
    }

    /**
     * Clear all collected data (chunks and snapshots).
     */
    fun clear() {
        chunks.clear()
        snapshots.clear()
        chunkIndex.set(0)
    }

    /**
     * Enable or disable data collection.
     *
     * When disabled, recordChunk() and captureState() become no-ops.
     * This can be used to pause collection without clearing existing data.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Check if data collection is currently enabled.
     */
    fun isEnabled(): Boolean {
        return enabled
    }

    /**
     * Get statistics about collected data.
     */
    fun getStats(): DebugStats {
        val chunkList = chunks.toList()
        val snapshotList = snapshots.toList()

        val earliestChunkIndex = chunkList.firstOrNull()?.index
        val latestChunkIndex = chunkList.lastOrNull()?.index
        val totalDataSize = chunkList.sumOf { it.data.size }

        return DebugStats(
            totalChunksRecorded = chunkIndex.get(),
            chunksStored = chunks.size,
            snapshotsStored = snapshots.size,
            earliestChunkIndex = earliestChunkIndex,
            latestChunkIndex = latestChunkIndex,
            totalDataBytes = totalDataSize,
            memoryUsageEstimate = estimateMemoryUsage()
        )
    }

    /**
     * Estimate memory usage in bytes (approximate).
     */
    private fun estimateMemoryUsage(): Long {
        val chunkOverhead = 64L  // Object overhead + fields
        val snapshotOverhead = 128L  // Object overhead + fields

        var total = 0L

        // Chunks
        chunks.forEach { chunk ->
            total += chunkOverhead + (chunk.data.size * 2)  // 2 bytes per char
        }

        // Snapshots (rough estimate based on string lengths)
        snapshots.forEach { snapshot ->
            total += snapshotOverhead +
                    (snapshot.screenLines.length * 2) +
                    (snapshot.styleLines.length * 2) +
                    (snapshot.historyLines.length * 2) +
                    (snapshot.styleState.length * 2)
        }

        return total
    }
}

/**
 * Statistics about collected debug data.
 */
data class DebugStats(
    val totalChunksRecorded: Int,
    val chunksStored: Int,
    val snapshotsStored: Int,
    val earliestChunkIndex: Int?,
    val latestChunkIndex: Int?,
    val totalDataBytes: Int,
    val memoryUsageEstimate: Long
) {
    fun toDisplayString(): String {
        val memoryMB = memoryUsageEstimate / 1024.0 / 1024.0
        return buildString {
            appendLine("Total chunks recorded: $totalChunksRecorded")
            appendLine("Chunks stored: $chunksStored")
            appendLine("Snapshots stored: $snapshotsStored")
            if (earliestChunkIndex != null && latestChunkIndex != null) {
                appendLine("Index range: $earliestChunkIndex - $latestChunkIndex")
            }
            appendLine("Data bytes: $totalDataBytes")
            appendLine("Estimated memory: %.2f MB".format(memoryMB))
        }
    }
}
