package com.jediterm.app

internal fun isWindows(): Boolean {
  return System.getProperty("os.name").lowercase().startsWith("windows")
}

internal fun isMacOS(): Boolean {
  return System.getProperty("os.name").lowercase().startsWith("mac")
}
