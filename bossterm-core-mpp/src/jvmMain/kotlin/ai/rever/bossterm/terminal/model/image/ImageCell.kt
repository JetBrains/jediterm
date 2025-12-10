package ai.rever.bossterm.terminal.model.image

/**
 * Represents a single cell of an image.
 * Each cell knows which portion of the image it should render.
 * Cells are independent and reflow like text characters.
 *
 * When rendering, each cell draws its portion:
 *   srcX = cellX * cellPixelWidth
 *   srcY = cellY * cellPixelHeight
 */
data class ImageCell(
    val imageId: Long,
    val cellX: Int,       // Column index within image grid (0, 1, 2, ...)
    val cellY: Int,       // Row index within image grid (0, 1, 2, ...)
    val totalCellsX: Int, // Total columns the image spans
    val totalCellsY: Int  // Total rows the image spans
)
