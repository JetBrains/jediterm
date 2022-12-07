package com.jediterm;

import com.jediterm.terminal.TerminalKeyEncoder;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.typeahead.Ascii;
import junit.framework.TestCase;
import org.junit.Assert;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author traff
 */
public class TerminalKeyEncoderTest extends TestCase {
  
  public void testAltBackspace() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    Assert.assertArrayEquals(new byte[]{Ascii.ESC, Ascii.DEL}, terminalKeyEncoder.getCode('\b', InputEvent.ALT_MASK));
  }

  public void testAltLeft() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    byte[] expected = UIUtil.isMac ? new byte[]{Ascii.ESC, 'b'} : new byte[]{Ascii.ESC, '[', '1', ';', '3', 'D'};
    Assert.assertArrayEquals(expected, terminalKeyEncoder.getCode(KeyEvent.VK_LEFT, InputEvent.ALT_MASK));
  }

  public void testShiftLeft() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    byte[] expected = new byte[]{Ascii.ESC, '[', '1', ';', '2', 'D'};
    Assert.assertArrayEquals(expected, terminalKeyEncoder.getCode(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK));
  }

  public void testShiftLeftApplication() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    terminalKeyEncoder.arrowKeysApplicationSequences();
    byte[] expected = new byte[]{Ascii.ESC, '[', '1', ';', '2', 'D'};
    Assert.assertArrayEquals(expected, terminalKeyEncoder.getCode(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK));
  }

  public void testControlF1() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    byte[] expected = new byte[]{Ascii.ESC, '[', '1', ';', '5', 'P'};
    Assert.assertArrayEquals(expected, terminalKeyEncoder.getCode(KeyEvent.VK_F1, InputEvent.CTRL_MASK));
  }

  public void testControlF11() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    byte[] expected = new byte[]{Ascii.ESC, '[', '2', '3', ';', '5', '~'};
    Assert.assertArrayEquals(expected, terminalKeyEncoder.getCode(KeyEvent.VK_F11, InputEvent.CTRL_MASK));
  }
}
