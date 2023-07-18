package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;

public interface TerminalExecutorServiceManager {
  /**
   * A single threaded executor service for fast tasks.
   */
  @NotNull ScheduledExecutorService getSingleThreadedExecutorService();

  void shutdownWhenAllExecuted();
}
