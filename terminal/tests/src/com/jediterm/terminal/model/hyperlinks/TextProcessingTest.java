package com.jediterm.terminal.model.hyperlinks;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.util.CharUtils;
import com.jediterm.util.BackBufferDisplay;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TextProcessingTest extends TestCase {

  private HyperlinkStyle myHyperlinkStyle;
  private JediTerminal myTerminal;
  private TerminalTextBuffer myTerminalTextBuffer;
  private TextStyle myDefaultStyle;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StyleState state = new StyleState();
    myDefaultStyle = state.getCurrent();
    TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.awt(Color.BLUE), TerminalColor.WHITE);
    myHyperlinkStyle = new HyperlinkStyle(hyperlinkTextStyle, new LinkInfo(() -> {}));
    TextProcessing textProcessing = new TextProcessing(hyperlinkTextStyle, HyperlinkStyle.HighlightMode.ALWAYS);
    textProcessing.addHyperlinkFilter(new TestFilter());
    myTerminalTextBuffer = new TerminalTextBuffer(100, 5, state, textProcessing);
    textProcessing.setTerminalTextBuffer(myTerminalTextBuffer);
    myTerminal = new JediTerminal(new BackBufferDisplay(myTerminalTextBuffer), myTerminalTextBuffer, state);
  }

  public void testBasic() {
    String link = TestFilter.formatLink("hello");
    myTerminal.writeString(link);
    assertEquals(
        Collections.singletonList(new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link))),
        myTerminalTextBuffer.getLine(0).getEntries()
    );
  }

  public void testErase() {
    String str = "<[-------- PROGRESS 1ms";
    myTerminal.writeString(str);
    assertEquals(
        Collections.singletonList(new TerminalLine.TextEntry(myDefaultStyle, new CharBuffer(str))),
        myTerminalTextBuffer.getLine(0).getEntries()
    );
    myTerminal.cursorHorizontalAbsolute(0);
    String link = TestFilter.formatLink("simple");
    myTerminal.writeString(link);
    assertEquals(
        Arrays.asList(
            new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link + "GRESS")),
            new TerminalLine.TextEntry(myDefaultStyle, new CharBuffer(" 1ms"))
        ),
        myTerminalTextBuffer.getLine(0).getEntries()
    );
    myTerminal.eraseInLine(0);
    assertEquals(
        Arrays.asList(
            new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link)),
            new TerminalLine.TextEntry(myDefaultStyle, new CharBuffer(CharUtils.NUL_CHAR, str.length() - link.length()))
        ),
        myTerminalTextBuffer.getLine(0).getEntries()
    );
  }

  private static void assertEquals(@NotNull List<TerminalLine.TextEntry> expected,
                                   @NotNull List<TerminalLine.TextEntry> actual) {
    assertEquals(expected.size(), actual.size());
    for (int i = 0; i < expected.size(); i++) {
      assertEqualTextEntries(expected.get(i), actual.get(i));
    }
  }

  private static void assertEqualTextEntries(@NotNull TerminalLine.TextEntry expected,
                                             @NotNull TerminalLine.TextEntry actual) {
    assertEquals(expected.getText().toString(), actual.getText().toString());
    assertEquals(expected.getStyle(), actual.getStyle());
  }
}
