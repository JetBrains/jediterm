package ai.rever.bossterm.compose.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Platform-specific update installation logic.
 *
 * Handles the actual installation of updates for different platforms.
 * For macOS, it uses a helper script pattern to safely install updates after the app quits.
 */
object UpdateInstaller {

    /**
     * Validate download file for security concerns.
     */
    private fun validateDownloadFile(downloadFile: File, expectedExtension: String) {
        if (!downloadFile.exists()) {
            throw SecurityException("Download file does not exist: ${downloadFile.absolutePath}")
        }
        if (!downloadFile.name.endsWith(expectedExtension, ignoreCase = true)) {
            throw SecurityException("Invalid file extension. Expected $expectedExtension but got: ${downloadFile.name}")
        }
        val canonicalPath = try {
            downloadFile.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("Failed to canonicalize path: ${downloadFile.absolutePath}")
        }

        val expectedTempDir = File(System.getProperty("java.io.tmpdir"), "bossterm-updates").canonicalPath
        if (!canonicalPath.startsWith(expectedTempDir)) {
            println("⚠️ Security Warning: Download file outside expected directory")
        }

        val filename = downloadFile.name
        if (filename.contains('\u0000') || filename.contains('\n') || filename.contains('\r')) {
            throw SecurityException("Filename contains invalid characters: $filename")
        }
    }

    /**
     * Extract version from update file name.
     */
    private fun extractVersionFromFilename(file: File): Version? {
        return try {
            val filename = file.name
            val versionStr = filename
                .removePrefix("BossTerm-")
                .removeSuffix("-Universal.dmg")
                .removeSuffix(".dmg")
                .removeSuffix(".msi")
                .removeSuffix(".jar")

            Version.parse(versionStr)
        } catch (e: Exception) {
            println("Failed to extract version from filename: ${e.message}")
            null
        }
    }

    /**
     * Verify update is not a downgrade.
     */
    private fun verifyNoDowngrade(downloadFile: File): Boolean {
        val downloadedVersion = extractVersionFromFilename(downloadFile)

        if (downloadedVersion == null) {
            println("⚠️ Cannot verify update version - version extraction failed")
            return true
        }

        val currentVersion = Version.CURRENT

        println("Version check:")
        println("  Current: $currentVersion")
        println("  Download: $downloadedVersion")

        if (downloadedVersion < currentVersion) {
            println("❌ DOWNGRADE DETECTED!")
            return false
        }

        if (downloadedVersion == currentVersion) {
            println("⚠️ Same version detected ($downloadedVersion) - allowing reinstall")
        } else {
            println("✅ Update verified: $currentVersion → $downloadedVersion")
        }

        return true
    }

