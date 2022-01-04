package com.intellij.openapi;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class Suppliers {

  private Suppliers() {}

  public static <T> Supplier<T> memoize(@NotNull Supplier<T> delegate) {
    AtomicReference<T> value = new AtomicReference<>();
    return () -> {
      T val = value.get();
      if (val == null) {
        synchronized (value) {
          val = value.get();
          if (val == null) {
            val = Objects.requireNonNull(delegate.get());
            value.set(val);
          }
        }
      }
      return val;
    };
  }


}
