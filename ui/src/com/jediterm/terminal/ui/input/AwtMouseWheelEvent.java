package com.jediterm.terminal.ui.input;

import com.jediterm.core.input.MouseWheelEvent;
import org.jetbrains.annotations.NotNull;

public final class AwtMouseWheelEvent extends MouseWheelEvent {
  private final java.awt.event.MouseWheelEvent myAwtMouseWheelEvent;

  public AwtMouseWheelEvent(@NotNull java.awt.event.MouseWheelEvent awtMouseWheelEvent) {
    super(AwtMouseEvent.createButtonCode(awtMouseWheelEvent), AwtMouseEvent.createButtonCode(awtMouseWheelEvent));
    myAwtMouseWheelEvent = awtMouseWheelEvent;
  }

  @Override
  public String toString() {
    return myAwtMouseWheelEvent.toString();
  }
}
