package com.jediterm.localterm;

import com.google.common.base.Joiner;
import jpty.JPty;
import jpty.Pty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author traff
 */
public class PtyProcess extends Process {
  private final Pty myPty;
  private int myExitCode;
  private boolean myFinished = false;
  private String[] myArguments;

  public PtyProcess(String command, String[] arguments) {
    myArguments = arguments;
    myPty = JPty.execInPTY(command, arguments);

    new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          myExitCode = myPty.waitFor();
        }
        catch (InterruptedException e) {
          myExitCode = -1;
        }
        myFinished = true;
      }
    }).start();
  }

  @Override
  public OutputStream getOutputStream() {
    return myPty.getOutputStream();
  }

  @Override
  public InputStream getInputStream() {
    return myPty.getInputStream();
  }

  @Override
  public InputStream getErrorStream() {
    return new NullInputStream();
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myPty.waitFor();
  }

  @Override
  public int exitValue() {
    if (!myFinished) {
      throw new IllegalThreadStateException("Process is not terminated");
    }
    return myExitCode;
  }

  @Override
  public void destroy() {
    try {
      myPty.close();
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public Pty getPty() {
    return myPty;
  }

  public boolean isFinished() {
    return myFinished;
  }

  public String getCommandLineString() {
    return Joiner.on(" ").join(myArguments);
  }
}
