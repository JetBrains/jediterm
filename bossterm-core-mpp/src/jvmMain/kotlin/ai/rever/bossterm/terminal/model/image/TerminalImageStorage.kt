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
    private val placements = CopyOnWriteArrayList<TerminalImagePlacement>()
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

        images[image.id] = image
        placements.add(placement)
        totalBytes += image.data.size

        LOG.debug(
            "Added image id={}, row={}, col={}, cells={}x{}, pixels={}x{}",
            image.id, row, col, dimensions.cellWidth, dimensions.cellHeight,
            dimensions.pixelWidth, dimensions.pixelHeight
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
        placements.removeIf { it.image.id == imageId }
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
        placements.clear()
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

        // Update placements list
        placements.clear()
        placements.addAll(updatedPlacements)

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
     */
    fun getAllPlacements(): List<TerminalImagePlacement> {
        return placements.toList()
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

        placements.clear()
        placements.addAll(updatedPlacements)

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
