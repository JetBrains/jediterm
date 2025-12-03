package com.jediterm.terminal.model.pool

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe object pool for char arrays used in snapshot-based rendering.
 *
 * Uses size-bucketed pools to minimize wasted memory while providing O(1)
 * acquire/release operations. Arrays are pooled by size bucket to reduce
 * memory fragmentation and improve cache locality.
 *
 * **Performance Characteristics**:
 * - Acquire: O(1) from pool or O(n) allocation if pool empty
 * - Release: O(1)
 * - Thread-safe via ConcurrentLinkedQueue (lock-free)
 *
 * **Size Buckets** (optimized for terminal line lengths):
 * - 32 chars: Short prompts, status lines
 * - 80 chars: Standard terminal width
 * - 160 chars: Wide terminals, tabs
 * - 320 chars: Very wide terminals, long lines
 * - >320 chars: Direct allocation (no pooling)
 *
 * **Memory Budget**: ~500 arrays per bucket = ~320KB total pool overhead
 */
object CharArrayPool {

    /**
     * Size thresholds for each bucket. Arrays are rounded up to next bucket size.
     */
    private val BUCKET_SIZES = intArrayOf(32, 80, 160, 320)

    /**
     * Maximum arrays to keep in each bucket pool.
     * 500 arrays * 4 buckets * avg 100 chars * 2 bytes = ~400KB max pool size
     */
    private const val MAX_POOL_SIZE_PER_BUCKET = 500

    /**
     * Thread-safe queues for each size bucket.
     */
    private val pools = Array(BUCKET_SIZES.size) { ConcurrentLinkedQueue<CharArray>() }

    /**
     * Current pool sizes for monitoring (approximate due to concurrent access).
     */
    private val poolSizes = Array(BUCKET_SIZES.size) { AtomicInteger(0) }

    // Statistics
    private val acquireHits = AtomicLong(0)
    private val acquireMisses = AtomicLong(0)
    private val releaseSuccess = AtomicLong(0)
    private val releaseDropped = AtomicLong(0)

    /**
     * Acquire a char array of at least [minSize] characters.
     *
     * Returns a pooled array if available, otherwise allocates a new one.
     * The returned array may be larger than [minSize] due to bucket rounding.
     *
     * **IMPORTANT**: Caller must call [release] when done, or memory will leak.
     *
     * @param minSize Minimum array size needed
     * @return Char array of at least [minSize] characters (may be larger)
     */
    fun acquire(minSize: Int): CharArray {
        val bucketIndex = findBucketIndex(minSize)

        // Oversized arrays are not pooled
        if (bucketIndex < 0) {
            acquireMisses.incrementAndGet()
            return CharArray(minSize)
        }

        val pool = pools[bucketIndex]
        val array = pool.poll()

        return if (array != null) {
            poolSizes[bucketIndex].decrementAndGet()
            acquireHits.incrementAndGet()
            array
        } else {
            acquireMisses.incrementAndGet()
            CharArray(BUCKET_SIZES[bucketIndex])
        }
    }

    /**
     * Return a char array to the pool for reuse.
     *
     * Arrays that don't fit any bucket or exceed pool limits are dropped.
     *
     * @param array Array to return to pool
     */
    fun release(array: CharArray) {
        val bucketIndex = findExactBucketIndex(array.size)

        // Only pool arrays that exactly match bucket sizes
        if (bucketIndex < 0) {
            releaseDropped.incrementAndGet()
            return
        }

        val pool = pools[bucketIndex]
        val currentSize = poolSizes[bucketIndex].get()

        // Drop if pool is full (prevents unbounded memory growth)
        if (currentSize >= MAX_POOL_SIZE_PER_BUCKET) {
            releaseDropped.incrementAndGet()
            return
        }

        // CAS-increment pool size, only add if still under limit
        if (poolSizes[bucketIndex].incrementAndGet() <= MAX_POOL_SIZE_PER_BUCKET) {
            pool.offer(array)
            releaseSuccess.incrementAndGet()
        } else {
            poolSizes[bucketIndex].decrementAndGet()
            releaseDropped.incrementAndGet()
        }
    }

