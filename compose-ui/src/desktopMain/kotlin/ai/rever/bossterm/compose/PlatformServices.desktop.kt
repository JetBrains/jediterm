package ai.rever.bossterm.compose

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
            val command = arrayOf(config.command) + config.arguments.toTypedArray()
            val pty = com.pty4j.PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(config.environment)
                .setDirectory(config.workingDirectory ?: System.getProperty("user.home"))
                .start()
            PtyProcessHandle(pty)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private class PtyProcessHandle(private val process: com.pty4j.PtyProcess) : PlatformServices.ProcessService.ProcessHandle {
        private val inputStream = process.inputStream
        private val outputStream = process.outputStream

        /**
         * Buffer for incomplete UTF-8 byte sequences at read boundaries.
         * UTF-8 characters can be 1-4 bytes, so we may need to buffer up to 3 bytes
         * if a read ends mid-character.
         */
        private val incompleteUtf8Buffer = mutableListOf<Byte>()

        override suspend fun write(data: String) {
            outputStream.write(data.toByteArray())
            outputStream.flush()
        }

        override suspend fun read(): String? {
            return try {
                val buffer = ByteArray(8192)
                val len = inputStream.read(buffer)
                if (len <= 0) return null

                // Combine any buffered incomplete UTF-8 bytes with new data
                val allBytes = if (incompleteUtf8Buffer.isNotEmpty()) {
                    val combined = ByteArray(incompleteUtf8Buffer.size + len)
                    incompleteUtf8Buffer.forEachIndexed { index, byte -> combined[index] = byte }
                    System.arraycopy(buffer, 0, combined, incompleteUtf8Buffer.size, len)
                    incompleteUtf8Buffer.clear()
                    combined
                } else {
                    buffer.copyOf(len)
                }

                // Check if data ends with incomplete UTF-8 sequence
                val lastCompleteByteIndex = findLastCompleteUtf8Boundary(allBytes)

                if (lastCompleteByteIndex < allBytes.size) {
                    // Buffer incomplete bytes for next read
                    for (i in lastCompleteByteIndex until allBytes.size) {
                        incompleteUtf8Buffer.add(allBytes[i])
                    }

                    if (lastCompleteByteIndex == 0) {
                        // Entire buffer is incomplete - very rare, but possible
                        return ""
                    }

                    // Return only complete UTF-8 characters
                    String(allBytes, 0, lastCompleteByteIndex, Charsets.UTF_8)
                } else {
                    // All bytes form complete UTF-8 characters
                    String(allBytes, 0, allBytes.size, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Find the index after the last complete UTF-8 character in a byte array.
         * Returns the index where incomplete bytes start (or array length if all complete).
         *
         * UTF-8 encoding rules:
         * - 0xxxxxxx: 1-byte character (ASCII)
         * - 110xxxxx 10xxxxxx: 2-byte character
         * - 1110xxxx 10xxxxxx 10xxxxxx: 3-byte character
         * - 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx: 4-byte character
         * - 10xxxxxx: continuation byte (invalid as first byte)
         */
        private fun findLastCompleteUtf8Boundary(bytes: ByteArray): Int {
            if (bytes.isEmpty()) return 0

            // Check last few bytes for incomplete sequence
            // Max UTF-8 character is 4 bytes, so check last 4 bytes
            val checkStart = maxOf(0, bytes.size - 4)

            for (i in bytes.size - 1 downTo checkStart) {
                val byte = bytes[i].toInt() and 0xFF

                // Check if this starts a multi-byte character
                val expectedLength = when {
                    (byte and 0x80) == 0 -> 1        // 0xxxxxxx (ASCII)
                    (byte and 0xE0) == 0xC0 -> 2    // 110xxxxx
                    (byte and 0xF0) == 0xE0 -> 3    // 1110xxxx
                    (byte and 0xF8) == 0xF0 -> 4    // 11110xxx
                    (byte and 0xC0) == 0x80 -> continue  // 10xxxxxx (continuation byte, skip)
                    else -> 1  // Invalid UTF-8, treat as 1 byte
                }

                val actualLength = bytes.size - i
                if (actualLength < expectedLength) {
                    // Incomplete multi-byte character
                    return i
                }
            }

            // All characters are complete
            return bytes.size
        }

        override fun isAlive(): Boolean = process.isAlive

        override suspend fun kill() {
            process.destroy()
        }

        override suspend fun waitFor(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            process.waitFor()
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