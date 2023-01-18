package com.jediterm.terminal.ui.input;

import com.jediterm.core.input.MouseEvent;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class AwtMouseEvent extends MouseEvent {
  private final java.awt.event.MouseEvent myAwtMouseEvent;

  public AwtMouseEvent(@NotNull java.awt.event.MouseEvent awtMouseEvent) {
    super(createButtonCode(awtMouseEvent), getModifierKeys(awtMouseEvent));
    myAwtMouseEvent = awtMouseEvent;
  }

  @Override
  public String toString() {
    return myAwtMouseEvent.toString();
  }

  static int createButtonCode(@NotNull java.awt.event.MouseEvent awtMouseEvent) {
    // for mouse dragged, button is stored in modifiers
    if (SwingUtilities.isLeftMouseButton(awtMouseEvent)) {
      return MouseButtonCodes.LEFT;
    } else if (SwingUtilities.isMiddleMouseButton(awtMouseEvent)) {
      return MouseButtonCodes.MIDDLE;
    } else if (SwingUtilities.isRightMouseButton(awtMouseEvent)) {
      return MouseButtonCodes.NONE; //we don't handle right mouse button as it used for the context menu invocation
    } else if (awtMouseEvent instanceof java.awt.event.MouseWheelEvent) {
      if (((java.awt.event.MouseWheelEvent) awtMouseEvent).getWheelRotation() > 0) {
        return MouseButtonCodes.SCROLLUP;
      } else {
        return MouseButtonCodes.SCROLLDOWN;
      }
    }
    return MouseButtonCodes.NONE;
  }

  static int getModifierKeys(@NotNull java.awt.event.MouseEvent awtMouseEvent) {
    int modifier = 0;
    if (awtMouseEvent.isControlDown()) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG;
    }
    if (awtMouseEvent.isShiftDown()) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG;
    }
    if (awtMouseEvent.isMetaDown()) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG;
    }
    return modifier;
  }
}
