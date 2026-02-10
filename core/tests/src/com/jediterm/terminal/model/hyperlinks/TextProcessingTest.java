package com.jediterm.terminal.model.hyperlinks;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.util.CharUtils;
import com.jediterm.util.TestSession;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TextProcessingTest extends TestCase {

  private HyperlinkStyle myHyperlinkStyle;
  private TestSession mySession;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySession = new TestSession(100, 5);
    TextStyle hyperlinkTextStyle = new TextStyle(TerminalColor.fromColor(TestSession.BLUE), TerminalColor.WHITE);
    myHyperlinkStyle = new HyperlinkStyle(hyperlinkTextStyle, new LinkInfo(() -> {}));
  }

  private @NotNull JediTerminal getTerminal() {
    return mySession.getTerminal();
  }

  private @NotNull TerminalTextBuffer getTextBuffer() {
    return mySession.getTerminalTextBuffer();
  }

  public void testBasic() throws IOException {
    mySession.getTextProcessing().addAsyncHyperlinkFilter(new TestFilter(true));
    String link = TestFilter.formatLink("hello");
    mySession.process(link);
    assertEquals(
        Collections.singletonList(new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link))),
        mySession.getTerminalTextBuffer().getLine(0).getEntries()
    );
  }

  public void testErase() throws IOException {
    mySession.getTextProcessing().addAsyncHyperlinkFilter(new TestFilter(true));
    String str = "<[-------- PROGRESS 1ms";
    mySession.process(str);
    assertEquals(
        Collections.singletonList(new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(str))),
        getTextBuffer().getLine(0).getEntries()
    );
    mySession.process("\u001b[1;1H"); // move cursor to the beginning of the line
    String link = TestFilter.formatLink("simple");
    mySession.process(link);
    assertEquals(
        Arrays.asList(
            new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link + "GRESS")),
            new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(" 1ms"))
        ),
        getTextBuffer().getLine(0).getEntries()
    );
    getTerminal().eraseInLine(0);
    assertEquals(
        Arrays.asList(
            new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(link)),
            new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(CharUtils.NUL_CHAR, str.length() - link.length()))
        ),
        getTextBuffer().getLine(0).getEntries()
    );
  }

  public void testOscLink() throws IOException {
    mySession.getTerminal().setUrlHyperlinkFilter(new TestSyncFilter());
    mySession.process("\u001B]8;;" + TestFilter.formatLink("foo") + "\u001B\\Foo link\u001B]8;;\u001B\\ Some text 1");
    assertEquals(
      Arrays.asList(
        new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("Foo link")),
        new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(" Some text 1"))
      ),
      getTextBuffer().getLine(0).getEntries()
    );
    mySession.process("\r\n");
    mySession.process("\u001B]8;;" + TestFilter.formatLink("bar") + "\u0007Bar link\u001B]8;;\u0007 Some text 2");
    assertEquals(
      Arrays.asList(
        new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("Bar link")),
        new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(" Some text 2"))
      ),
      getTextBuffer().getLine(1).getEntries()
    );
  }

  public void testLinkAfterHorizontalResize() throws IOException {
    TestFilter testFilter = new TestFilter(false);
    mySession.getTextProcessing().addAsyncHyperlinkFilter(testFilter);
    mySession.process("1_2_3_4_5_6_7_8_9 1_2_3_4_5_6_7_8_9 " + TestFilter.formatLink("foo"));
    mySession.getTerminal().resize(new TermSize(10, 5), RequestOrigin.User);
    testFilter.completeAll(); // produce links before the resize (as width has changed, hyperlinks will be rescheduled)
    testFilter.completeAll(); // produce links after the resize 

/*
1_2_3_4_5_
6_7_8_9 1_
2_3_4_5_6_
7_8_9 my_l
ink:foo   
*/

    assertEquals(
      List.of(
        new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer("7_8_9 ")),
        new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("my_l"))
      ),
      getTextBuffer().getLine(3).getEntries()
    );
    assertEquals(
      List.of(new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("ink:foo"))),
      getTextBuffer().getLine(4).getEntries()
    );
  }

  public void testLinkAfterHorizontalResizeAndMoveToHistoryBuffer() throws IOException {
    TestFilter testFilter = new TestFilter(false);
    mySession.getTextProcessing().addAsyncHyperlinkFilter(testFilter);
    mySession.process("1_2_3_4_5_6_7_8_9 " + TestFilter.formatLink("foo") + " a-b-c-d-e-f-g-h-i-j-k-l-m-n-o");
    mySession.getTerminal().resize(new TermSize(5, 5), RequestOrigin.User);
    testFilter.completeAll(); // produce links before the resize
    testFilter.completeAll(); // produce links after the resize 

/*
1_2_3
_4_5_
6_7_8
_9 my
_link
:foo 
a-b-c
-d-e-
f-g-h
-i-j-
k-l-m
-n-o  
*/

    assertEquals(
      List.of(
        new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer("_9 ")),
        new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("my"))
      ),
      getTextBuffer().getLine(-4).getEntries()
    );
    assertEquals(
      List.of(new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer("_link"))),
      getTextBuffer().getLine(-3).getEntries()
    );
    assertEquals(
      List.of(
        new TerminalLine.TextEntry(myHyperlinkStyle, new CharBuffer(":foo")),
        new TerminalLine.TextEntry(mySession.getCurrentStyle(), new CharBuffer(" "))
      ),
      getTextBuffer().getLine(-2).getEntries()
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
