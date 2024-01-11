package com.jediterm.core

internal enum class Platform {
  Windows,

  @Suppress("EnumEntryName")
  macOS,
  Linux,
  Other;

  companion object {
    private var CURRENT: Platform = detectCurrent()

    @JvmStatic
    fun current(): Platform = CURRENT

    @JvmStatic
    fun isWindows(): Boolean = CURRENT == Windows

    @JvmStatic
    fun isMacOS(): Boolean = CURRENT == macOS

    private fun detectCurrent(): Platform {
      val osName = System.getProperty("os.name").lowercase()
      if (osName.startsWith("windows")) return Windows
      if (osName.startsWith("mac")) return macOS
      if (osName.startsWith("linux")) return Linux
      return Other
    }
  }
}
