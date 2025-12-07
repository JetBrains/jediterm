package ai.rever.bossterm.compose.update

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Generates platform-specific update helper scripts.
 *
 * These scripts run AFTER the main app quits and handle:
 * - Waiting for app to fully terminate
 * - Performing the actual installation
 * - Launching the updated app
 * - Cleaning up the script itself
 */
object UpdateScriptGenerator {

    /**
     * Escape a string for safe use as a shell argument.
     */
    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    /**
     * Escape a string for safe use in Windows batch files.
     */
    private fun escapeWindowsArg(arg: String): String {
        val escapedQuotes = arg.replace("\"", "\"\"")
        val escapedPercent = escapedQuotes.replace("%", "%%")
        return "\"$escapedPercent\""
    }

    /**
     * Validate a path for security concerns.
     */
    private fun validatePath(path: String, description: String) {
        if (path.contains('\u0000')) {
            throw SecurityException("$description contains null byte - possible directory traversal attack")
        }
        if (path.contains('$') || path.contains('`')) {
            throw SecurityException("$description contains shell metacharacters - possible command injection")
        }
        if (path.contains('\n') || path.contains('\r')) {
            throw SecurityException("$description contains newline characters - possible script injection")
        }
        if (path.contains("..")) {
            throw SecurityException("$description contains path traversal sequence '..' - rejected for security")
        }
        if (path.contains(";") || path.contains("|") || path.contains("&")) {
            throw SecurityException("$description contains command separator characters - rejected for security")
        }
        if (path.contains("%") || path.contains("^") || path.contains("!")) {
            throw SecurityException("$description contains Windows batch metacharacters - rejected for security")
        }
    }

