package ai.rever.bossterm.terminal.model.image

/**
 * Represents the placement of an image in the terminal buffer.
 *
 * Images are anchored to a specific row (in the scrollback/screen buffer)
 * and column, and span a calculated number of cells.
 *
 * @property image The image being placed
 * @property anchorRow The row where the image starts (can be negative for history)
 * @property anchorCol The column where the image starts (0-based)
 * @property cellWidth Number of character cells the image spans horizontally
 * @property cellHeight Number of character cells the image spans vertically
 * @property pixelWidth Actual render width in pixels
 * @property pixelHeight Actual render height in pixels
 */
data class TerminalImagePlacement(
    val image: TerminalImage,
    val anchorRow: Int,
    val anchorCol: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val pixelWidth: Int,
    val pixelHeight: Int
) {
    /**
     * Check if this placement overlaps with a given row range.
     */
    fun overlapsRows(startRow: Int, endRow: Int): Boolean {
        val placementEndRow = anchorRow + cellHeight - 1
        return anchorRow <= endRow && placementEndRow >= startRow
    }

    /**
     * Check if this placement is fully above the given row.
     */
    fun isAboveRow(row: Int): Boolean = anchorRow + cellHeight <= row

    /**
     * Check if this placement is fully below the given row.
     */
    fun isBelowRow(row: Int): Boolean = anchorRow > row

    /**
     * Check if a specific cell (row, col) is covered by this image.
     */
    fun containsCell(row: Int, col: Int): Boolean {
        return row >= anchorRow &&
               row < anchorRow + cellHeight &&
               col >= anchorCol &&
               col < anchorCol + cellWidth
    }

    /**
     * Get the row offset within this image for a given absolute row.
     * Returns null if the row is outside this placement.
     */
    fun getRowOffset(absoluteRow: Int): Int? {
        val offset = absoluteRow - anchorRow
        return if (offset in 0 until cellHeight) offset else null
    }
}

/**
 * Calculator for image dimensions based on terminal metrics.
 */
object ImageDimensionCalculator {
    /**
     * Calculate the final pixel dimensions and cell span for an image.
     *
     * @param image The image with dimension specs
     * @param terminalWidthCells Terminal width in cells
     * @param terminalHeightCells Terminal height in cells
     * @param cellWidthPx Width of a single cell in pixels
     * @param cellHeightPx Height of a single cell in pixels
     * @return Pair of (pixelWidth, pixelHeight) and (cellWidth, cellHeight)
     */
    fun calculate(
        image: TerminalImage,
        terminalWidthCells: Int,
        terminalHeightCells: Int,
        cellWidthPx: Float,
        cellHeightPx: Float
    ): ImageDimensions {
        val terminalWidthPx = terminalWidthCells * cellWidthPx
        val terminalHeightPx = terminalHeightCells * cellHeightPx

        // Calculate target width in pixels
        var targetWidthPx = when (val spec = image.widthSpec) {
            is DimensionSpec.Cells -> (spec.count * cellWidthPx).toInt()
            is DimensionSpec.Pixels -> spec.count
            is DimensionSpec.Percent -> (terminalWidthPx * spec.value / 100).toInt()
            is DimensionSpec.Auto -> image.intrinsicWidth
        }

        // Calculate target height in pixels
        var targetHeightPx = when (val spec = image.heightSpec) {
            is DimensionSpec.Cells -> (spec.count * cellHeightPx).toInt()
            is DimensionSpec.Pixels -> spec.count
            is DimensionSpec.Percent -> (terminalHeightPx * spec.value / 100).toInt()
            is DimensionSpec.Auto -> image.intrinsicHeight
        }

        // Preserve aspect ratio if requested
        if (image.preserveAspectRatio && image.intrinsicWidth > 0 && image.intrinsicHeight > 0) {
            val aspectRatio = image.intrinsicWidth.toFloat() / image.intrinsicHeight.toFloat()

            when {
                // Both auto - use intrinsic size but constrain to terminal width
                image.widthSpec is DimensionSpec.Auto && image.heightSpec is DimensionSpec.Auto -> {
                    // If wider than terminal, scale down to fit while preserving aspect ratio
                    if (targetWidthPx > terminalWidthPx) {
                        val scale = terminalWidthPx / targetWidthPx
                        targetWidthPx = terminalWidthPx.toInt()
                        targetHeightPx = (targetHeightPx * scale).toInt()
                    }
                }
                // Only width specified - calculate height
                image.heightSpec is DimensionSpec.Auto -> {
                    targetHeightPx = (targetWidthPx / aspectRatio).toInt()
                }
                // Only height specified - calculate width
                image.widthSpec is DimensionSpec.Auto -> {
                    targetWidthPx = (targetHeightPx * aspectRatio).toInt()
                }
                // Both specified - fit within bounds while preserving aspect ratio
                else -> {
                    val widthRatio = targetWidthPx.toFloat() / image.intrinsicWidth
                    val heightRatio = targetHeightPx.toFloat() / image.intrinsicHeight
                    val scale = minOf(widthRatio, heightRatio)
                    targetWidthPx = (image.intrinsicWidth * scale).toInt()
                    targetHeightPx = (image.intrinsicHeight * scale).toInt()
                }
            }
        }

        // Ensure minimum size of 1 pixel
        targetWidthPx = maxOf(1, targetWidthPx)
        targetHeightPx = maxOf(1, targetHeightPx)

        // Calculate cell span (ceiling to ensure image fits)
        val cellWidth = kotlin.math.ceil(targetWidthPx / cellWidthPx).toInt().coerceAtLeast(1)
        val cellHeight = kotlin.math.ceil(targetHeightPx / cellHeightPx).toInt().coerceAtLeast(1)

        return ImageDimensions(
            pixelWidth = targetWidthPx,
            pixelHeight = targetHeightPx,
            cellWidth = cellWidth,
            cellHeight = cellHeight
        )
    }
}

/**
 * Calculated image dimensions.
 */
data class ImageDimensions(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val cellWidth: Int,
    val cellHeight: Int
)
