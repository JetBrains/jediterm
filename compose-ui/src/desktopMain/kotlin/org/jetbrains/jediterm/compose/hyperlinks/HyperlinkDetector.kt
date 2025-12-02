package org.jetbrains.jediterm.compose.hyperlinks

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents a detected hyperlink in terminal text.
 *
 * @property url The URL to open when clicked
 * @property startCol Start column (0-based) in the line
 * @property endCol End column (exclusive) in the line
 * @property row Row number in the terminal buffer
 */
data class Hyperlink(
    val url: String,
    val startCol: Int,
    val endCol: Int,
    val row: Int
)

/**
 * Represents a hyperlink pattern that can be registered with the detector.
 *
 * @property id Unique identifier for this pattern (used for removal)
 * @property regex The regex pattern to match
 * @property priority Higher priority patterns are matched first (default: 0)
 * @property urlTransformer Transforms the matched text into a URL (default: identity)
 * @property quickCheck Optional fast check before applying regex (for performance)
 */
data class HyperlinkPattern(
    val id: String,
    val regex: Regex,
    val priority: Int = 0,
    val urlTransformer: (matchedText: String) -> String = { it },
    val quickCheck: ((line: String) -> Boolean)? = null
)

/**
 * Registry for managing hyperlink patterns.
 *
 * Thread-safe: All operations are safe to call from any thread.
 *
 * Built-in patterns (registered by default):
 * - HTTP/HTTPS URLs
 * - File URLs
 * - Mailto links
 *
 * Example custom patterns:
 * ```kotlin
 * // Jira ticket pattern
 * registry.addPattern(HyperlinkPattern(
 *     id = "jira",
 *     regex = Regex("\\b([A-Z]{2,}-\\d+)\\b"),
 *     priority = 10,
 *     urlTransformer = { "https://jira.company.com/browse/$it" },
 *     quickCheck = { line -> line.any { it.isUpperCase() } && line.any { it.isDigit() } }
 * ))
 *
 * // GitHub issue pattern
 * registry.addPattern(HyperlinkPattern(
 *     id = "github-issue",
 *     regex = Regex("#(\\d+)\\b"),
 *     priority = 5,
 *     urlTransformer = { "https://github.com/org/repo/issues/${it.removePrefix("#")}" }
 * ))
 * ```
 */
class HyperlinkRegistry {
    private val patterns = CopyOnWriteArrayList<HyperlinkPattern>()

    init {
        // Register built-in patterns
        addBuiltinPatterns()
    }

