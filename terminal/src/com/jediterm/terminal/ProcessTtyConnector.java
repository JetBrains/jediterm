package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author traff
 */
public abstract class ProcessTtyConnector implements TtyConnector {
  protected final InputStream myInputStream;
  protected final OutputStream myOutputStream;
  protected final InputStreamReader myReader;
  protected Charset myCharset;
  private Dimension myPendingTermSize;
  private final Process myProcess;

  public ProcessTtyConnector(@NotNull Process process, @NotNull Charset charset) {
    myOutputStream = process.getOutputStream();
    myCharset = charset;
    myInputStream = process.getInputStream();
    myReader = new InputStreamReader(myInputStream, charset);
    myProcess = process;
  }

  @NotNull
  public Process getProcess() {
    return myProcess;
  }

  @Override
  public void resize(@NotNull Dimension termWinSize) {
    setPendingTermSize(termWinSize);
    if (isConnected()) {
      resizeImmediately();
      setPendingTermSize(null);
    }
  }

  protected abstract void resizeImmediately();

  @Override
  public abstract String getName();

  public int read(char[] buf, int offset, int length) throws IOException {
    return myReader.read(buf, offset, length);
    //return myInputStream.read(buf, offset, length);
  }

  public void write(byte[] bytes) throws IOException {
    myOutputStream.write(bytes);
    myOutputStream.flush();
  }

  @Override
  public abstract boolean isConnected();

  @Override
  public void write(String string) throws IOException {
    write(string.getBytes(myCharset));
  }

  protected void setPendingTermSize(@Nullable Dimension pendingTermSize) {
    myPendingTermSize = pendingTermSize;
  }

  protected @Nullable Dimension getPendingTermSize() {
    return myPendingTermSize;
  }

  /**
   * @deprecated don't use it (pixel size is not used anymore)
   */
  @Deprecated
  protected Dimension getPendingPixelSize() {
    return myPendingTermSize;
  }

  @Override
  public boolean init(Questioner q) {
    return isConnected();
  }

  @Override
  public void close() {
    myProcess.destroy();
    try {
      myOutputStream.close();
    }
    catch (IOException ignored) { }
    try {
      myInputStream.close();
    }
    catch (IOException ignored) { }
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myProcess.waitFor();
  }
}
