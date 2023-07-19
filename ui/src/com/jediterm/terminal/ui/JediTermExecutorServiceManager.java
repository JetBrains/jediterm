package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalExecutorServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class JediTermExecutorServiceManager implements TerminalExecutorServiceManager {

  private final ScheduledExecutorService mySingleThreadScheduledExecutor;
  private final ExecutorService myUnboundedExecutor;

  public JediTermExecutorServiceManager() {
    mySingleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor(new JediTermThreadFactory("JediTerm-fast-job-"));
    myUnboundedExecutor = Executors.newCachedThreadPool(new JediTermThreadFactory("JediTerm-"));
  }

  @Override
  public @NotNull ScheduledExecutorService getSingleThreadScheduledExecutor() {
    return mySingleThreadScheduledExecutor;
  }

  @Override
  public @NotNull ExecutorService getUnboundedExecutorService() {
    return myUnboundedExecutor;
  }

  @Override
  public void shutdownWhenAllExecuted() {
    if (!mySingleThreadScheduledExecutor.isShutdown()) {
      mySingleThreadScheduledExecutor.execute(mySingleThreadScheduledExecutor::shutdown);
    }
    myUnboundedExecutor.shutdown();
  }

  private static class JediTermThreadFactory implements ThreadFactory {
    private final String myThreadNamePrefix;
    private final AtomicInteger myThreadNumber = new AtomicInteger(0);

    JediTermThreadFactory(@NotNull String threadNamePrefix) {
      myThreadNamePrefix = threadNamePrefix;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
      Thread t = new Thread(r, myThreadNamePrefix + myThreadNumber.getAndIncrement());
      if (t.isDaemon()) {
        t.setDaemon(false);
      }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY);
      }
      return t;
    }
  }
}
