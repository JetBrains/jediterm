package com.jediterm.terminal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.util.Pair;

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
  final ArrayList<CharBuffer> chunks = Lists.newArrayList();
  int firstIndex;
  int power = 0;

  private final FindResult result = new FindResult();


  public SubstringFinder(String pattern) {
    this.pattern = pattern;
    patternHash = pattern.hashCode();
  }


  public void nextChar(CharBuffer characters, int index) {
    if (chunks.size() == 0 || chunks.get(chunks.size() - 1) != characters) {
      chunks.add(characters);
    }

    if (currentLength == pattern.length()) {
      currentHash -= hashCodeForChar(chunks.get(0).charAt(firstIndex));
      if (firstIndex + 1 == chunks.get(0).length()) {
        firstIndex = 0;
        chunks.remove(0);
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
            pattern.equals(new FindResult.FindItem(chunks, firstIndex, index).toString())) {
      result.patternMatched(chunks, firstIndex, index);
      currentHash = 0;
      currentLength = 0;
      power = 0;
      chunks.clear();
      if (index + 1 < characters.length()) {
        firstIndex = index + 1;
        chunks.add(characters);
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
    private final List<String> items = Lists.newArrayList();
    private final Map<CharBuffer, List<Pair<Integer, Integer>>> ranges = Maps.newHashMap();

    public List<Pair<Integer, Integer>> getRanges(CharBuffer characters) {
      return ranges.get(characters);
    }

    public static class FindItem {
      final ArrayList<CharBuffer> chunks;
      final int firstIndex;
      final int lastIndex;

      private FindItem(ArrayList<CharBuffer> chunks, int firstIndex, int lastIndex) {
        this.chunks = Lists.newArrayList(chunks);
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
      }

      public String toString() {
        StringBuilder b = new StringBuilder();

        if (chunks.size() > 1) {
          Pair<Integer, Integer> range = Pair.create(firstIndex, chunks.get(0).length());
          b.append(chunks.get(0).subBuffer(range));
        } else {
          Pair<Integer, Integer> range = Pair.create(firstIndex, lastIndex + 1);
          b.append(chunks.get(0).subBuffer(range));
        }

        for (int i = 1; i < chunks.size() - 1; i++) {
          b.append(chunks.get(i));
        }

        if (chunks.size() > 1) {
          Pair<Integer, Integer> range = Pair.create(0, lastIndex + 1);
          b.append(chunks.get(chunks.size() - 1).subBuffer(range));
        }

        return b.toString();
      }
    }

    public void patternMatched(ArrayList<CharBuffer> chunks, int firstIndex, int lastIndex) {
      StringBuilder b = new StringBuilder();

      if (chunks.size() > 1) {
        Pair<Integer, Integer> range = Pair.create(firstIndex, chunks.get(0).length());
        b.append(chunks.get(0).subBuffer(range));
        put(chunks.get(0), range);
      } else {
        Pair<Integer, Integer> range = Pair.create(firstIndex, lastIndex + 1);
        b.append(chunks.get(0).subBuffer(range));
        put(chunks.get(0), range);
      }

      for (int i = 1; i < chunks.size() - 1; i++) {
        b.append(chunks.get(i));

        put(chunks.get(i), Pair.create(0, chunks.get(i).length()));
      }

      if (chunks.size() > 1) {
        Pair<Integer, Integer> range = Pair.create(0, lastIndex + 1);
        b.append(chunks.get(chunks.size() - 1).subBuffer(range));
        put(chunks.get(0), range);
      }

      items.add(b.toString());

    }

    private void put(CharBuffer characters, Pair<Integer, Integer> range) {
      if (ranges.containsKey(characters)) {
        ranges.get(characters).add(range);
      } else {
        ranges.put(characters, Lists.newArrayList(range));
      }
    }

    public List<String> getItems() {
      return items;
    }
  }
}
