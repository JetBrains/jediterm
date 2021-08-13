package com.jediterm.terminal.model;

public interface Debouncer {
  void call();

  void terminateCall();
}