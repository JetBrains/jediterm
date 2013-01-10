package com.jediterm;

import java.awt.Dimension;
import java.io.IOException;

public interface Tty {
  boolean init(Questioner q);

  void close();

  void resize(Dimension termSize, Dimension pixelSize);

  String getName();

  int read(byte[] buf, int offset, int length) throws IOException;

  void write(byte[] bytes) throws IOException;

  boolean isConnected();
}
