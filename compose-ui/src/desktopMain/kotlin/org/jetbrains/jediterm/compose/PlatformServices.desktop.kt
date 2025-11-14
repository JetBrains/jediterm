package org.jetbrains.jediterm.compose

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Desktop platform services implementation.
 * Track 6 will provide full implementation.
 */
actual fun getPlatformServices(): PlatformServices = DesktopPlatformServices()

class DesktopPlatformServices : PlatformServices {
    private val clipboardService = DesktopClipboardService()
    private val fileSystemService = DesktopFileSystemService()
    private val processService = DesktopProcessService()
    private val platformInfo = DesktopPlatformInfo()
    private val browserService = DesktopBrowserService()
    private val notificationService = DesktopNotificationService()

    override fun getClipboardService() = clipboardService
    override fun getFileSystemService() = fileSystemService
    override fun getProcessService() = processService
    override fun getPlatformInfo() = platformInfo
    override fun getBrowserService() = browserService
    override fun getNotificationService() = notificationService
}

class DesktopClipboardService : PlatformServices.ClipboardService {
    override suspend fun copyText(text: String): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun pasteText(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun hasText(): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
        } catch (e: Exception) {
            false
        }
    }
}

class DesktopFileSystemService : PlatformServices.FileSystemService {
    override suspend fun fileExists(path: String): Boolean {
        // TODO: Track 6 - Implement file operations
        return java.io.File(path).exists()
    }

    override suspend fun readTextFile(path: String): String? {
        // TODO: Track 6 - Implement file reading
        return try {
            java.io.File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean {
        // TODO: Track 6 - Implement file writing
        return try {
            java.io.File(path).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getUserHomeDirectory(): String = System.getProperty("user.home")

    override fun getTempDirectory(): String = System.getProperty("java.io.tmpdir")
}

class DesktopProcessService : PlatformServices.ProcessService {
    override suspend fun spawnProcess(config: PlatformServices.ProcessService.ProcessConfig): PlatformServices.ProcessService.ProcessHandle? {
        return try {
            val pty = com.pty4j.PtyProcess.exec(
                arrayOf(config.command) + config.arguments.toTypedArray(),
                config.environment,
                config.workingDirectory ?: System.getProperty("user.home")
            )
            PtyProcessHandle(pty)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private class PtyProcessHandle(private val process: com.pty4j.PtyProcess) : PlatformServices.ProcessService.ProcessHandle {
        private val inputStream = process.inputStream
        private val outputStream = process.outputStream

        override suspend fun write(data: String) {
            outputStream.write(data.toByteArray())
            outputStream.flush()
        }

        override suspend fun read(): String? {
            return try {
                val buffer = ByteArray(8192)
                val len = inputStream.read(buffer)
                if (len > 0) String(buffer, 0, len) else null
            } catch (e: Exception) {
                null
            }
        }

        override fun isAlive(): Boolean = process.isAlive

        override suspend fun kill() {
            process.destroy()
        }

        override suspend fun resize(columns: Int, rows: Int) {
            process.setWinSize(com.pty4j.WinSize(columns, rows))
        }

        override fun getExitCode(): Int? = if (process.isAlive) null else process.exitValue()
    }
}

class DesktopPlatformInfo : PlatformServices.PlatformInfo {
    override fun getPlatformName(): String = "Desktop"

    override fun getOSName(): String = System.getProperty("os.name")

    override fun getOSVersion(): String = System.getProperty("os.version")

    override fun isMobile(): Boolean = false

    override fun isDesktop(): Boolean = true

    override fun isWeb(): Boolean = false
}

class DesktopBrowserService : PlatformServices.BrowserService {
    override suspend fun openUrl(url: String): Boolean {
        return try {
            // TODO: Track 6 - Implement proper browser opening
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

class DesktopNotificationService : PlatformServices.NotificationService {
    override suspend fun showNotification(title: String, message: String) {
        // TODO: Track 6 - Implement system notifications
        println("Notification: $title - $message")
    }

    override fun beep() {
        Toolkit.getDefaultToolkit().beep()
    }
}