    /**
     * Generate macOS update script.
     */
    fun generateMacOSUpdateScript(
        dmgPath: String,
        targetAppPath: String,
        appPid: Long
    ): File {
        validatePath(dmgPath, "DMG path")
        validatePath(targetAppPath, "Target app path")

        val escapedDmgPath = escapeShellArg(dmgPath)
        val escapedTargetAppPath = escapeShellArg(targetAppPath)

        println("🔒 Security: Validated and escaped update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "bossterm-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_bossterm_${System.currentTimeMillis()}.sh")

        val script = """
            #!/bin/bash

            # BossTerm Update Helper Script

            echo "BossTerm Update Helper started"
            echo "Waiting for BossTerm to quit (PID: $appPid)..."

            WAIT_COUNT=0
            MAX_WAIT=30
            while kill -0 $appPid 2>/dev/null; do
                sleep 1
                WAIT_COUNT=${'$'}((WAIT_COUNT + 1))
                if [ ${'$'}WAIT_COUNT -ge ${'$'}MAX_WAIT ]; then
                    echo "Timeout waiting for app to quit"
                    exit 1
                fi
            done

            echo "BossTerm has quit. Starting installation..."
            sleep 2

            echo "Mounting DMG: $escapedDmgPath"
            hdiutil attach $escapedDmgPath -nobrowse -quiet
            if [ ${'$'}? -ne 0 ]; then
                echo "Failed to mount DMG"
                open $escapedDmgPath
                exit 1
            fi

            VOLUME=${'$'}(ls -d /Volumes/BossTerm* 2>/dev/null | head -n 1)
            if [ -z "${'$'}VOLUME" ]; then
                echo "Could not find mounted BossTerm volume"
                open $escapedDmgPath
                exit 1
            fi

            echo "Found mounted volume: ${'$'}VOLUME"

            APP_BUNDLE=${'$'}(find "${'$'}VOLUME" -name "*.app" -maxdepth 1 | grep -i bossterm | head -n 1)
            if [ -z "${'$'}APP_BUNDLE" ]; then
                echo "Could not find BossTerm.app in volume"
                hdiutil detach "${'$'}VOLUME" -quiet
                open $escapedDmgPath
                exit 1
            fi

            echo "Found app bundle: ${'$'}APP_BUNDLE"

            echo "Removing old BossTerm: $escapedTargetAppPath"
            if [ -d $escapedTargetAppPath ]; then
                rm -rf $escapedTargetAppPath
                if [ ${'$'}? -ne 0 ]; then
                    echo "Failed to remove old app"
                    hdiutil detach "${'$'}VOLUME" -quiet
                    exit 1
                fi
            fi

            echo "Installing new BossTerm..."
            cp -R "${'$'}APP_BUNDLE" $escapedTargetAppPath
            if [ ${'$'}? -ne 0 ]; then
                echo "Failed to copy new app"
                hdiutil detach "${'$'}VOLUME" -quiet
                exit 1
            fi

            echo "Installation successful!"
            hdiutil detach "${'$'}VOLUME" -quiet

            echo "Launching new BossTerm..."
            open $escapedTargetAppPath

            sleep 2
            rm -f "${'$'}0"
            exit 0
        """.trimIndent()

        scriptFile.writeText(script)
        makeExecutable(scriptFile)

        println("Generated macOS update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Generate Windows update script.
     */
    fun generateWindowsUpdateScript(
        msiPath: String,
        appPid: Long
    ): File {
        validatePath(msiPath, "MSI path")
        val escapedMsiPath = escapeWindowsArg(msiPath)

        println("🔒 Security: Validated and escaped Windows update script parameters")

        val tempDir = File(System.getProperty("java.io.tmpdir"), "bossterm-updater")
        tempDir.mkdirs()

        val scriptFile = File(tempDir, "update_bossterm_${System.currentTimeMillis()}.bat")

        val script = """
            @echo off
            REM BossTerm Update Helper Script

            echo BossTerm Update Helper started
            echo Waiting for BossTerm to quit (PID: $appPid)...

            :waitloop
            tasklist /FI "PID eq $appPid" 2>NUL | find /I /N "$appPid">NUL
            if "%ERRORLEVEL%"=="0" (
                timeout /t 1 /nobreak >NUL
                goto waitloop
            )

            echo BossTerm has quit. Starting installation...
            timeout /t 2 /nobreak >NUL

            echo Installing update...
            msiexec /i $escapedMsiPath /quiet /norestart

            if %ERRORLEVEL% NEQ 0 (
                echo Installation failed. Opening installer manually...
                start "" $escapedMsiPath
            ) else (
                echo Installation successful!
            )

            timeout /t 2 /nobreak >NUL
            del "%~f0"
        """.trimIndent()

        scriptFile.writeText(script)

        println("Generated Windows update script: ${scriptFile.absolutePath}")
        return scriptFile
    }

    /**
     * Launch the update script in the background.
     */
    fun launchScript(scriptFile: File) {
        try {
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("mac") || os.contains("darwin") || os.contains("linux") -> {
                    listOf("nohup", "sh", scriptFile.absolutePath)
                }
                os.contains("win") -> {
                    listOf("cmd", "/c", "start", "/b", scriptFile.absolutePath)
                }
                else -> {
                    listOf("sh", scriptFile.absolutePath)
                }
            }

            println("Launching update script: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)
            processBuilder.start()

            println("✅ Update script launched successfully")
        } catch (e: Exception) {
            println("❌ Failed to launch update script: ${e.message}")
            throw e
        }
    }

    /**
     * Make a file executable (Unix-like systems).
     */
    fun makeExecutable(file: File) {
        try {
            val path = file.toPath()
            val permissions = mutableSetOf<PosixFilePermission>()
            permissions.add(PosixFilePermission.OWNER_READ)
            permissions.add(PosixFilePermission.OWNER_WRITE)
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
            permissions.add(PosixFilePermission.GROUP_READ)
            permissions.add(PosixFilePermission.GROUP_EXECUTE)
            permissions.add(PosixFilePermission.OTHERS_READ)
            permissions.add(PosixFilePermission.OTHERS_EXECUTE)

            Files.setPosixFilePermissions(path, permissions)
        } catch (e: Exception) {
            // Not a POSIX system (Windows) - ignore
        }
    }
}
