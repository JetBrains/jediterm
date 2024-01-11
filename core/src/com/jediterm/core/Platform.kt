package com.jediterm.core;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum Platform {
  Windows,
  OS2,
  Mac,
  Linux,
  Other;

  private static Platform cachedPlatform;

  public static @NotNull Platform current() {
    Platform current = cachedPlatform;
    if (current == null) {
      current = detectCurrent();
      cachedPlatform = current;
    }
    return current;
  }

  public static boolean isWindows() {
    return Platform.current() == Platform.Windows;
  }

  public static boolean isMac() {
    return Platform.current() == Platform.Mac;
  }

  private static @NotNull Platform detectCurrent() {
    String osName = getOsNameLowerCase();
    if (osName.startsWith("windows")) return Platform.Windows;
    if (osName.startsWith("os/2") || osName.startsWith("os2")) return Platform.OS2;
    if (osName.startsWith("mac")) return Platform.Mac;
    if (osName.startsWith("linux")) return Platform.Linux;
    return Platform.Other;
  }

  private static @NotNull String getOsNameLowerCase() {
    return System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
  }
}
