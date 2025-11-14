package com.jediterm.core;

public enum Platform {
    Windows,
    macOS,
    Linux,
    Unknown;

    private static Platform current = detectPlatform();

    private static Platform detectPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return Windows;
        } else if (osName.contains("mac")) {
            return macOS;
        } else if (osName.contains("nux") || osName.contains("nix")) {
            return Linux;
        } else {
            return Unknown;
        }
    }

    public static Platform current() {
        return current;
    }

    public static boolean isWindows() {
        return current() == Windows;
    }

    public static boolean isMacOS() {
        return current() == macOS;
    }

    public static boolean isLinux() {
        return current() == Linux;
    }
}
