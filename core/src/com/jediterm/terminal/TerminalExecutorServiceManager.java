package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public interface TerminalExecutorServiceManager {
  /**
   * A single threaded executor service for fast tasks.
   */
  @NotNull ScheduledExecutorService getSingleThreadScheduledExecutor();

  /**
   * A general purpose executor service, e.g. to run emulator.
   */
  @NotNull ExecutorService getUnboundedExecutorService();

  void shutdownWhenAllExecuted();
}
