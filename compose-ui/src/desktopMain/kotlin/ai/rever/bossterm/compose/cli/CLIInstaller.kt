package ai.rever.bossterm.compose.cli

import java.io.File
import java.io.FileOutputStream

/**
 * Handles installation of the `bossterm` command line tool.
 */
object CLIInstaller {
    private const val CLI_NAME = "bossterm"
    private const val INSTALL_PATH = "/usr/local/bin/bossterm"

    /**
     * Check if CLI is already installed
     */
    fun isInstalled(): Boolean {
        val file = File(INSTALL_PATH)
        return file.exists() && file.canExecute()
    }

    /**
     * Check if CLI needs update (compare versions)
     */
    fun needsUpdate(): Boolean {
        if (!isInstalled()) return false

        // Read installed version
        val installedVersion = try {
            val process = ProcessBuilder(INSTALL_PATH, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            // Parse "BossTerm CLI version X.X.X"
            output.substringAfter("version ").substringBefore("\n").trim()
        } catch (e: Exception) {
            "0.0.0"
        }

        return installedVersion != getCurrentVersion()
    }

    /**
     * Get current CLI version from embedded resource
     */
    fun getCurrentVersion(): String {
        return "1.0.0" // TODO: Read from build config
    }

    /**
     * Install the CLI tool. Returns result message.
     */
    fun install(): InstallResult {
        return try {
            // Extract CLI script from resources
            val scriptContent = getCLIScript()

            // Check if /usr/local/bin exists
            val installDir = File("/usr/local/bin")
            if (!installDir.exists()) {
                return InstallResult.Error("Directory /usr/local/bin does not exist")
            }

            // Try to write directly (might work if user has permissions)
            val targetFile = File(INSTALL_PATH)
            try {
                FileOutputStream(targetFile).use { out ->
                    out.write(scriptContent.toByteArray())
                }
                targetFile.setExecutable(true, false)
                InstallResult.Success
            } catch (e: SecurityException) {
                // Need sudo - use AppleScript to request admin privileges
                installWithAdminPrivileges(scriptContent)
            } catch (e: Exception) {
                installWithAdminPrivileges(scriptContent)
            }
        } catch (e: Exception) {
            InstallResult.Error("Failed to install: ${e.message}")
        }
    }

    /**
     * Uninstall the CLI tool
     */
    fun uninstall(): InstallResult {
        return try {
            val targetFile = File(INSTALL_PATH)
            if (!targetFile.exists()) {
                return InstallResult.Success
            }

            try {
                targetFile.delete()
                InstallResult.Success
            } catch (e: Exception) {
                uninstallWithAdminPrivileges()
            }
        } catch (e: Exception) {
            InstallResult.Error("Failed to uninstall: ${e.message}")
        }
    }

    private fun installWithAdminPrivileges(scriptContent: String): InstallResult {
        return try {
            // Create temp file with script content
            val tempFile = File.createTempFile("bossterm_cli", ".sh")
            tempFile.writeText(scriptContent)
            tempFile.setExecutable(true)

            // Use osascript to run with admin privileges
            val script = """
                do shell script "cp '${tempFile.absolutePath}' '$INSTALL_PATH' && chmod +x '$INSTALL_PATH'" with administrator privileges
            """.trimIndent()

            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            tempFile.delete()

            if (exitCode == 0) {
                InstallResult.Success
            } else {
                val error = process.inputStream.bufferedReader().readText()
                if (error.contains("User canceled") || error.contains("cancelled")) {
                    InstallResult.Cancelled
                } else {
                    InstallResult.Error("Installation failed: $error")
                }
            }
        } catch (e: Exception) {
            InstallResult.Error("Failed to install with admin privileges: ${e.message}")
        }
    }

    private fun uninstallWithAdminPrivileges(): InstallResult {
        return try {
            val script = """
                do shell script "rm -f '$INSTALL_PATH'" with administrator privileges
            """.trimIndent()

            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                InstallResult.Success
            } else {
                val error = process.inputStream.bufferedReader().readText()
                if (error.contains("User canceled") || error.contains("cancelled")) {
                    InstallResult.Cancelled
                } else {
                    InstallResult.Error("Uninstall failed: $error")
                }
            }
        } catch (e: Exception) {
            InstallResult.Error("Failed to uninstall: ${e.message}")
        }
    }

