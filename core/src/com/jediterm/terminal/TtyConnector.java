package com.jediterm.terminal;

import com.jediterm.core.compatibility.Dimension;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Interface to tty.
 */
public interface TtyConnector {
  boolean init(Questioner q);

  void close();

  default void resize(@NotNull Dimension termWinSize) {
    // support old implementations not overriding this method
    resize(new java.awt.Dimension(termWinSize.width, termWinSize.height));
  }

  String getName();

  int read(char[] buf, int offset, int length) throws IOException;

  void write(byte[] bytes) throws IOException;

  boolean isConnected();

  void write(String string) throws IOException;

  int waitFor() throws InterruptedException;

  boolean ready() throws IOException;

  /**
   * @deprecated use {@link #resize(Dimension)} instead
   */
  @Deprecated
  default void resize(@NotNull java.awt.Dimension termWinSize) {
    // support old implementations overriding neither `resize(Dimension)` nor this method
    resize(termWinSize, new java.awt.Dimension(0, 0));
  }

  /**
   * @deprecated use {@link #resize(Dimension)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void resize(java.awt.Dimension termWinSize, java.awt.Dimension pixelSize) {
    throw new IllegalStateException("This method shouldn't be called. " +
      getClass() + " should override TtyConnector.resize(com.jediterm.core.compatibility.Dimension)");
  }
}
