package com.jediterm.core.typeahead;

public interface Debouncer {
  void call();

  void terminateCall();
}