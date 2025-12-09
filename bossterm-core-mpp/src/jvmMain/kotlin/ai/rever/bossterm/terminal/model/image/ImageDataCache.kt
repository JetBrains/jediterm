package ai.rever.bossterm.terminal.model.image

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple LRU cache for image data.
 * Images are referenced by ID from ImageAnchorCell in the terminal buffer.
 *
 * Unlike TerminalImageStorage, this class does NOT track placements -
 * that's handled by image cells in the buffer, which flow naturally with text.
 */
class ImageDataCache(
    private val maxImages: Int = 100,
    private val maxTotalBytes: Long = 50 * 1024 * 1024 // 50MB
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ImageDataCache::class.java)
    }

    private val images = ConcurrentHashMap<Long, TerminalImage>()
    private val accessOrder = ConcurrentHashMap<Long, Long>() // id -> access timestamp
    private val accessCounter = AtomicLong(0)
    private var totalBytes: Long = 0

    /**
     * Store image data. Returns imageId for reference from ImageAnchorCell.
     */
    fun storeImage(image: TerminalImage): Long {
        ensureCapacity(image.data.size.toLong())
        images[image.id] = image
        accessOrder[image.id] = accessCounter.incrementAndGet()
        totalBytes += image.data.size
        LOG.debug("Stored image id={}, size={} bytes, total images={}",
            image.id, image.data.size, images.size)
        return image.id
    }

    /**
     * Get image by ID. Updates access order for LRU eviction.
     */
    fun getImage(imageId: Long): TerminalImage? {
        val image = images[imageId] ?: return null
        accessOrder[imageId] = accessCounter.incrementAndGet()
        return image
    }

    /**
     * Check if image exists in cache.
     */
    fun hasImage(imageId: Long): Boolean = images.containsKey(imageId)

    /**
     * Remove image from cache.
     * Called when image cells are cleared from buffer (text overwrite, scroll out).
     */
    fun removeImage(imageId: Long): Boolean {
        val image = images.remove(imageId) ?: return false
        totalBytes -= image.data.size
        accessOrder.remove(imageId)
        LOG.debug("Removed image id={}, remaining images={}", imageId, images.size)
        return true
    }

    /**
     * Clear all cached images.
     */
    fun clearAll() {
        images.clear()
        accessOrder.clear()
        totalBytes = 0
        LOG.debug("Cleared all images")
    }

    /**
     * Get current number of cached images.
     */
    val imageCount: Int get() = images.size

    /**
     * Get total memory used by cached images.
     */
    val totalMemoryUsed: Long get() = totalBytes

    /**
     * Ensure capacity by evicting LRU images.
     */
    private fun ensureCapacity(newBytes: Long) {
        while (totalBytes + newBytes > maxTotalBytes && images.isNotEmpty()) {
            evictLRU()
        }
        while (images.size >= maxImages && images.isNotEmpty()) {
            evictLRU()
        }
    }

    private fun evictLRU() {
        val lruId = accessOrder.minByOrNull { it.value }?.key ?: return
        LOG.debug("Evicting LRU image id={}", lruId)
        removeImage(lruId)
    }
}
