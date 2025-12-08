package ai.rever.bossterm.compose.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import ai.rever.bossterm.terminal.model.image.DimensionSpec
import ai.rever.bossterm.terminal.model.image.ImageDimensionCalculator
import ai.rever.bossterm.terminal.model.image.TerminalImage
import ai.rever.bossterm.terminal.model.image.TerminalImagePlacement
import org.jetbrains.skia.Image
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Image renderer for terminal inline images.
 *
 * Handles:
 * - Converting byte arrays to Compose ImageBitmap (with caching)
 * - Calculating display dimensions based on terminal metrics
 * - Rendering images at correct positions in the terminal canvas
 */
object ImageRenderer {
    private val LOG = LoggerFactory.getLogger(ImageRenderer::class.java)

    /**
     * Cache for decoded images. Maps image ID to decoded ImageBitmap.
     * Uses weak references implicitly through the LRU eviction.
     */
    private val imageCache = ConcurrentHashMap<Long, ImageBitmap>()
    private const val MAX_CACHE_SIZE = 100

    /**
     * Get or decode an image from the cache.
     */
    fun getOrDecodeImage(image: TerminalImage): ImageBitmap? {
        return imageCache.getOrPut(image.id) {
            try {
                val skiaImage = Image.makeFromEncoded(image.data)
                skiaImage.toComposeImageBitmap()
            } catch (e: Exception) {
                LOG.warn("Failed to decode image {}: {}", image.id, e.message)
                return null
            }
        }.also {
            // Simple LRU: remove oldest if over capacity
            if (imageCache.size > MAX_CACHE_SIZE) {
                val oldest = imageCache.keys.firstOrNull()
                if (oldest != null) {
                    imageCache.remove(oldest)
                }
            }
        }
    }

    /**
     * Clear cached image by ID (when image is removed from terminal).
     */
    fun clearCachedImage(imageId: Long) {
        imageCache.remove(imageId)
    }

    /**
     * Clear all cached images.
     */
    fun clearCache() {
        imageCache.clear()
    }

    /**
     * Render all images in the visible area.
     *
     * @param placements List of image placements to render
     * @param cellWidth Width of a terminal cell in pixels
     * @param cellHeight Height of a terminal cell in pixels
     * @param scrollOffset Current scroll offset (positive = scrolled back into history)
     * @param visibleRows Number of visible rows
     * @param terminalWidthCells Terminal width in cells
     * @param terminalHeightCells Terminal height in cells
     */
    fun DrawScope.renderImages(
        placements: List<TerminalImagePlacement>,
        cellWidth: Float,
        cellHeight: Float,
        scrollOffset: Int,
        visibleRows: Int,
        terminalWidthCells: Int,
        terminalHeightCells: Int
    ) {
        for (placement in placements) {
            val bitmap = getOrDecodeImage(placement.image) ?: continue

            // Recalculate dimensions with actual cell metrics
            val dimensions = ImageDimensionCalculator.calculate(
                image = placement.image,
                terminalWidthCells = terminalWidthCells,
                terminalHeightCells = terminalHeightCells,
                cellWidthPx = cellWidth,
                cellHeightPx = cellHeight
            )

            // Calculate screen position
            // anchorRow is buffer-relative (negative = history, 0+ = screen)
            // We need to convert to visible row based on scroll offset
            val screenRow = placement.anchorRow + scrollOffset
            val screenCol = placement.anchorCol

            // Skip if completely outside visible area
            if (screenRow + dimensions.cellHeight < 0 || screenRow >= visibleRows) {
                continue
            }

            // Calculate pixel position
            val x = screenCol * cellWidth
            val y = screenRow * cellHeight

            // Calculate render size
            val renderWidth = dimensions.pixelWidth.toFloat()
            val renderHeight = dimensions.pixelHeight.toFloat()

            // Draw the image
            try {
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(renderWidth.toInt(), renderHeight.toInt())
                )
            } catch (e: Exception) {
                LOG.debug("Failed to render image {}: {}", placement.image.id, e.message)
            }
        }
    }

    /**
     * Get visible image placements for the current scroll position.
     * Filters placements to only those overlapping the visible area.
     *
     * @param allPlacements All image placements from the terminal
     * @param scrollOffset Current scroll offset
     * @param visibleRows Number of visible rows
     * @return Filtered list of placements visible in current viewport
     */
    fun getVisiblePlacements(
        allPlacements: List<TerminalImagePlacement>,
        scrollOffset: Int,
        visibleRows: Int
    ): List<TerminalImagePlacement> {
        // Convert scroll offset to buffer rows
        // scrollOffset=0 means showing screen buffer (row 0+)
        // scrollOffset>0 means scrolled into history (showing row -scrollOffset)
        val visibleStartRow = -scrollOffset
        val visibleEndRow = visibleStartRow + visibleRows

        return allPlacements.filter { placement ->
            placement.overlapsRows(visibleStartRow, visibleEndRow)
        }
    }
}
