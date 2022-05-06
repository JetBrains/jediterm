package com.jediterm.terminal;

import com.jediterm.core.emulator.mouse.MouseButtonCodes;
import com.jediterm.core.emulator.mouse.MouseButtonModifierFlags;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.jediterm.core.awtCompat.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseWheelEvent;

@SuppressWarnings("unused")
public class AwtTransformers {
  @Contract("null -> null; !null -> new")
  public static @Nullable java.awt.Color toAwtColor(@Nullable Color color) {
    if (color == null) {
      return null;
    }
    return new java.awt.Color(color.getRGB(), true);
  }

  @Contract("null -> null; !null -> new")
  public static @Nullable Color fromAwtColor(@Nullable java.awt.Color color) {
    if (color == null) {
      return null;
    }
    return new Color(color.getRGB(), true);
  }

  @Contract("null -> null; !null -> new")
  public static @Nullable java.awt.Dimension toAwtDimension(@Nullable Dimension dimension) {
    if (dimension == null) {
      return null;
    }
    return new java.awt.Dimension(dimension.width, dimension.height);
  }

  @Contract("null -> null; !null -> new")
  public static @Nullable Dimension fromAwtDimension(@Nullable java.awt.Dimension dimension) {
    if (dimension == null) {
      return null;
    }
    return new Dimension(dimension.width, dimension.height);
  }

  @Contract("null -> null; !null -> new")
  public static @Nullable java.awt.Point toAwtPoint(@Nullable Point point) {
    if (point == null) {
      return null;
    }
    return new java.awt.Point(point.x, point.y);
  }

  @Contract("null -> null; !null -> new")
  public static @Nullable Point fromAwtPoint(@Nullable java.awt.Point point) {
    if (point == null) {
      return null;
    }
    return new Point(point.x, point.y);
  }

  @Contract("null -> null; !null -> new")
  public static MouseEvent fromAwtMouseEvent(@Nullable java.awt.event.MouseEvent event) {
    if (event == null) {
      return null;
    }
    int buttonCode = createButtonCode(event);
    int modifierKeys = getModifierKeys(event);
    return new MouseEvent(buttonCode, modifierKeys);
  }

  private static int createButtonCode(@NotNull java.awt.event.MouseEvent event) {
    // for mouse dragged, button is stored in modifiers
    if (SwingUtilities.isLeftMouseButton(event)) {
      return MouseButtonCodes.LEFT;
    } else if (SwingUtilities.isMiddleMouseButton(event)) {
      return MouseButtonCodes.MIDDLE;
    } else if (SwingUtilities.isRightMouseButton(event)) {
      return MouseButtonCodes.NONE; //we don't handle right mouse button as it used for the context menu invocation
    } else if (event instanceof MouseWheelEvent) {
      if (((MouseWheelEvent) event).getWheelRotation() > 0) {
        return MouseButtonCodes.SCROLLUP;
      } else {
        return MouseButtonCodes.SCROLLDOWN;
      }
    }
    return MouseButtonCodes.NONE;
  }

  private static int getModifierKeys(@NotNull java.awt.event.MouseEvent event) {
    int modifier = 0;
    if (event.isControlDown()) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG;
    }
    if (event.isShiftDown()) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG;
    }
    if ((event.getModifiersEx() & java.awt.event.InputEvent.META_MASK) != 0) {
      modifier |= MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG;
    }
    return modifier;
  }

}
