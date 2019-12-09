package com.jediterm;

import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.model.CharBuffer;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class SubstringFinderTest extends TestCase {
  public void test0() {
    doTest("abc", "abc");
  }

  public void test2() {
    doTest("abc", "xa", "bc");
  }

  public void test3() {
    doTest("abc", "xyza", "ba", "bcd");
  }

  public void test4() {
    doTest("abcdef", "xxxxxxxxxxxxxxxxxxxxxxxxxxx", "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyabc", "defzzzzzzzzzzzzzzzzzzzzzz");
  }

  public void test5() {
    doTest("abc", "xxxxxxxxxxxxabcxxxxxxxxxxxxxxx");
  }

  public void test6() {
    SubstringFinder.FindResult res = getFindResult("aba", "abacaba");
    assertEquals(2, res.getItems().size());
    for (int i = 0; i < res.getItems().size(); i++) {
      assertEquals("aba", res.getItems().get(i).getText());
    }
  }

  public void test7() {
    SubstringFinder.FindResult res = getFindResult("aa", "aaaa");
    //after a pattern is matched we start from the next character
    assertEquals(2, res.getItems().size());
    for (int i = 0; i < res.getItems().size(); i++) {
      assertEquals("aa", res.getItems().get(i).getText());
    }
  }

  public void test8() {
    SubstringFinder.FindResult res = getFindResult("aaa", "aa", "aa", "aa");
    //after a pattern is matched we start from the next character
    assertEquals(2, res.getItems().size());
    for (int i = 0; i < res.getItems().size(); i++) {
      assertEquals("aaa", res.getItems().get(i).getText());
    }
  }

  public void test9() {
    doTest("2Menu", " 2", "Menu ");
  }

  public void test10() {
    doTest("git log", "g", "i", "t", " ", "l", "o", "g");
  }

  public void test11() {
    doTest("Hello World", "print('Hello", " ", "World')");
  }

  public void testIgnoreCase() {
    SubstringFinder.FindResult res = getFindResult("abc", " ABC ");
    //after a pattern is matched we start from the next character
    assertEquals(1, res.getItems().size());
    assertEquals("ABC", res.getItems().get(0).getText());
  }

  private void doTest(String patter, String... strings) {
    SubstringFinder.FindResult res = getFindResult(patter, strings);

    assertEquals(1, res.getItems().size());
    assertEquals(patter, res.getItems().get(0).getText());
  }

  @NotNull
  private static SubstringFinder.FindResult getFindResult(@NotNull String patter, String... strings) {
    SubstringFinder f = new SubstringFinder(patter, true);
    for (String string : strings) {
      CharBuffer cb = new CharBuffer(string);

      for (int j = 0; j < cb.length(); j++) {
        f.nextChar(0, 0, cb, j);
      }
    }

    return f.getResult();
  }
}
