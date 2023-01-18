package com.jediterm.core.input;

public class MouseEvent {
  private final int myButtonCode;
  private final int myModifierKeys;

  public MouseEvent(int buttonCode, int modifierKeys) {
    myButtonCode = buttonCode;
    myModifierKeys = modifierKeys;
  }

  public int getButtonCode() {
    return myButtonCode;
  }

  public int getModifierKeys() {
    return myModifierKeys;
  }
}
