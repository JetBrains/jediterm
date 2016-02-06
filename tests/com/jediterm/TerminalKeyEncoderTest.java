package com.jediterm;

import com.jediterm.terminal.TerminalKeyEncoder;
import junit.framework.TestCase;
import org.junit.Assert;

import java.awt.event.InputEvent;

/**
 * @author traff
 */
public class TerminalKeyEncoderTest extends TestCase {
  
  public void testAltBackspace() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    Assert.assertArrayEquals(new byte[]{TerminalKeyEncoder.ESC, TerminalKeyEncoder.DEL}, terminalKeyEncoder.getCode('\b', InputEvent.ALT_MASK));
  }

  public void testAltLeft() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    Assert.assertArrayEquals(new byte[]{TerminalKeyEncoder.ESC, TerminalKeyEncoder.ESC, '[', 'D'}, terminalKeyEncoder.getCode(37, InputEvent.ALT_MASK));
  }

  public void testAltLeft_AltNoEscape() {
    TerminalKeyEncoder terminalKeyEncoder = new TerminalKeyEncoder();
    terminalKeyEncoder.setAltSendsEscape(false);
    
    // we expect to have '3' (corresponds to alt) before the final char in left key code
    Assert.assertArrayEquals(new byte[]{TerminalKeyEncoder.ESC, '[', '3', 'D'}, terminalKeyEncoder.getCode(37, InputEvent.ALT_MASK));
  }
}
