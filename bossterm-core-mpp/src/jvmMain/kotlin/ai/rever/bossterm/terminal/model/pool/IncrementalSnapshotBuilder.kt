package ai.rever.bossterm.terminal.model.pool

import ai.rever.bossterm.terminal.model.LinesStorage
import ai.rever.bossterm.terminal.model.TerminalLine
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Incremental snapshot builder that minimizes allocations using copy-on-write semantics.
 *
 * **Core Optimization Strategy**:
 * 1. Track lines by object identity using IdentityHashMap (not by position)
 * 2. Compare current version to cached version for each line
 * 3. Reuse unchanged line copies (zero-copy reference sharing)
 * 4. Only copy changed lines
 * 5. Prune cache when lines are deleted from buffer
 *
 * **Why IdentityHashMap?**
 * CyclicBufferLinesStorage uses ArrayDeque internally, where indices shift during scroll.
 * A line at index 5 may move to index 4 after scrolling. By tracking object identity
 * instead of position, we can find cached copies regardless of index changes.
 *
 * **Expected Performance Impact**:
 * - 99%+ reduction in allocations for typical terminal use
 * - Most frames have 0-5 changed lines vs copying all 1000+ lines
 * - ~320KB memory overhead for identity cache (acceptable trade-off)
 *
 * **Thread Safety**:
 * - Builder instance is NOT thread-safe (use one per TerminalTextBuffer)
 * - Returned snapshots are immutable and thread-safe for reading
 * - Must be called while holding TerminalTextBuffer lock
 */
class IncrementalSnapshotBuilder(
    /**
     * Feature flag to enable incremental identity-based line reuse.
     *
     * When true (default): Uses IdentityHashMap to track and reuse unchanged lines (~1-10KB/frame)
     * When false: Uses full deep copy (reliable but ~430KB/frame)
     */
    private val enablePooling: Boolean = true
) {
    /**
     * Identity-based cache: maps original TerminalLine reference to its VersionedLine copy.
     * Uses IdentityHashMap for O(1) lookup by object identity (not equals()).
     *
     * This allows tracking lines across position changes in CyclicBufferLinesStorage.
     */
    private val lineCache = IdentityHashMap<TerminalLine, VersionedLine>()

    /**
     * Metadata for detecting structural changes that require cache clear.
     */
    private var previousWidth: Int = -1
    private var previousHeight: Int = -1
    private var previousAlternateBuffer: Boolean = false

    /**
     * Statistics for monitoring optimization effectiveness.
     */
    private val stats = SnapshotBuilderStats()

    /**
     * Create an incremental snapshot of the current buffer state.
     *
     * **MUST be called while holding TerminalTextBuffer lock.**
     *
     * @param screenLinesStorage Current screen lines
     * @param historyLinesStorage Current history lines
     * @param width Terminal width
     * @param height Terminal height
     * @param isUsingAlternateBuffer Whether alternate buffer is active
     * @return Immutable versioned snapshot for rendering
     */
    fun createSnapshot(
        screenLinesStorage: LinesStorage,
        historyLinesStorage: LinesStorage,
        width: Int,
        height: Int,
        isUsingAlternateBuffer: Boolean
    ): VersionedBufferSnapshot {
        stats.snapshotCount.incrementAndGet()

        if (!enablePooling) {
            return createFullSnapshot(
                screenLinesStorage, historyLinesStorage,
                width, height, isUsingAlternateBuffer
            )
        }

        // Force cache clear on structural changes
        if (width != previousWidth ||
            height != previousHeight ||
            isUsingAlternateBuffer != previousAlternateBuffer
        ) {
            stats.fullCopyCount.incrementAndGet()
            lineCache.clear()
        }

        // Build snapshot using identity-based lookup
        val screenLines = (0 until screenLinesStorage.size).map { i ->
            processLine(screenLinesStorage[i])
        }

        val historyLines = (0 until historyLinesStorage.size).map { i ->
            processLine(historyLinesStorage[i])
        }

        // Prune stale cache entries (lines no longer in either buffer)
        pruneCache(screenLinesStorage, historyLinesStorage)

        // Update metadata
        previousWidth = width
        previousHeight = height
        previousAlternateBuffer = isUsingAlternateBuffer

        return VersionedBufferSnapshot(
            screenLines = screenLines,
            historyLines = historyLines,
            width = width,
            height = height,
            historyLinesCount = historyLinesStorage.size,
            isUsingAlternateBuffer = isUsingAlternateBuffer
        )
    }

    /**
     * Process a single line: return cached copy if unchanged, or create new copy.
     * Uses object identity (not position) to find cached entries.
     */
    private fun processLine(originalLine: TerminalLine): VersionedLine {
        val currentVersion = originalLine.getSnapshotVersion()

        // Look up by IDENTITY (object reference), not position
        val cached = lineCache[originalLine]

        if (cached != null && cached.version == currentVersion) {
            // ZERO-COPY: line hasn't changed, reuse the cached copy
            stats.linesReused.incrementAndGet()
            return cached
        }

        // Line changed or new - create fresh copy
        val lineCopy = originalLine.copy()
        val newEntry = VersionedLine(lineCopy, currentVersion)

        // Update cache with new entry
        lineCache[originalLine] = newEntry
        stats.linesCopied.incrementAndGet()

        return newEntry
    }

    /**
     * Remove cache entries for lines that are no longer in any buffer.
     * This prevents memory leaks when lines are deleted.
     */
    private fun pruneCache(screenStorage: LinesStorage, historyStorage: LinesStorage) {
        // Build set of current line identities
        val currentLines: MutableSet<TerminalLine> = Collections.newSetFromMap(IdentityHashMap())

        for (i in 0 until screenStorage.size) {
            currentLines.add(screenStorage[i])
        }
        for (i in 0 until historyStorage.size) {
            currentLines.add(historyStorage[i])
        }

        // Remove entries for lines no longer in buffers
        lineCache.keys.retainAll(currentLines)
    }

    /**
     * Create a full deep-copy snapshot (fallback path).
     * Uses map{} to create immutable List.
     */
    private fun createFullSnapshot(
        screenLinesStorage: LinesStorage,
        historyLinesStorage: LinesStorage,
        width: Int,
        height: Int,
        isUsingAlternateBuffer: Boolean
    ): VersionedBufferSnapshot {
        // Use map{} to create immutable List
        val screenLines = (0 until screenLinesStorage.size).map { i ->
            val line = screenLinesStorage[i]
            VersionedLine(line.copy(), line.getSnapshotVersion())
        }

        val historyLines = (0 until historyLinesStorage.size).map { i ->
            val line = historyLinesStorage[i]
            VersionedLine(line.copy(), line.getSnapshotVersion())
        }

        stats.linesCopied.addAndGet((screenLines.size + historyLines.size).toLong())

        return VersionedBufferSnapshot(
            screenLines = screenLines,
            historyLines = historyLines,
            width = width,
            height = height,
            historyLinesCount = historyLinesStorage.size,
            isUsingAlternateBuffer = isUsingAlternateBuffer
        )
    }

    /**
     * Clear cached state. Call when buffer is cleared or reset.
     */
    fun clear() {
        lineCache.clear()
        previousWidth = -1
        previousHeight = -1
        previousAlternateBuffer = false
    }

    /**
     * Get current statistics.
     */
    fun getStats(): SnapshotBuilderStats = stats

    /**
     * Check if pooling is enabled.
     */
    fun isPoolingEnabled(): Boolean = enablePooling
}