    /**
     * Find the smallest bucket that can hold [size] characters.
     *
     * @return Bucket index, or -1 if size exceeds all buckets
     */
    private fun findBucketIndex(size: Int): Int {
        for (i in BUCKET_SIZES.indices) {
            if (size <= BUCKET_SIZES[i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Find bucket index for exact size match (for release).
     *
     * @return Bucket index, or -1 if size doesn't match any bucket
     */
    private fun findExactBucketIndex(size: Int): Int {
        for (i in BUCKET_SIZES.indices) {
            if (size == BUCKET_SIZES[i]) {
                return i
            }
        }
        return -1
    }

    /**
     * Get current pool statistics for monitoring/debugging.
     */
    fun getStats(): PoolStats {
        return PoolStats(
            bucketSizes = BUCKET_SIZES.clone(),
            poolSizes = poolSizes.map { it.get() }.toIntArray(),
            maxPoolSize = MAX_POOL_SIZE_PER_BUCKET,
            acquireHits = acquireHits.get(),
            acquireMisses = acquireMisses.get(),
            releaseSuccess = releaseSuccess.get(),
            releaseDropped = releaseDropped.get()
        )
    }

    /**
     * Clear all pools. Useful for testing or memory pressure situations.
     */
    fun clear() {
        pools.forEach { it.clear() }
        poolSizes.forEach { it.set(0) }
    }

    /**
     * Reset statistics. Useful for testing.
     */
    fun resetStats() {
        acquireHits.set(0)
        acquireMisses.set(0)
        releaseSuccess.set(0)
        releaseDropped.set(0)
    }
}

/**
 * Pool statistics for monitoring and debugging.
 */
data class PoolStats(
    val bucketSizes: IntArray,
    val poolSizes: IntArray,
    val maxPoolSize: Int,
    val acquireHits: Long,
    val acquireMisses: Long,
    val releaseSuccess: Long,
    val releaseDropped: Long
) {
    val hitRate: Double
        get() {
            val total = acquireHits + acquireMisses
            return if (total > 0) acquireHits.toDouble() / total else 0.0
        }

    val totalAcquires: Long
        get() = acquireHits + acquireMisses

    val totalReleases: Long
        get() = releaseSuccess + releaseDropped

    override fun toString(): String {
        return buildString {
            appendLine("CharArrayPool Stats:")
            appendLine("  Buckets: ${bucketSizes.contentToString()}")
            appendLine("  Pool sizes: ${poolSizes.contentToString()} (max: $maxPoolSize)")
            appendLine("  Hit rate: ${String.format("%.2f", hitRate * 100)}%")
            appendLine("  Acquires: $totalAcquires (hits: $acquireHits, misses: $acquireMisses)")
            appendLine("  Releases: $totalReleases (pooled: $releaseSuccess, dropped: $releaseDropped)")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PoolStats
        return bucketSizes.contentEquals(other.bucketSizes) &&
               poolSizes.contentEquals(other.poolSizes) &&
               maxPoolSize == other.maxPoolSize &&
               acquireHits == other.acquireHits &&
               acquireMisses == other.acquireMisses &&
               releaseSuccess == other.releaseSuccess &&
               releaseDropped == other.releaseDropped
    }

    override fun hashCode(): Int {
        var result = bucketSizes.contentHashCode()
        result = 31 * result + poolSizes.contentHashCode()
        result = 31 * result + maxPoolSize
        result = 31 * result + acquireHits.hashCode()
        result = 31 * result + acquireMisses.hashCode()
        result = 31 * result + releaseSuccess.hashCode()
        result = 31 * result + releaseDropped.hashCode()
        return result
    }
}
