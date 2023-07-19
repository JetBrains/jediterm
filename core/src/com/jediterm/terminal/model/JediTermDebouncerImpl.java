package com.jediterm.terminal.model;

import com.jediterm.core.typeahead.Debouncer;
import com.jediterm.terminal.TerminalExecutorServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JediTermDebouncerImpl implements Debouncer {
  private final Object myLock = new Object();

  private final Runnable myRunnable;
  private final long myDelay;
  private final ScheduledExecutorService myScheduler;

  private TimerTask myTimerTask = null;

  public JediTermDebouncerImpl(@NotNull Runnable runnable, long delay,
                               @NotNull TerminalExecutorServiceManager executorServiceManager) {
    myRunnable = runnable;
    myDelay = delay;
    myScheduler = executorServiceManager.getSingleThreadScheduledExecutor();
  }

  @Override
  public void call() {
    synchronized (myLock) {
      if (myTimerTask != null) {
        myTimerTask.cancel();
      }

      myTimerTask = new TimerTask();
      myScheduler.schedule(myTimerTask, myDelay, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public void terminateCall() {
    synchronized (myLock) {
      if (myTimerTask != null) {
        myTimerTask.cancel();
        myTimerTask = null;
      }
    }
  }

  private class TimerTask implements Runnable {
    private final long myDueTime;
    private volatile boolean myIsActive = true;

    public TimerTask() {
      myDueTime = System.nanoTime() + myDelay;
    }

    public void cancel() {
      myIsActive = false;
    }

    public void run() {
      if (!myIsActive) {
        return;
      }

      long remaining = myDueTime - System.nanoTime();
      if (remaining > 0) { // Re-schedule task
        myScheduler.schedule(this, remaining, TimeUnit.NANOSECONDS);
      } else { // Mark as terminated and invoke callback
        myIsActive = false;
        myRunnable.run();
      }
    }
  }
}
