package ai.rever.bossterm.terminal.model.image

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents an inline image in the terminal.
 *
 * Images are stored with their raw data and metadata from OSC 1337;File sequence.
 * The actual rendering dimensions are calculated based on the specification
 * (cells, pixels, percentage, or auto) and the current terminal metrics.
 *
 * @property id Unique identifier for this image
 * @property data Raw image data (decoded from base64)
 * @property name Optional filename (decoded from base64)
 * @property format Detected image format (PNG, JPEG, GIF, etc.)
 * @property intrinsicWidth Original image width in pixels
 * @property intrinsicHeight Original image height in pixels
 * @property widthSpec Width specification from OSC sequence (e.g., "80", "200px", "50%", "auto")
 * @property heightSpec Height specification from OSC sequence
 * @property preserveAspectRatio Whether to maintain aspect ratio when scaling
 */
data class TerminalImage(
    val id: Long = nextId(),
    val data: ByteArray,
    val name: String? = null,
    val format: ImageFormat = ImageFormat.UNKNOWN,
    val intrinsicWidth: Int = 0,
    val intrinsicHeight: Int = 0,
    val widthSpec: DimensionSpec = DimensionSpec.Auto,
    val heightSpec: DimensionSpec = DimensionSpec.Auto,
    val preserveAspectRatio: Boolean = true
) {
    companion object {
        private val idCounter = AtomicLong(0)
        private fun nextId(): Long = idCounter.incrementAndGet()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalImage) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Supported image formats for inline display.
 */
enum class ImageFormat {
    PNG,
    JPEG,
    GIF,
    BMP,
    WEBP,
    UNKNOWN;

    companion object {
        /**
         * Detect image format from magic bytes.
         */
        fun detect(data: ByteArray): ImageFormat {
            if (data.size < 8) return UNKNOWN

            return when {
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                data[0] == 0x89.toByte() &&
                data[1] == 0x50.toByte() &&
                data[2] == 0x4E.toByte() &&
                data[3] == 0x47.toByte() -> PNG

                // JPEG: FF D8 FF
                data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte() -> JPEG

                // GIF: 47 49 46 38
                data[0] == 0x47.toByte() &&
                data[1] == 0x49.toByte() &&
                data[2] == 0x46.toByte() &&
                data[3] == 0x38.toByte() -> GIF

                // BMP: 42 4D
                data[0] == 0x42.toByte() &&
                data[1] == 0x4D.toByte() -> BMP

                // WebP: 52 49 46 46 ... 57 45 42 50
                data[0] == 0x52.toByte() &&
                data[1] == 0x49.toByte() &&
                data[2] == 0x46.toByte() &&
                data[3] == 0x46.toByte() &&
                data.size >= 12 &&
                data[8] == 0x57.toByte() &&
                data[9] == 0x45.toByte() &&
                data[10] == 0x42.toByte() &&
                data[11] == 0x50.toByte() -> WEBP

                else -> UNKNOWN
            }
        }
    }
}

/**
 * Dimension specification for image width/height.
 * Supports: cells (default), pixels (px), percentage (%), or auto.
 */
sealed class DimensionSpec {
    /** Use N character cells */
    data class Cells(val count: Int) : DimensionSpec()

    /** Use N pixels */
    data class Pixels(val count: Int) : DimensionSpec()

    /** Use N% of terminal width/height */
    data class Percent(val value: Int) : DimensionSpec()

    /** Use image's intrinsic dimension */
    data object Auto : DimensionSpec()

    companion object {
        /**
         * Parse dimension specification from OSC string.
         * Examples: "80" (cells), "200px" (pixels), "50%" (percent), "auto"
         */
        fun parse(spec: String?): DimensionSpec {
            if (spec.isNullOrBlank() || spec == "auto") return Auto

            return when {
                spec.endsWith("px") -> {
                    val value = spec.dropLast(2).toIntOrNull()
                    if (value != null && value > 0) Pixels(value) else Auto
                }
                spec.endsWith("%") -> {
                    val value = spec.dropLast(1).toIntOrNull()
                    if (value != null && value in 1..100) Percent(value) else Auto
                }
                else -> {
                    val value = spec.toIntOrNull()
                    if (value != null && value > 0) Cells(value) else Auto
                }
            }
        }
    }
}
