package com.jediterm.core;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum Platform {
  Windows,
  OS2,
  Mac,
  Linux,
  Other;

  private static @NotNull String getOsNameLowerCase() {
    return System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
  }

  public static @NotNull Platform current() {
    String osName = getOsNameLowerCase();
    if (osName.startsWith("windows")) return Platform.Windows;
    if (osName.startsWith("os/2") || osName.startsWith("os2")) return Platform.OS2;
    if (osName.startsWith("mac")) return Platform.Mac;
    if (osName.startsWith("linux")) return Platform.Linux;
    return Platform.Other;
  }
}
