package com.jediterm.terminal;

import com.google.common.base.Ascii;

import java.util.HashMap;
import java.util.Map;

import static java.awt.event.KeyEvent.*;

/**
 * @author traff
 */
public class KeyEncoder {
  public static final int ESC = Ascii.ESC;
  public static final int DEL = Ascii.DEL;


  private final Map<Integer, byte[]> myKeyCodes = new HashMap<Integer, byte[]>();

  public KeyEncoder() {
    putCode(VK_ENTER, Ascii.CR);
    arrowKeysApplicationSequences();
    keypadApplicationSequences();
    putCode(VK_F1, ESC, 'O', 'P');
    putCode(VK_F2, ESC, 'O', 'Q');
    putCode(VK_F3, ESC, 'O', 'R');
    putCode(VK_F4, ESC, 'O', 'S');
    putCode(VK_F5, ESC, 'O', 't');
    putCode(VK_F6, ESC, '[', '1', '7', '~');
    putCode(VK_F7, ESC, '[', '1', '8', '~');
    putCode(VK_F8, ESC, '[', '1', '9', '~');
    putCode(VK_F9, ESC, '[', '2', '0', '~');
    putCode(VK_F10, ESC, '[', '2', '1', '~');
    putCode(VK_F11, ESC, '[', '2', '3', '~', ESC);
    putCode(VK_F12, ESC, '[', '2', '4', '~', Ascii.BS);
    putCode(VK_HOME, ESC, '[', 'H');
    putCode(VK_END, ESC, '[', 'F');
    //putCode(VK_PAGE_UP, ESC, '[', '5', '~');  don't work
    //putCode(VK_PAGE_DOWN, ESC, '[', '6', '~');
  }

  public void arrowKeysApplicationSequences() {
    putCode(VK_UP, ESC, 'O', 'A');
    putCode(VK_DOWN, ESC, 'O', 'B');
    putCode(VK_RIGHT, ESC, 'O', 'C');
    putCode(VK_LEFT, ESC, 'O', 'D');
  }

  public void arrowKeysAnsiCursorSequences() {
    putCode(VK_UP, ESC, '[', 'A');
    putCode(VK_DOWN, ESC, '[', 'B');
    putCode(VK_RIGHT, ESC, '[', 'C');
    putCode(VK_LEFT, ESC, '[', 'D');
  }

  void putCode(final int code, final int... bytesAsInt) {
    myKeyCodes.put(code, CharacterUtils.makeCode(bytesAsInt));
  }

  public byte[] getCode(final int key) {
    return myKeyCodes.get(key);
  }

  public void keypadApplicationSequences() {
    //0
    //1
    putCode(VK_KP_DOWN, ESC, 'O', 'r'); //2
    //3
    putCode(VK_KP_LEFT, ESC, 'O', 't'); //4
    //5
    putCode(VK_KP_RIGHT, ESC, 'O', 'v'); //6
    //7
    putCode(VK_KP_UP, ESC, 'O', 'x'); //8
    //9
  }

  public void normalKeypad() {
    //0
    //1
    putCode(VK_KP_DOWN, 2); //2
    //3
    putCode(VK_KP_LEFT, 4); //4
    //5
    putCode(VK_KP_RIGHT, 6); //6
    //7
    putCode(VK_KP_UP, 8); //8
    //9
  }
}
