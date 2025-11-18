package org.jetbrains.jediterm.compose.hyperlinks

data class Hyperlink(
    val url: String,
    val startCol: Int,
    val endCol: Int,
    val row: Int
)

object HyperlinkDetector {
    private val URL_REGEX = Regex(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|" +
        "file://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    )

    fun detectHyperlinks(text: String, row: Int): List<Hyperlink> {
        val matches = URL_REGEX.findAll(text)
        return matches.map { match ->
            Hyperlink(
                url = match.value,
                startCol = match.range.first,
                endCol = match.range.last + 1,
                row = row
            )
        }.toList()
    }

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
}
