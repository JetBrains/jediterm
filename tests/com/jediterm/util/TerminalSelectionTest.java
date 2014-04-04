package com.jediterm.util;

import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.util.Pair;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author traff
 */
public class TerminalSelectionTest extends TestCase {
  public void testSameRow() {
    TerminalSelection s = new TerminalSelection(new Point(2, 1), new Point(4, 1));
    Pair<Integer, Integer> intersection = s.intersect(3, 1, 1);
    doTest(intersection, 3, 1);
  }

  public void testSameRow2() {
    TerminalSelection s = new TerminalSelection(new Point(2, 1), new Point(4, 1));
    Pair<Integer, Integer> intersection = s.intersect(3, 1, 10);
    doTest(intersection, 3, 2);
  }

  public void testSameRow3() {
    TerminalSelection s = new TerminalSelection(new Point(2, 1), new Point(4, 1));
    Pair<Integer, Integer> intersection = s.intersect(1, 1, 10);
    doTest(intersection, 2, 3);
  }

  public void testSameRowNotIntersect() {
    TerminalSelection s = new TerminalSelection(new Point(2, 1), new Point(4, 1));
    Pair<Integer, Integer> intersection = s.intersect(1, 1, 1);
    assertNull(intersection);
  }

  public void testEndRow() {
    TerminalSelection s = new TerminalSelection(new Point(5, 1), new Point(4, 2));
    Pair<Integer, Integer> intersection = s.intersect(2, 2, 10);
    doTest(intersection, 2, 3);
  }

  public void testStartRow() {
    TerminalSelection s = new TerminalSelection(new Point(5, 1), new Point(4, 2));
    Pair<Integer, Integer> intersection = s.intersect(5, 1, 10);
    doTest(intersection, 5, 10);
  }

  public void testStartRowUnsorted() {
    TerminalSelection s = new TerminalSelection(new Point(4, 2), new Point(5, 1));
    Pair<Integer, Integer> intersection = s.intersect(5, 1, 10);
    doTest(intersection, 5, 10);
  }

  public void testRowOut() {
    TerminalSelection s = new TerminalSelection(new Point(5, 1), new Point(4, 2));
    Pair<Integer, Integer> intersection = s.intersect(5, 3, 10);
    assertNull(intersection);
  }

  public void testRowOut2() {
    TerminalSelection s = new TerminalSelection(new Point(2, 2), new Point(4, 2));
    Pair<Integer, Integer> intersection = s.intersect(5, 1, 10);
    assertNull(intersection);
  }

  public void testConsRows() {
    TerminalSelection s = new TerminalSelection(new Point(5, 2), new Point(5, 3));
    Pair<Integer, Integer> intersection = s.intersect(0, 2, 20);
    doTest(intersection, 5, 15);
  }



  private void doTest(Pair<Integer, Integer> intersection, int x, int len) {
    assertTrue(intersection.toString() + " instead of " + x + ", " + len, x == intersection.first && len == intersection.second);
  }
}
