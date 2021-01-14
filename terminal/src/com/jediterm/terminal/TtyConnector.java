package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

/**
 * Interface to tty.
 */
public interface TtyConnector {
  boolean init(Questioner q);

  void close();

  default void resize(@NotNull Dimension termWinSize) {
    resize(termWinSize, termWinSize);
  }

  /**
   * @deprecated use {@link #resize(Dimension)} instead
   */
  @Deprecated
  default void resize(Dimension termWinSize, Dimension pixelSize) {}

  String getName();

  int read(char[] buf, int offset, int length) throws IOException;

  void write(byte[] bytes) throws IOException;

  boolean isConnected();

  void write(String string) throws IOException;

  int waitFor() throws InterruptedException;
}