/**
 * A terminal line with its version number at snapshot time.
 */
data class VersionedLine(
    val line: TerminalLine,
    val version: Long
)

/**
 * Enhanced snapshot with version tracking for incremental updates.
 */
class VersionedBufferSnapshot(
    val screenLines: List<VersionedLine>,
    val historyLines: List<VersionedLine>,
    val width: Int,
    val height: Int,
    val historyLinesCount: Int,
    val isUsingAlternateBuffer: Boolean
) {
    /**
     * Get line by index using same semantics as TerminalTextBuffer.getLine().
     * Negative indices access history buffer, non-negative access screen buffer.
     */
    fun getLine(index: Int): TerminalLine {
        return if (index >= 0) {
            screenLines.getOrNull(index)?.line ?: TerminalLine.createEmpty()
        } else {
            val historyIndex = historyLinesCount + index
            historyLines.getOrNull(historyIndex)?.line ?: TerminalLine.createEmpty()
        }
    }

    /**
     * Get version of line at index.
     */
    fun getLineVersion(index: Int): Long? {
        return if (index >= 0) {
            screenLines.getOrNull(index)?.version
        } else {
            val historyIndex = historyLinesCount + index
            historyLines.getOrNull(historyIndex)?.version
        }
    }
}

/**
 * Statistics for monitoring incremental snapshot builder performance.
 */
class SnapshotBuilderStats {
    val snapshotCount = AtomicLong(0)
    val fullCopyCount = AtomicLong(0)
    val linesReused = AtomicLong(0)
    val linesCopied = AtomicLong(0)

    val reuseRate: Double
        get() {
            val total = linesReused.get() + linesCopied.get()
            return if (total > 0) linesReused.get().toDouble() / total else 0.0
        }

    val incrementalRate: Double
        get() {
            val total = snapshotCount.get()
            return if (total > 0) (total - fullCopyCount.get()).toDouble() / total else 0.0
        }

    fun reset() {
        snapshotCount.set(0)
        fullCopyCount.set(0)
        linesReused.set(0)
        linesCopied.set(0)
    }

    override fun toString(): String {
        return buildString {
            appendLine("IncrementalSnapshotBuilder Stats:")
            appendLine("  Snapshots: ${snapshotCount.get()} (full: ${fullCopyCount.get()}, incremental: ${snapshotCount.get() - fullCopyCount.get()})")
            appendLine("  Incremental rate: ${String.format("%.2f", incrementalRate * 100)}%")
            appendLine("  Lines reused: ${linesReused.get()}, copied: ${linesCopied.get()}")
            appendLine("  Reuse rate: ${String.format("%.2f", reuseRate * 100)}%")
        }
    }
}
