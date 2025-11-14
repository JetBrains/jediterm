package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * @author traff
 */
public abstract class ProcessTtyConnector implements TtyConnector {
  protected final InputStream myInputStream;
  protected final OutputStream myOutputStream;
  protected final InputStreamReader myReader;
  protected final Charset myCharset;
  private final Process myProcess;
  private final @Nullable List<String> myCommandLine;

  public ProcessTtyConnector(@NotNull Process process, @NotNull Charset charset) {
    this(process, charset, null);
  }

  public ProcessTtyConnector(@NotNull Process process, @NotNull Charset charset, @Nullable List<String> commandLine) {
    myOutputStream = process.getOutputStream();
    myCharset = charset;
    myInputStream = process.getInputStream();
    myReader = new InputStreamReader(myInputStream, charset);
    myProcess = process;
    myCommandLine = commandLine;
  }

  @NotNull
  public Process getProcess() {
    return myProcess;
  }

  @Override
  public abstract String getName();

  public @Nullable List<String> getCommandLine() {
    return myCommandLine != null ? Collections.unmodifiableList(myCommandLine) : null;
  }

  public int read(char[] buf, int offset, int length) throws IOException {
    return myReader.read(buf, offset, length);
  }

  public void write(byte[] bytes) throws IOException {
    myOutputStream.write(bytes);
    myOutputStream.flush();
  }

  @Override
  public boolean isConnected() {
    return myProcess.isAlive();
  }

  @Override
  public void write(String string) throws IOException {
    write(string.getBytes(myCharset));
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

  @Override
  public boolean ready() throws IOException {
    return myReader.ready();
  }
}
