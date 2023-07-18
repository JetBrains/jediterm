package com.jediterm.terminal.ui;

import com.jediterm.terminal.TerminalExecutorServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class JediTermExecutorServiceManager implements TerminalExecutorServiceManager {

  private final ScheduledExecutorService myExecutor;

  public JediTermExecutorServiceManager() {
    myExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      private final AtomicInteger myThreadNumber = new AtomicInteger(0);
      @Override
      public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, "JediTerm-" + myThreadNumber.getAndIncrement());
        if (t.isDaemon()) {
          t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
          t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
      }
    });
  }

  @Override
  public @NotNull ScheduledExecutorService getSingleThreadedExecutorService() {
    return myExecutor;
  }

  @Override
  public void shutdownWhenAllExecuted() {
    if (!myExecutor.isShutdown()) {
      myExecutor.execute(myExecutor::shutdown);
    }
  }
}
