package com.jediterm.terminal;

import com.jediterm.core.compatibility.Point;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.SubCharBuffer;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of substring search based on Rabin-Karp algorithm
 *
 * @author traff
 */
public class SubstringFinder {
  private final String myPattern;
  private final int myPatternHash;
  private int myCurrentHash;
  private int myCurrentLength;
  private final ArrayList<TextToken> myTokens = new ArrayList<>();
  private int myFirstIndex;
  private int myPower = 0;

  private final FindResult myResult = new FindResult();
  private final boolean myIgnoreCase;

  public SubstringFinder(String pattern, boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
    myPattern = ignoreCase ? pattern.toLowerCase() : pattern;
    myPatternHash = myPattern.hashCode();
  }


  public void nextChar(int x, int y, CharBuffer characters, int index) {
    if (myTokens.size() == 0 || myTokens.get(myTokens.size() - 1).buf != characters) {
      myTokens.add(new TextToken(x, y, characters));
    }

    if (myCurrentLength == myPattern.length()) {
      myCurrentHash -= hashCodeForChar(myTokens.get(0).buf.charAt(myFirstIndex));
      if (myFirstIndex + 1 == myTokens.get(0).buf.length()) {
        myFirstIndex = 0;
        myTokens.remove(0);
      } else {
        myFirstIndex += 1;
      }
    } else {
      myCurrentLength += 1;
      if (myPower == 0) {
        myPower = 1;
      } else {
        myPower *= 31;
      }
    }

    myCurrentHash = 31 * myCurrentHash + charHash(characters.charAt(index));

    if (myCurrentLength == myPattern.length() && myCurrentHash == myPatternHash) {
      FindResult.FindItem item = new FindResult.FindItem(myTokens, myFirstIndex, index, -1);
      String itemText = item.getText();
      boolean matched = myPattern.equals(myIgnoreCase ? itemText.toLowerCase() : itemText);
      if (matched && accept(item)) {
        myResult.patternMatched(myTokens, myFirstIndex, index);
        myCurrentHash = 0;
        myCurrentLength = 0;
        myPower = 0;
        myTokens.clear();
        if (index + 1 < characters.length()) {
          myFirstIndex = index + 1;
          myTokens.add(new TextToken(x, y, characters));
        } else {
          myFirstIndex = 0;
        }
      }
    }
  }

  public boolean accept(@NotNull FindResult.FindItem item) {
    return true;
  }

  private int charHash(char c) {
    return myIgnoreCase ? Character.toLowerCase(c) : c;
  }

  private int hashCodeForChar(char charAt) {
    return myPower * charHash(charAt);
  }

  public FindResult getResult() {
    return myResult;
  }

  public static final class FindResult {
    private final List<FindItem> items = new ArrayList<>();
    private final Map<CharBuffer, List<Pair<Integer, Integer>>> ranges = new HashMap<>();
    private int selectedItem = 0;

    public List<Pair<Integer, Integer>> getRanges(CharBuffer characters) {
      if (characters instanceof SubCharBuffer) {
        SubCharBuffer subCharBuffer = (SubCharBuffer) characters;
        List<Pair<Integer, Integer>> pairs = ranges.get(subCharBuffer.getParent());
        if (pairs != null) {
          List<Pair<Integer, Integer>> filtered = new ArrayList<>();
          for (Pair<Integer, Integer> pair : pairs) {
            Pair<Integer, Integer> intersected = intersect(pair, subCharBuffer.getOffset(), subCharBuffer.getOffset() + subCharBuffer.length());
            if (intersected != null) {
              filtered.add(new Pair<>(intersected.getFirst() - subCharBuffer.getOffset(), intersected.getSecond() - subCharBuffer.getOffset()));
            }
          }
          return filtered;
        }
        return null;
      }
      return ranges.get(characters);
    }

    private @Nullable Pair<Integer, Integer> intersect(@NotNull Pair<Integer, Integer> interval, int a, int b) {
      int start = Math.max(interval.getFirst(), a);
      int end = Math.min(interval.getSecond(), b);
      return start < end ? new Pair<>(start, end) : null;
    }

    public static class FindItem {
      final ArrayList<TextToken> tokens;
      final int firstIndex;
      final int lastIndex;

      // index in the result list
      final int index;

      private FindItem(ArrayList<TextToken> tokens, int firstIndex, int lastIndex, int index) {
        this.tokens = new ArrayList<>(tokens);
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.index = index;
      }

      @NotNull
      public String getText() {
        StringBuilder b = new StringBuilder();

        if (tokens.size() > 1) {
          Pair<Integer, Integer> range = new Pair<>(firstIndex, tokens.get(0).buf.length());
          b.append(tokens.get(0).buf.subBuffer(range));
        } else {
          Pair<Integer, Integer> range = new Pair<>(firstIndex, lastIndex + 1);
          b.append(tokens.get(0).buf.subBuffer(range));
        }

        for (int i = 1; i < tokens.size() - 1; i++) {
          b.append(tokens.get(i).buf);
        }

        if (tokens.size() > 1) {
          Pair<Integer, Integer> range = new Pair<>(0, lastIndex + 1);
          b.append(tokens.get(tokens.size() - 1).buf.subBuffer(range));
        }

        return b.toString();
      }

      @Override
      public String toString() {
        return getText();
      }

      // one-based index in the result list
      public int getIndex() {
        return index;
      }

      public Point getStart() {
        return new Point(tokens.get(0).x + firstIndex, tokens.get(0).y);
      }

      public Point getEnd() {
        return new Point(tokens.get(tokens.size() - 1).x + lastIndex, tokens.get(tokens.size() - 1).y);
      }
    }

    public void patternMatched(ArrayList<TextToken> tokens, int firstIndex, int lastIndex) {
      if (tokens.size() > 1) {
        Pair<Integer, Integer> range = new Pair<>(firstIndex, tokens.get(0).buf.length());
        put(tokens.get(0).buf, range);
      } else {
        Pair<Integer, Integer> range = new Pair<>(firstIndex, lastIndex + 1);
        put(tokens.get(0).buf, range);
      }

      for (int i = 1; i < tokens.size() - 1; i++) {
        put(tokens.get(i).buf, new Pair<>(0, tokens.get(i).buf.length()));
      }

      if (tokens.size() > 1) {
        Pair<Integer, Integer> range = new Pair<>(0, lastIndex + 1);
        put(tokens.get(tokens.size() - 1).buf, range);
      }

      items.add(new FindItem(tokens, firstIndex, lastIndex, items.size() + 1));

    }

    private void put(CharBuffer characters, Pair<Integer, Integer> range) {
      if (ranges.containsKey(characters)) {
        ranges.get(characters).add(range);
      } else {
        ranges.put(characters, new ArrayList<>(List.of(range)));
      }
    }

    public @NotNull List<FindItem> getItems() {
      return items;
    }

    public @NotNull FindItem selectedItem() {
      assertNotEmpty();
      return items.get(selectedItem);
    }

    public @NotNull FindItem nextFindItem() {
      assertNotEmpty();
      selectedItem = (selectedItem + 1) % items.size();
      return selectedItem();
    }

    public @NotNull FindItem prevFindItem() {
      assertNotEmpty();
      selectedItem = (selectedItem + items.size() - 1) % items.size();
      return selectedItem();
    }

    private void assertNotEmpty() {
      if (items.isEmpty()) {
        throw new AssertionError("No items");
      }
    }
  }

  private static class TextToken {
    final CharBuffer buf;
    final int x;
    final int y;

    private TextToken(int x, int y, CharBuffer buf) {
      this.x = x;
      this.y = y;
      this.buf = buf;
    }
  }
}
