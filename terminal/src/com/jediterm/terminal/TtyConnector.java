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
    // support old implementations not overriding this method
    resize(termWinSize, new Dimension(0, 0));
    // StackOverflowError is only possible if both resize(Dimension) and resize(Dimension,Dimension) are not overridden.
  }

  /**
   * @deprecated use {@link #resize(Dimension)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void resize(Dimension termWinSize, Dimension pixelSize) {
    // support old code that calls this method on new implementations (not overriding this deprecated method)
    resize(termWinSize);
  }

  String getName();

  int read(char[] buf, int offset, int length) throws IOException;

  void write(byte[] bytes) throws IOException;

  boolean isConnected();

  void write(String string) throws IOException;

  int waitFor() throws InterruptedException;

  boolean ready() throws IOException;
}
