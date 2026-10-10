package com.jediterm.terminal.ui;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import junit.framework.TestCase;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class CursorPaintTest extends TestCase {
  public void testCursorMoveDuringPaintDoesNotEraseCharacter() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      DefaultSettingsProvider settings = new DefaultSettingsProvider();
      StyleState style = new StyleState();
      style.setDefaultStyle(settings.getDefaultStyle());
      TerminalTextBuffer buffer = new TerminalTextBuffer(24, 2, style);
      RacingPanel panel = new RacingPanel(settings, buffer, style);
      JediTerminal terminal = new JediTerminal(panel, buffer, style);
      panel.initFont();
      panel.setSize(panel.getPixelWidth(), panel.getPixelHeight());
      terminal.writeCharacters("blah blah");
      terminal.cursorShape(CursorShape.STEADY_BLOCK);
      // Interleave a cursor-only output update after the blank under column 10 has
      // been sampled, before the cursor is drawn. No text or input bytes are lost.
      panel.duringCursorPaint = () -> {
        Thread output = new Thread(() -> terminal.cursorBackward(7), "offline-cursor-output");
        output.start();
        try {
          output.join(5000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
        assertFalse("The cursor-only output update should complete during paint", output.isAlive());
      };
      BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = image.createGraphics();
      try {
        panel.paintComponent(graphics);
      } finally {
        graphics.dispose();
      }
      assertEquals("blah blah", buffer.getLine(0).getText());
      assertEquals(3, terminal.getCursorX());
      assertTrue("The first a must still have glyph pixels, rather than a solid blank cell",
        panel.cellColors(image, 2).size() > 1);
    });
  }

  private static final class RacingPanel extends TerminalPanel {
    Runnable duringCursorPaint;

    RacingPanel(DefaultSettingsProvider settings, TerminalTextBuffer buffer, StyleState style) {
      super(settings, buffer, style);
    }

    @Override
    public boolean isFocusOwner() {
      Runnable action = duringCursorPaint;
      duringCursorPaint = null;
      if (action != null) action.run();
      return true;
    }

    Set<Integer> cellColors(BufferedImage image, int column) {
      Set<Integer> colors = new HashSet<>();
      int left = getInsetX() + column * myCharSize.width;
      for (int y = 1; y < myCharSize.height - 1; y++) {
        for (int x = left + 1; x < left + myCharSize.width - 1; x++) {
          colors.add(image.getRGB(x, y));
        }
      }
      return colors;
    }
  }
}
