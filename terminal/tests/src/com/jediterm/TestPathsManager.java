package com.jediterm;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author traff
 */
public class TestPathsManager {
  public static @NotNull Path getTestDataPath() {
    return Path.of("tests/resources/testData").toAbsolutePath().normalize();
  }
}
