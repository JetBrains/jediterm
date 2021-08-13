package com.jediterm.typeahead;

public interface Debouncer {
  void call();

  void terminateCall();
}