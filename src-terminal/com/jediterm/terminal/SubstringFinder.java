package com.jediterm.terminal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.util.Pair;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of substring search based on Rabin-Karp algorithm
 *
 * @author traff
 */
public class SubstringFinder {
  final String pattern;
  final int patternHash;
  int currentHash;
  int currentLength;
  final ArrayList<TextToken> tokens = Lists.newArrayList();
  int firstIndex;
  int power = 0;

  private final FindResult result = new FindResult();


  public SubstringFinder(String pattern) {
    this.pattern = pattern;
    patternHash = pattern.hashCode();
  }


  public void nextChar(int x, int y, CharBuffer characters, int index) {
    if (tokens.size() == 0 || tokens.get(tokens.size() - 1).buf != characters) {
      tokens.add(new TextToken(x, y, characters));
    }

    if (currentLength == pattern.length()) {
      currentHash -= hashCodeForChar(tokens.get(0).buf.charAt(firstIndex));
      if (firstIndex + 1 == tokens.get(0).buf.length()) {
        firstIndex = 0;
        tokens.remove(0);
      } else {
        firstIndex += 1;
      }
    } else {
      currentLength += 1;
      if (power == 0) {
        power = 1;
      } else {
        power *= 31;
      }
    }

    currentHash = 31 * currentHash + characters.charAt(index);

    if (currentLength == pattern.length() &&
            currentHash == patternHash &&
            pattern.equals(new FindResult.FindItem(tokens, firstIndex, index).toString())) {
      result.patternMatched(tokens, firstIndex, index);
      currentHash = 0;
      currentLength = 0;
      power = 0;
      tokens.clear();
      if (index + 1 < characters.length()) {
        firstIndex = index + 1;
        tokens.add(new TextToken(x, y, characters));
      } else {
        firstIndex = 0;
      }
    }
  }


  private int hashCodeForChar(char charAt) {
    return power * charAt;
  }

  public FindResult getResult() {
    return result;
  }

  public static class FindResult {
    private final List<FindItem> items = Lists.newArrayList();
    private final Map<CharBuffer, List<Pair<Integer, Integer>>> ranges = Maps.newHashMap();
    private int currentFindItem = 0;

    public List<Pair<Integer, Integer>> getRanges(CharBuffer characters) {
      return ranges.get(characters);
    }

    public static class FindItem {
      final ArrayList<TextToken> tokens;
      final int firstIndex;
      final int lastIndex;

      private FindItem(ArrayList<TextToken> tokens, int firstIndex, int lastIndex) {
        this.tokens = Lists.newArrayList(tokens);
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
      }

      public String toString() {
        StringBuilder b = new StringBuilder();

        if (tokens.size() > 1) {
          Pair<Integer, Integer> range = Pair.create(firstIndex, tokens.get(0).buf.length());
          b.append(tokens.get(0).buf.subBuffer(range));
        } else {
          Pair<Integer, Integer> range = Pair.create(firstIndex, lastIndex + 1);
          b.append(tokens.get(0).buf.subBuffer(range));
        }

        for (int i = 1; i < tokens.size() - 1; i++) {
          b.append(tokens.get(i));
        }

        if (tokens.size() > 1) {
          Pair<Integer, Integer> range = Pair.create(0, lastIndex + 1);
          b.append(tokens.get(tokens.size() - 1).buf.subBuffer(range));
        }

        return b.toString();
      }

      public Point getStart() {
        return new Point(tokens.get(0).x + firstIndex, tokens.get(0).y);
      }

      public Point getEnd() {
        return new Point(tokens.get(tokens.size()-1).x + lastIndex, tokens.get(tokens.size()-1).y);
      }
    }

    public void patternMatched(ArrayList<TextToken> tokens, int firstIndex, int lastIndex) {
      if (tokens.size() > 1) {
        Pair<Integer, Integer> range = Pair.create(firstIndex, tokens.get(0).buf.length());
        put(tokens.get(0).buf, range);
      } else {
        Pair<Integer, Integer> range = Pair.create(firstIndex, lastIndex + 1);
        put(tokens.get(0).buf, range);
      }

      for (int i = 1; i < tokens.size() - 1; i++) {
        put(tokens.get(i).buf, Pair.create(0, tokens.get(i).buf.length()));
      }

      if (tokens.size() > 1) {
        Pair<Integer, Integer> range = Pair.create(0, lastIndex + 1);
        put(tokens.get(0).buf, range);
      }

      items.add(new FindItem(tokens, firstIndex, lastIndex));

    }

    private void put(CharBuffer characters, Pair<Integer, Integer> range) {
      if (ranges.containsKey(characters)) {
        ranges.get(characters).add(range);
      } else {
        ranges.put(characters, Lists.newArrayList(range));
      }
    }

    public List<FindItem> getItems() {
      return items;
    }
    
    public FindItem nextFindItem() {
      if (currentFindItem == 0) {
        currentFindItem = items.size() - 1;
      } else {
        currentFindItem--;
      }
      
      return items.get(currentFindItem);
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
