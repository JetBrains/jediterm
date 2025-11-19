package com.jediterm.core

import java.util.*

enum class Platform {
    Windows,
    macOS,
    Linux,
    Unknown;

    companion object {
        private val current: Platform = detectPlatform()

        private fun detectPlatform(): Platform {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            if (osName.contains("win")) {
                return Platform.Windows
            } else if (osName.contains("mac")) {
                return Platform.macOS
            } else if (osName.contains("nux") || osName.contains("nix")) {
                return Platform.Linux
            } else {
                return Platform.Unknown
            }
        }

        @JvmStatic
        fun current(): Platform {
            return current
        }

        val isWindows: Boolean
            get() = current() == Platform.Windows

        @JvmStatic
        val isMacOS: Boolean
            get() = current() == Platform.macOS

        val isLinux: Boolean
            get() = current() == Platform.Linux
    }
}
