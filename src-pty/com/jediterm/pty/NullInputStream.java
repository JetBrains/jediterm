package com.jediterm.pty;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author traff
 */
public final class NullInputStream extends InputStream {
  @Override
  public int read() throws IOException {
    return 0;
  }


  @Override
  public int available() throws IOException {
    return 0;
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public synchronized void reset() throws IOException {
  }

  @Override
  public int read(byte[] bytes) throws IOException {
    return 0;
  }

  @Override
  public int read(byte[] bytes, int i, int i2) throws IOException {
    return 0;
  }

  @Override
  public long skip(long l) throws IOException {
    return 0;
  }

  @Override
  public synchronized void mark(int i) {
  }

  @Override
  public boolean markSupported() {
    return false;
  }
}
