package com.jediterm.terminal.model;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Debouncer {
  private static final ScheduledExecutorService myScheduler = Executors.newScheduledThreadPool(1);
  private final Object myLock = new Object();

  private final Runnable myRunnable;
  private final long myDelay;

  private TimerTask myTimerTask = null;

  public Debouncer(Runnable runnable, long delay) {
    myRunnable = runnable;
    myDelay = delay;
  }

  public void call() {
    synchronized (myLock) {
      if (myTimerTask != null) {
        myTimerTask.cancel();
      }

      myTimerTask = new TimerTask();
      myScheduler.schedule(myTimerTask, myDelay, TimeUnit.NANOSECONDS);
    }
  }

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
    private boolean myIsActive = true;

    public TimerTask() {
      myDueTime = System.nanoTime() + myDelay;
    }

    public void cancel() {
      myIsActive = false;
    }

    public void run() {
      synchronized (myLock) {
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
}
