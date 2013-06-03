package com.jediterm.pty;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import jpty.JPty;
import jpty.Pty;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * @author traff
 */
public class PtyProcess extends Process {
  private static final Logger LOG = Logger.getLogger(PtyProcess.class);

  private final Pty myPty;
  private int myExitCode;
  private boolean myFinished = false;
  private String[] myArguments;
  private Semaphore myWaitForSemaphore = new Semaphore(0);

  public PtyProcess(String command, String[] arguments) {
    this(command, arguments, new String[0]);
  }

  public PtyProcess(String command, String[] arguments, Map<String, String> environment) {
    this(command, arguments, toStringArray(environment));
  }

  private static String[] toStringArray(Map<String, String> environment) {
    List<String> list = Lists.transform(Lists.newArrayList(environment.entrySet()), new Function<Map.Entry<String, String>, String>() {
      @Override
      public String apply(Map.Entry<String, String> entry) {
        return entry.getKey() + "=" + entry.getValue();
      }
    });
    return list.toArray(new String[list.size()]);
  }

  public PtyProcess(String command, String[] arguments, String[] environment) {
    myArguments = arguments;
    myPty = JPty.execInPTY(command, arguments, environment);

    startThread(new Runnable() {
      @Override
      public void run() {
        try {
          myExitCode = myPty.waitFor();
        }
        catch (InterruptedException e) {
          LOG.info("WaitFor thread interrupted: " + e.getMessage(), e);
          myExitCode = -1;
        }
        myFinished = true;
        myWaitForSemaphore.release();
      }
    });
  }

  protected void startThread(Runnable runnable) {
    new Thread(runnable).start();
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
    myWaitForSemaphore.acquire();
    return exitValue();
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