    /**
     * Install update for the current platform.
     */
    suspend fun installUpdate(downloadPath: String): InstallResult {
        return try {
            val downloadFile = File(downloadPath)
            if (!downloadFile.exists()) {
                return InstallResult.Error("Update file not found")
            }

            if (!verifyNoDowngrade(downloadFile)) {
                return InstallResult.Error("Cannot install older version")
            }

            when (getCurrentPlatform()) {
                "macOS" -> installMacOSUpdate(downloadFile)
                "Windows" -> installWindowsUpdate(downloadFile)
                else -> installJarUpdate(downloadFile)
            }
        } catch (e: Exception) {
            println("Error installing update: ${e.message}")
            InstallResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Install macOS update using helper script pattern.
     */
    private suspend fun installMacOSUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                println("Starting macOS update installation...")
                validateDownloadFile(downloadFile, ".dmg")

                val currentAppPath = getCurrentApplicationPath()
                if (currentAppPath == null) {
                    println("⚠️ Could not determine current application path")
                    return@withContext openDMGForManualInstallation(downloadFile)
                }

                println("🎯 Target application path: $currentAppPath")

                // Verify DMG
                println("📦 Mounting DMG for verification...")
                val mountTest = ProcessBuilder(
                    "hdiutil", "attach", downloadFile.absolutePath,
                    "-nobrowse", "-quiet", "-verify"
                ).start()
                mountTest.waitFor()

                if (mountTest.exitValue() != 0) {
                    return@withContext InstallResult.Error("Failed to mount DMG for verification")
                }

                val mountedVolume = findMountedBossTermVolume()
                if (mountedVolume == null) {
                    cleanupDMG(null)
                    return@withContext InstallResult.Error("Could not locate mounted DMG volume")
                }

                try {
                    val appBundle = findAppBundleInVolume(mountedVolume)
                        ?: throw IllegalStateException("Could not find BossTerm.app in mounted DMG")
                    println("✅ DMG verified successfully (found: ${appBundle.name})")
                } finally {
                    cleanupDMG(mountedVolume)
                }

                val currentPid = ProcessHandle.current().pid()
                println("📝 Generating update script (PID: $currentPid)")

                val scriptFile = UpdateScriptGenerator.generateMacOSUpdateScript(
                    dmgPath = downloadFile.absolutePath,
                    targetAppPath = currentAppPath,
                    appPid = currentPid
                )

                println("🚀 Launching update script")
                UpdateScriptGenerator.launchScript(scriptFile)

                InstallResult.RequiresRestart("Update is ready to install. The app will quit and install the update.")
            } catch (e: Exception) {
                println("❌ Error during update preparation: ${e.message}")
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Install Windows update using helper script pattern.
     */
    private suspend fun installWindowsUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                println("Starting Windows update installation...")
                validateDownloadFile(downloadFile, ".msi")

                val currentPid = ProcessHandle.current().pid()
                val scriptFile = UpdateScriptGenerator.generateWindowsUpdateScript(
                    msiPath = downloadFile.absolutePath,
                    appPid = currentPid
                )

                UpdateScriptGenerator.launchScript(scriptFile)

                InstallResult.RequiresRestart("Update is ready to install. The app will quit and install the update.")
            } catch (e: Exception) {
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Install JAR update.
     */
    private suspend fun installJarUpdate(downloadFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                validateDownloadFile(downloadFile, ".jar")

                val currentJar = getCurrentJarPath()
                if (currentJar == null) {
                    return@withContext InstallResult.Error("Could not locate current JAR")
                }

                val backupJar = File(currentJar.parentFile, "${currentJar.name}.backup")
                currentJar.copyTo(backupJar, overwrite = true)

                downloadFile.copyTo(currentJar, overwrite = true)

                InstallResult.Success("Update installed. Restart the app to use the new version.")
            } catch (e: Exception) {
                InstallResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Get current application path for macOS .app bundle.
     */
    fun getCurrentApplicationPath(): String? {
        return try {
            val libraryPath = System.getProperty("java.library.path")
            val bundlePath = libraryPath
                ?.split(":")
                ?.find { it.contains(".app") }
                ?.let { "${it.substringBefore(".app")}.app" }

            if (bundlePath?.contains(".app") == true && File(bundlePath).exists()) {
                return bundlePath
            }

            val jarPath = UpdateInstaller::class.java.protectionDomain.codeSource.location.path
            var currentFile = File(jarPath)
            for (i in 0..5) {
                if (currentFile.name.endsWith(".app")) {
                    return currentFile.absolutePath
                }
                currentFile = currentFile.parentFile ?: break
            }

            val applicationsPath = "/Applications/BossTerm.app"
            if (File(applicationsPath).exists()) {
                return applicationsPath
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findMountedBossTermVolume(): File? {
        val volumesDir = File("/Volumes")
        return volumesDir.listFiles()?.find {
            it.name.contains("BossTerm", ignoreCase = true) && it.isDirectory
        }
    }

    fun findAppBundleInVolume(mountedVolume: File): File? {
        return mountedVolume.listFiles()?.find {
            it.name.endsWith(".app") && it.name.contains("BossTerm", ignoreCase = true)
        }
    }

    private fun openDMGForManualInstallation(downloadFile: File): InstallResult {
        return try {
            ProcessBuilder("open", downloadFile.absolutePath).start().waitFor()
            InstallResult.Success("DMG opened for manual installation")
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "Failed to open DMG")
        }
    }

    private fun cleanupDMG(mountedVolume: File?) {
        try {
            val volume = mountedVolume ?: findMountedBossTermVolume()
            if (volume != null) {
                ProcessBuilder("hdiutil", "detach", volume.absolutePath, "-quiet")
                    .start()
                    .waitFor()
            }
        } catch (e: Exception) {
            println("Warning: Could not unmount DMG: ${e.message}")
        }
    }

    private fun getCurrentJarPath(): File? {
        return try {
            val jarPath = UpdateInstaller::class.java.protectionDomain.codeSource.location.toURI().path
            val jarFile = File(jarPath)
            if (jarFile.exists() && jarFile.name.endsWith(".jar")) jarFile else null
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentPlatform(): String {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macOS"
            osName.contains("win") -> "Windows"
            osName.contains("linux") -> "Linux"
            else -> "Unknown"
        }
    }
}