    /**
     * Get the CLI script content
     */
    private fun getCLIScript(): String {
        // Try to load from resources first
        val resourceStream = CLIInstaller::class.java.classLoader?.getResourceAsStream("cli/bossterm")
        if (resourceStream != null) {
            return resourceStream.bufferedReader().readText()
        }

        // Fallback to embedded script
        return EMBEDDED_CLI_SCRIPT
    }

    sealed class InstallResult {
        object Success : InstallResult()
        object Cancelled : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    // Embedded CLI script (fallback if resource not found)
    private val EMBEDDED_CLI_SCRIPT = """
#!/usr/bin/env bash
#
# BossTerm CLI Launcher Script
# Version: 1.0.0
#

APP_PATH="/Applications/BossTerm.app"
APP_NAME="BossTerm"
VERSION="1.0.0"

check_app() {
    if [ ! -d "${'$'}APP_PATH" ]; then
        echo "Error: BossTerm.app not found at ${'$'}APP_PATH"
        exit 1
    fi
}

open_bossterm() {
    open -a "${'$'}APP_NAME" "${'$'}@"
}

expand_path() {
    local path="${'$'}1"
    path="${'$'}{path/#\~/${'$'}HOME}"
    if [[ ! "${'$'}path" =~ ^/ ]]; then
        path="${'$'}(cd "${'$'}path" 2>/dev/null && pwd || echo "${'$'}(pwd)/${'$'}path")"
    fi
    echo "${'$'}{path}"
}

show_help() {
    cat <<EOF
BossTerm - Modern Terminal Emulator
Version: ${'$'}VERSION

Usage:
  bossterm                      Open BossTerm
  bossterm <path>               Open BossTerm in directory
  bossterm -d <path>            Open BossTerm in specified directory
  bossterm -c <command>         Execute command (coming soon)
  bossterm --new-window         Open a new window

Options:
  -d, --directory <path>   Start in specified directory
  -c, --command <cmd>      Execute command after opening
  -n, --new-window         Force open a new window
  -v, --version            Show version information
  -h, --help               Show this help message

EOF
}

main() {
    check_app

    if [ ${'$'}# -eq 0 ]; then
        open_bossterm
        exit 0
    fi

    case "${'$'}1" in
        -h|--help|help)
            show_help
            exit 0
            ;;
        -v|--version|version)
            echo "BossTerm CLI version ${'$'}VERSION"
            exit 0
            ;;
        -n|--new-window)
            open_bossterm -n
            exit 0
            ;;
        -d|--directory)
            if [ -z "${'$'}2" ]; then
                echo "Error: Directory path required"
                exit 1
            fi
            dir_path=${'$'}(expand_path "${'$'}2")
            if [ ! -d "${'$'}dir_path" ]; then
                echo "Error: Directory not found: ${'$'}dir_path"
                exit 1
            fi
            BOSSTERM_CWD="${'$'}dir_path" open_bossterm
            exit 0
            ;;
        -c|--command)
            if [ -z "${'$'}2" ]; then
                echo "Error: Command required"
                exit 1
            fi
            echo "Note: Command execution coming soon"
            open_bossterm
            exit 0
            ;;
        -*)
            echo "Error: Unknown option: ${'$'}1"
            echo "Run 'bossterm --help' for usage"
            exit 1
            ;;
        *)
            path=${'$'}(expand_path "${'$'}1")
            if [ -d "${'$'}path" ]; then
                BOSSTERM_CWD="${'$'}path" open_bossterm
                exit 0
            elif [ -f "${'$'}path" ]; then
                parent_dir=${'$'}(dirname "${'$'}path")
                BOSSTERM_CWD="${'$'}parent_dir" open_bossterm
                exit 0
            else
                echo "Error: Path not found: ${'$'}1"
                exit 1
            fi
            ;;
    esac
}

main "${'$'}@"
    """.trimIndent()
}
