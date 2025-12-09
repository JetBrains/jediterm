package ai.rever.bossterm.terminal.model.image

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Storage manager for terminal images.
 *
 * Maintains a collection of images and their placements, handling:
 * - Image addition at cursor position
 * - Image removal when scrolled out of buffer
 * - Image position updates when buffer scrolls
 * - Memory management (limiting total images/memory)
 */
class TerminalImageStorage(
    private val maxImages: Int = 100,
    private val maxTotalBytes: Long = 50 * 1024 * 1024 // 50MB default
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TerminalImageStorage::class.java)
    }

    private val images = ConcurrentHashMap<Long, TerminalImage>()

    // Copy-on-Write pattern: Atomic reference swap for lock-free reads during rendering
    // All updates create a new list and atomically swap the reference
    @Volatile
    private var placements: List<TerminalImagePlacement> = emptyList()

    private val listeners = CopyOnWriteArrayList<TerminalImageListener>()

    private var totalBytes: Long = 0

    /**
     * Add a new image at the specified position.
     *
     * @param image The image to add
     * @param row The anchor row (buffer-relative, can be negative for history)
     * @param col The anchor column
     * @param cellWidthPx Cell width in pixels for dimension calculation
     * @param cellHeightPx Cell height in pixels for dimension calculation
     * @param terminalWidthCells Terminal width in cells
     * @param terminalHeightCells Terminal height in cells
     * @return The created placement, or null if image couldn't be added
     */
    fun addImage(
        image: TerminalImage,
        row: Int,
        col: Int,
        cellWidthPx: Float,
        cellHeightPx: Float,
        terminalWidthCells: Int,
        terminalHeightCells: Int
    ): TerminalImagePlacement? {
        // Check memory limits
        if (!ensureCapacity(image.data.size.toLong())) {
            LOG.warn("Cannot add image: memory limit exceeded")
            return null
        }

        // Calculate dimensions
        val dimensions = ImageDimensionCalculator.calculate(
            image = image,
            terminalWidthCells = terminalWidthCells,
            terminalHeightCells = terminalHeightCells,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx
        )

        val placement = TerminalImagePlacement(
            image = image,
            anchorRow = row,
            anchorCol = col,
            cellWidth = dimensions.cellWidth,
            cellHeight = dimensions.cellHeight,
            pixelWidth = dimensions.pixelWidth,
            pixelHeight = dimensions.pixelHeight
        )

        val placementsBefore = placements.size
        images[image.id] = image
        placements = placements + placement  // COW: atomic reference swap
        totalBytes += image.data.size

        LOG.debug(
            "Added image id={}, row={}, col={}, cells={}x{}, pixels={}x{}, placements before={} after={}",
            image.id, row, col, dimensions.cellWidth, dimensions.cellHeight,
            dimensions.pixelWidth, dimensions.pixelHeight, placementsBefore, placements.size
        )

        // Notify listeners
        for (listener in listeners) {
            listener.onImageAdded(placement)
        }

        return placement
    }

    /**
     * Remove an image by ID.
     */
    fun removeImage(imageId: Long): Boolean {
        val image = images.remove(imageId) ?: return false
        placements = placements.filter { it.image.id != imageId }  // COW: atomic reference swap
        totalBytes -= image.data.size

        LOG.debug("Removed image id={}", imageId)

        for (listener in listeners) {
            listener.onImageRemoved(imageId)
        }

        return true
    }

    /**
     * Clear all images.
     */
    fun clearAll() {
        images.clear()
        placements = emptyList()  // COW: atomic reference swap
        totalBytes = 0

        LOG.debug("Cleared all images")

        for (listener in listeners) {
            listener.onAllImagesCleared()
        }
    }

    /**
     * Update image positions when the buffer scrolls.
     * Images are removed when they scroll completely out of history.
     *
     * @param scrollDelta How many rows the buffer scrolled (positive = up)
     * @param minRow Minimum row to keep (images above this are removed)
     */
    fun onBufferScroll(scrollDelta: Int, minRow: Int) {
        if (scrollDelta == 0) return

        val toRemove = mutableListOf<Long>()

        // Update placements and find images to remove
        val updatedPlacements = placements.mapNotNull { placement ->
            val newRow = placement.anchorRow - scrollDelta
            if (placement.isAboveRow(minRow - scrollDelta)) {
                toRemove.add(placement.image.id)
                null
            } else {
                placement.copy(anchorRow = newRow)
            }
        }

        // Remove images that scrolled out
        for (imageId in toRemove) {
            images.remove(imageId)?.let { totalBytes -= it.data.size }
        }

        // COW: atomic reference swap - no lock needed
        placements = updatedPlacements

        // Notify listeners
        for (imageId in toRemove) {
            for (listener in listeners) {
                listener.onImageRemoved(imageId)
            }
        }
    }

    /**
     * Get all placements visible in the given row range.
     */
    fun getPlacementsInRange(startRow: Int, endRow: Int): List<TerminalImagePlacement> {
        return placements.filter { it.overlapsRows(startRow, endRow) }
    }

    /**
     * Get all current placements.
     * Returns the current snapshot - immutable due to COW pattern.
     */
    fun getAllPlacements(): List<TerminalImagePlacement> {
        val snapshot = placements
        LOG.debug("getAllPlacements: returning {} placements", snapshot.size)
        snapshot.forEachIndexed { index, p ->
            LOG.debug("  [{}] imageId={}, anchorRow={}, anchorCol={}, cellW={}, cellH={}",
                index, p.image.id, p.anchorRow, p.anchorCol, p.cellWidth, p.cellHeight)
        }
        return snapshot
    }

    /**
     * Get an image by ID.
     */
    fun getImage(imageId: Long): TerminalImage? = images[imageId]

    /**
     * Check if a cell is covered by any image.
     */
    fun getPlacementAt(row: Int, col: Int): TerminalImagePlacement? {
        return placements.find { it.containsCell(row, col) }
    }

    /**
     * Recalculate all placements after terminal resize.
     */
    fun onTerminalResize(
        cellWidthPx: Float,
        cellHeightPx: Float,
        terminalWidthCells: Int,
        terminalHeightCells: Int
    ) {
        val updatedPlacements = placements.map { placement ->
            val dimensions = ImageDimensionCalculator.calculate(
                image = placement.image,
                terminalWidthCells = terminalWidthCells,
                terminalHeightCells = terminalHeightCells,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx
            )
            placement.copy(
                cellWidth = dimensions.cellWidth,
                cellHeight = dimensions.cellHeight,
                pixelWidth = dimensions.pixelWidth,
                pixelHeight = dimensions.pixelHeight
            )
        }

        // COW: atomic reference swap - no lock needed
        placements = updatedPlacements

        for (listener in listeners) {
            listener.onImagesUpdated(updatedPlacements)
        }
    }

    /**
     * Adjust image anchor rows when lines are moved from screen to history.
     * Called during terminal height shrink.
     *
     * @param lineCount Number of lines moved to history
     */
    fun adjustAnchorsForLinesToHistory(lineCount: Int) {
        if (lineCount <= 0 || placements.isEmpty()) return

        LOG.debug("Adjusting image anchors for {} lines moved to history", lineCount)

        val updatedPlacements = placements.map { placement ->
            // Screen lines moved to history = all screen indices shift DOWN
            // anchorRow 10 with 5 lines moved → anchorRow 5
            placement.copy(anchorRow = placement.anchorRow - lineCount)
        }

        // COW: atomic reference swap - no lock needed
        placements = updatedPlacements
    }

    /**
     * Adjust image anchor rows when lines are restored from history to screen.
     * Called during terminal height expand.
     *
     * @param lineCount Number of lines restored from history
     */
    fun adjustAnchorsForLinesFromHistory(lineCount: Int) {
        if (lineCount <= 0 || placements.isEmpty()) return

        LOG.debug("Adjusting image anchors for {} lines restored from history", lineCount)

        val updatedPlacements = placements.map { placement ->
            // Lines added to screen TOP = all screen indices shift UP
            // anchorRow 5 with 3 lines added → anchorRow 8
            placement.copy(anchorRow = placement.anchorRow + lineCount)
        }

        // COW: atomic reference swap - no lock needed
        placements = updatedPlacements
    }

    /**
     * Get all current anchor rows for tracking through width reflow.
     * @return Set of screen-relative anchor rows (0-indexed)
     */
    fun getAnchorRows(): Set<Int> {
        return placements.map { it.anchorRow }.toSet()
    }

    /**
     * Reanchor images after width change using the provided mapping.
     * @param anchorMapping Map of old anchor row -> new anchor row
     */
    fun reanchorImages(anchorMapping: Map<Int, Int>) {
        if (anchorMapping.isEmpty() || placements.isEmpty()) return

        LOG.debug("Reanchoring images with mapping: {}", anchorMapping)

        val updatedPlacements = placements.map { placement ->
            val newRow = anchorMapping[placement.anchorRow]
            if (newRow != null) {
                LOG.debug("Reanchoring image from row {} to row {}", placement.anchorRow, newRow)
                placement.copy(anchorRow = newRow)
            } else {
                // Keep original position if not in mapping (shouldn't happen)
                LOG.warn("No mapping found for anchor row {}", placement.anchorRow)
                placement
            }
        }

        // COW: atomic reference swap - no lock needed
        placements = updatedPlacements

        for (listener in listeners) {
            listener.onImagesUpdated(updatedPlacements)
        }
    }

    fun addListener(listener: TerminalImageListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: TerminalImageListener) {
        listeners.remove(listener)
    }

    /**
     * Ensure there's capacity for a new image.
     * May evict old images if necessary.
     */
    private fun ensureCapacity(newBytes: Long): Boolean {
        // Evict oldest images if over memory limit
        while (totalBytes + newBytes > maxTotalBytes && placements.isNotEmpty()) {
            val oldest = placements.firstOrNull() ?: break
            removeImage(oldest.image.id)
        }

        // Evict if over count limit
        while (placements.size >= maxImages && placements.isNotEmpty()) {
            val oldest = placements.firstOrNull() ?: break
            removeImage(oldest.image.id)
        }

        return totalBytes + newBytes <= maxTotalBytes
    }

    val imageCount: Int get() = images.size
    val totalMemoryUsed: Long get() = totalBytes
}
