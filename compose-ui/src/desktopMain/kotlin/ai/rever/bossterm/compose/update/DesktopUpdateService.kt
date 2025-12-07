package ai.rever.bossterm.compose.update

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Desktop implementation of update service using GitHub Releases API.
 */
class DesktopUpdateService {

    private val apiClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    private val downloadClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 900_000  // 15 minutes for large downloads
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }

    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val RELEASES_REPO = "kshivang/BossTerm"
        private const val RELEASES_ENDPOINT = "$GITHUB_API_BASE/repos/$RELEASES_REPO/releases"
    }

    /**
     * Check for available updates.
     */
    suspend fun checkForUpdates(): UpdateInfo {
        return try {
            if (GitHubConfig.hasToken) {
                println("✅ Using authenticated GitHub API (5,000 requests/hour)")
            } else {
                println("⚠️ Using unauthenticated GitHub API (60 requests/hour)")
            }

            val response = apiClient.get(RELEASES_ENDPOINT) {
                headers {
                    append("Accept", "application/vnd.github.v3+json")
                    append("User-Agent", "BossTerm-Desktop-${Version.CURRENT}")
                    GitHubConfig.token?.let { append("Authorization", "Bearer $it") }
                }
            }

            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                val errorMessage = when {
                    errorBody.contains("rate limit", ignoreCase = true) ->
                        "GitHub API rate limit exceeded. Please try again later."
                    else -> "Unable to check for updates (HTTP ${response.status.value})"
                }
                println("Update check failed: $errorMessage")
                return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )
            }

            val releases = response.body<List<GitHubRelease>>()

            val latestRelease = releases
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    Version.parse(release.tag_name)?.let { version -> release to version }
                }
                .maxByOrNull { it.second }
                ?.first

            if (latestRelease == null) {
                return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )
            }

            val latestVersion = Version.parse(latestRelease.tag_name)
                ?: return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )

            val isUpdateAvailable = latestVersion.isNewerThan(Version.CURRENT)

            val expectedAssetName = getExpectedAssetName(latestVersion)
            println("Looking for asset: $expectedAssetName")
            println("Available assets: ${latestRelease.assets.map { it.name }}")

            val asset = latestRelease.assets.find {
                it.name.equals(expectedAssetName, ignoreCase = true)
            }

            UpdateInfo(
                available = isUpdateAvailable,
                currentVersion = Version.CURRENT,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = asset?.browser_download_url,
                assetSize = asset?.size ?: 0,
                assetName = asset?.name ?: ""
            )
        } catch (e: Exception) {
            println("Error checking for updates: ${e.message}")
            UpdateInfo(
                available = false,
                currentVersion = Version.CURRENT,
                latestVersion = Version.CURRENT,
                releaseNotes = ""
            )
        }
    }

    /**
     * Download an update.
     */
    suspend fun downloadUpdate(
        updateInfo: UpdateInfo,
        onProgress: (progress: Float) -> Unit
    ): String? {
        return try {
            val downloadUrl = updateInfo.downloadUrl ?: return null

            println("Starting download from: $downloadUrl")

            val response = downloadClient.get(downloadUrl)
            if (response.status.value !in 200..299) {
                println("Download failed with HTTP status: ${response.status.value}")
                return null
            }

            val totalSize = response.headers["Content-Length"]?.toLongOrNull() ?: updateInfo.assetSize
            val tempDir = File(System.getProperty("java.io.tmpdir"), "bossterm-updates")
            tempDir.mkdirs()

            val downloadFile = File(tempDir, updateInfo.assetName)
            if (downloadFile.exists()) {
                downloadFile.delete()
            }

            withContext(Dispatchers.IO) {
                val channel = response.bodyAsChannel()
                val outputStream = FileOutputStream(downloadFile)

                var downloadedBytes = 0L
                val buffer = ByteArray(8192)
                var lastProgressUpdate = 0L

                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val shouldUpdateProgress = if (totalSize > 0) {
                            downloadedBytes - lastProgressUpdate >= 262144 ||
                                (downloadedBytes.toFloat() / totalSize - lastProgressUpdate.toFloat() / totalSize) >= 0.05f
                        } else {
                            downloadedBytes - lastProgressUpdate >= 131072
                        }

                        if (shouldUpdateProgress) {
                            val progress = if (totalSize > 0) {
                                (downloadedBytes.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.1f + (downloadedBytes / 1048576f % 0.8f)
                            }

                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                            lastProgressUpdate = downloadedBytes
                        }
                    }
                }

                outputStream.close()
                channel.cancel()

                withContext(Dispatchers.Main) {
                    onProgress(1f)
                }
            }

            if (downloadFile.exists() && downloadFile.length() > 0) {
                println("Update downloaded successfully: ${downloadFile.absolutePath}")
                downloadFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error downloading update: ${e.message}")
            null
        }
    }

    /**
     * Install an update.
     */
    suspend fun installUpdate(downloadPath: String): Boolean {
        val result = UpdateInstaller.installUpdate(downloadPath)

        return when (result) {
            is InstallResult.Success -> {
                println("✅ ${result.message}")
                true
            }
            is InstallResult.RequiresRestart -> {
                println("🔄 ${result.message}")
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    delay(1000)
                    quitForUpdate()
                }
                true
            }
            is InstallResult.Error -> {
                println("❌ ${result.message}")
                false
            }
        }
    }

    fun getCurrentPlatform(): String = UpdateInstaller.getCurrentPlatform()

    fun getExpectedAssetName(version: Version): String {
        return when (getCurrentPlatform()) {
            "macOS" -> "BossTerm-${version}.dmg"
            "Windows" -> "BossTerm-${version}.msi"
            else -> "BossTerm-${version}.jar"
        }
    }

    /**
     * Quit the application for update installation.
     */
    private fun quitForUpdate() {
        println("Quitting application for update...")
        System.exit(0)
    }
}