    private fun addBuiltinPatterns() {
        // HTTP/HTTPS URL pattern (priority 0 - lowest built-in)
        addPattern(HyperlinkPattern(
            id = "builtin:http",
            regex = Regex("\\bhttps?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),
            priority = 0,
            quickCheck = { it.contains("http://") || it.contains("https://") }
        ))

        // File URL pattern (priority 0)
        addPattern(HyperlinkPattern(
            id = "builtin:file",
            regex = Regex("\\bfile:(?:///|/)[-A-Za-z0-9+\$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+\$&@#/%=~_|]"),
            priority = 0,
            quickCheck = { it.contains("file:/") }
        ))

        // Mailto pattern (priority 0)
        addPattern(HyperlinkPattern(
            id = "builtin:mailto",
            regex = Regex("\\bmailto:[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}"),
            priority = 0,
            quickCheck = { it.contains("mailto:") }
        ))

        // FTP URL pattern (priority 0)
        addPattern(HyperlinkPattern(
            id = "builtin:ftp",
            regex = Regex("\\bftps?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),
            priority = 0,
            quickCheck = { it.contains("ftp://") || it.contains("ftps://") }
        ))

        // www. URL pattern (priority -1, lower than explicit protocols)
        addPattern(HyperlinkPattern(
            id = "builtin:www",
            regex = Regex("(?<![\\p{L}0-9_.])www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"),
            priority = -1,
            urlTransformer = { "https://$it" },
            quickCheck = { it.contains("www.") }
        ))
    }

    /**
     * Add a pattern to the registry.
     *
     * If a pattern with the same ID already exists, it will be replaced.
     *
     * @param pattern The pattern to add
     */
    fun addPattern(pattern: HyperlinkPattern) {
        // Remove existing pattern with same ID
        patterns.removeIf { it.id == pattern.id }
        patterns.add(pattern)
    }

    /**
     * Remove a pattern by ID.
     *
     * @param id The pattern ID to remove
     * @return true if pattern was found and removed
     */
    fun removePattern(id: String): Boolean {
        return patterns.removeIf { it.id == id }
    }

    /**
     * Get a pattern by ID.
     *
     * @param id The pattern ID
     * @return The pattern or null if not found
     */
    fun getPattern(id: String): HyperlinkPattern? {
        return patterns.find { it.id == id }
    }

    /**
     * Get all registered patterns sorted by priority (highest first).
     */
    fun getPatterns(): List<HyperlinkPattern> {
        return patterns.sortedByDescending { it.priority }
    }

    /**
     * Clear all patterns (including built-in).
     */
    fun clear() {
        patterns.clear()
    }

    /**
     * Reset to default built-in patterns only.
     */
    fun resetToDefaults() {
        patterns.clear()
        addBuiltinPatterns()
    }

    /**
     * Get the number of registered patterns.
     */
    fun size(): Int = patterns.size
}

/**
 * Extensible hyperlink detector for terminal text.
 *
 * Detects hyperlinks in terminal lines based on registered patterns.
 * Patterns are applied in priority order (highest first).
 *
 * Usage:
 * ```kotlin
 * // Get the singleton instance
 * val detector = HyperlinkDetector
 *
 * // Detect hyperlinks in a line
 * val hyperlinks = detector.detectHyperlinks("Visit https://example.com", row = 5)
 *
 * // Register a custom pattern
 * detector.registry.addPattern(HyperlinkPattern(
 *     id = "jira",
 *     regex = Regex("\\b([A-Z]{2,}-\\d+)\\b"),
 *     priority = 10,
 *     urlTransformer = { "https://jira.company.com/browse/$it" }
 * ))
 *
 * // Remove a pattern
 * detector.registry.removePattern("jira")
 * ```
 */
object HyperlinkDetector {
    /**
     * The pattern registry. Use this to add/remove custom patterns.
     */
    val registry = HyperlinkRegistry()

    /**
     * Detect all hyperlinks in a line of text.
     *
     * Patterns are applied in priority order. If multiple patterns match
     * overlapping regions, only the first match (highest priority) is kept.
     *
     * @param text The line of text to scan
     * @param row The row number in the terminal buffer
     * @return List of detected hyperlinks, sorted by column position
     */
    fun detectHyperlinks(text: String, row: Int): List<Hyperlink> {
        if (text.isEmpty()) return emptyList()

        val hyperlinks = mutableListOf<Hyperlink>()
        val coveredRanges = mutableListOf<IntRange>()

        // Apply patterns in priority order
        for (pattern in registry.getPatterns()) {
            // Skip if quick check fails
            if (pattern.quickCheck != null && !pattern.quickCheck.invoke(text)) {
                continue
            }

            val matches = pattern.regex.findAll(text)
            for (match in matches) {
                val range = match.range

                // Check if this range overlaps with already detected hyperlinks
                val overlaps = coveredRanges.any { existing ->
                    range.first <= existing.last && range.last >= existing.first
                }

                if (!overlaps) {
                    val url = pattern.urlTransformer(match.value)
                    hyperlinks.add(Hyperlink(
                        url = url,
                        startCol = range.first,
                        endCol = range.last + 1,
                        row = row
                    ))
                    coveredRanges.add(range)
                }
            }
        }

        // Sort by column position
        return hyperlinks.sortedBy { it.startCol }
    }

    /**
     * Open a URL using the system default handler.
     *
     * @param url The URL to open
     */
    fun openUrl(url: String) {
        try {
            when {
                System.getProperty("os.name").lowercase().contains("mac") -> {
                    Runtime.getRuntime().exec(arrayOf("open", url))
                }
                System.getProperty("os.name").lowercase().contains("win") -> {
                    Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
                }
                else -> {
                    Runtime.getRuntime().exec(arrayOf("xdg-open", url))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if a line potentially contains any hyperlinks.
     *
     * This is a fast check that can be used to skip expensive regex matching
     * for lines that definitely don't contain URLs.
     *
     * @param line The line to check
     * @return true if the line might contain hyperlinks
     */
    fun canContainHyperlink(line: String): Boolean {
        return line.contains("://") ||
               line.contains("www.") ||
               line.contains("mailto:") ||
               line.contains("file:/")
    }
}
