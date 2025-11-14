package org.jetbrains.jediterm.compose

/**
 * Platform-specific services abstraction.
 * Uses expect/actual pattern for platform-specific implementations.
 */
interface PlatformServices {
    /**
     * Clipboard operations
     */
    interface ClipboardService {
        /**
         * Copy text to clipboard
         */
        suspend fun copyText(text: String): Boolean

        /**
         * Get text from clipboard
         */
        suspend fun pasteText(): String?

        /**
         * Check if clipboard has text content
         */
        suspend fun hasText(): Boolean
    }

    /**
     * File system operations
     */
    interface FileSystemService {
        /**
         * Check if file exists
         */
        suspend fun fileExists(path: String): Boolean

        /**
         * Read file as text
         */
        suspend fun readTextFile(path: String): String?

        /**
         * Write text to file
         */
        suspend fun writeTextFile(path: String, content: String): Boolean

        /**
         * Get user home directory
         */
        fun getUserHomeDirectory(): String

        /**
         * Get temporary directory
         */
        fun getTempDirectory(): String
    }

    /**
     * Process/PTY operations
     */
    interface ProcessService {
        data class ProcessConfig(
            val command: String,
            val arguments: List<String>,
            val environment: Map<String, String> = emptyMap(),
            val workingDirectory: String? = null
        )

        /**
         * Spawn a new PTY process
         */
        suspend fun spawnProcess(config: ProcessConfig): ProcessHandle?

        /**
         * Process handle for managing spawned processes
         */
        interface ProcessHandle {
            /**
             * Write data to process stdin
             */
            suspend fun write(data: String)

            /**
             * Read available data from process stdout
             */
            suspend fun read(): String?

            /**
             * Check if process is alive
             */
            fun isAlive(): Boolean

            /**
             * Kill the process
             */
            suspend fun kill()

            /**
             * Resize PTY
             */
            suspend fun resize(columns: Int, rows: Int)

            /**
             * Get process exit code (null if still running)
             */
            fun getExitCode(): Int?
        }
    }

    /**
     * Platform information
     */
    interface PlatformInfo {
        /**
         * Platform name (Android, iOS, Desktop, Web)
         */
        fun getPlatformName(): String

        /**
         * OS name (Linux, macOS, Windows, Android, iOS, Browser)
         */
        fun getOSName(): String

        /**
         * OS version
         */
        fun getOSVersion(): String

        /**
         * Check if running on mobile platform
         */
        fun isMobile(): Boolean

        /**
         * Check if running on desktop platform
         */
        fun isDesktop(): Boolean

        /**
         * Check if running in browser
         */
        fun isWeb(): Boolean
    }

    /**
     * URL/Browser operations
     */
    interface BrowserService {
        /**
         * Open URL in browser
         */
        suspend fun openUrl(url: String): Boolean
    }

    /**
     * System notifications
     */
    interface NotificationService {
        /**
         * Show a system notification
         */
        suspend fun showNotification(title: String, message: String)

        /**
         * Play system beep sound
         */
        fun beep()
    }

    /**
     * Get clipboard service
     */
    fun getClipboardService(): ClipboardService

    /**
     * Get file system service
     */
    fun getFileSystemService(): FileSystemService

    /**
     * Get process service
     */
    fun getProcessService(): ProcessService

    /**
     * Get platform info
     */
    fun getPlatformInfo(): PlatformInfo

    /**
     * Get browser service
     */
    fun getBrowserService(): BrowserService

    /**
     * Get notification service
     */
    fun getNotificationService(): NotificationService
}

/**
 * Get platform services instance for current platform
 */
expect fun getPlatformServices(): PlatformServices
