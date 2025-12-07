package ai.rever.bossterm.compose.update

import kotlinx.serialization.Serializable

/**
 * GitHub Release data models
 */
@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String? = null,
    val size: Long = 0,
    val content_type: String = ""
)

/**
 * Update information for the application
 */
data class UpdateInfo(
    val available: Boolean,
    val currentVersion: Version,
    val latestVersion: Version,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val assetSize: Long = 0,
    val assetName: String = ""
) {
    val isNewerVersionAvailable: Boolean
        get() = available && latestVersion.isNewerThan(currentVersion)
}

/**
 * Update check result sealed class
 */
sealed class UpdateResult {
    object NoUpdateAvailable : UpdateResult()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateResult()
    data class Error(val message: String, val exception: Exception? = null) : UpdateResult()
}

/**
 * Installation result sealed class
 */
sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class RequiresRestart(val message: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
