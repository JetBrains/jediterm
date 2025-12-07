package ai.rever.bossterm.compose.update

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Properties

/**
 * Configuration for GitHub API access.
 *
 * GitHub API rate limits:
 * - Unauthenticated: 60 requests/hour
 * - Authenticated: 5,000 requests/hour
 *
 * The GitHub token is obtained from multiple sources (in order):
 * 1. Environment variable: GITHUB_TOKEN
 * 2. System property: GITHUB_TOKEN
 * 3. local.properties file: GITHUB_TOKEN=ghp_...
 * 4. GitHub CLI (gh auth token)
 * 5. No token (fallback to unauthenticated access)
 */
object GitHubConfig {
    /**
     * GitHub Personal Access Token loaded from secure sources.
     * Attempts to use GitHub CLI if no token is explicitly configured.
     * Returns null if not configured (will use unauthenticated access).
     */
    val token: String? by lazy {
        // Try explicit configuration first
        getConfiguredToken() ?: getTokenFromGitHubCLI()
    }

    /**
     * Check if GitHub token is configured
     */
    val hasToken: Boolean
        get() = token != null

    /**
     * Get token from environment, system property, or local.properties
     */
    private fun getConfiguredToken(): String? {
        // 1. Environment variable
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }

        // 2. System property
        System.getProperty("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }

        // 3. local.properties file
        try {
            val localProps = File("local.properties")
            if (localProps.exists()) {
                val props = Properties()
                localProps.inputStream().use { props.load(it) }
                props.getProperty("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (e: Exception) {
            // Ignore errors reading local.properties
        }

        return null
    }

    /**
     * Attempt to retrieve token from GitHub CLI (gh auth token)
     * Returns null if gh is not installed or not authenticated
     */
    private fun getTokenFromGitHubCLI(): String? {
        return try {
            val process = ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText().trim()
            }

            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank() && !output.contains("not logged in", ignoreCase = true)) {
                println("✅ Using GitHub token from GitHub CLI (gh)")
                output
            } else {
                null
            }
        } catch (e: Exception) {
            // gh command not found or other error - silently ignore
            null
        }
    }
}
