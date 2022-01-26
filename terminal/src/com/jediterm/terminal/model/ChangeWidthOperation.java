package com.jediterm.terminal.model;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.*;

class ChangeWidthOperation {
  private static final Logger LOG = Logger.getLogger(TerminalTextBuffer.class.getName());

  private final TerminalTextBuffer myTextBuffer;
  private final int myNewWidth;
  private final int myNewHeight;
  private final Map<Point, Point> myTrackingPoints = new HashMap<>();
  private final List<TerminalLine> myAllLines = new ArrayList<>();
  private TerminalLine myCurrentLine;
  private int myCurrentLineLength;

  ChangeWidthOperation(@NotNull TerminalTextBuffer textBuffer,
                       int newWidth, int newHeight) { 
    myTextBuffer = textBuffer;
    myNewWidth = newWidth;
    myNewHeight = newHeight;
  }

  void addPointToTrack(@NotNull Point original) {
    myTrackingPoints.put(new Point(original), null);
  }

  @NotNull
  Point getTrackedPoint(@NotNull Point original) {
    Point result = myTrackingPoints.get(new Point(original));
    if (result == null) {
      LOG.warning("Not tracked point: " + original);
      return original;
    }
    return result;
  }

  void run() {
    LinesBuffer historyBuffer = myTextBuffer.getHistoryBufferOrBackup();
    for (int i = 0; i < historyBuffer.getLineCount(); i++) {
      TerminalLine line = historyBuffer.getLine(i);
      addLine(line);
    }
    int screenStartInd = myAllLines.size() - 1;
    if (myCurrentLine == null || myCurrentLineLength == myNewWidth) {
      screenStartInd++;
    }
    Preconditions.checkState(screenStartInd >= 0, "screenStartInd < 0: %d", screenStartInd);
    LinesBuffer screenBuffer = myTextBuffer.getScreenBufferOrBackup();
    if (screenBuffer.getLineCount() > myTextBuffer.getHeight()) {
      LOG.warning("Terminal height < screen buffer line count: " + myTextBuffer.getHeight() + " < " + screenBuffer.getLineCount());
    }
    int oldScreenLineCount = Math.min(screenBuffer.getLineCount(), myTextBuffer.getHeight());
    for (int i = 0; i < oldScreenLineCount; i++) {
      List<Point> points = findPointsAtY(i);
      for (Point point : points) {
        int newX = (myCurrentLineLength + point.x) % myNewWidth;
        int newY = myAllLines.size() + (myCurrentLineLength + point.x) / myNewWidth;
        if (myCurrentLine != null) {
          newY--;
        }
        myTrackingPoints.put(point, new Point(newX, newY));
      }
      addLine(screenBuffer.getLine(i));
    }
    for (int i = oldScreenLineCount; i < myTextBuffer.getHeight(); i++) {
      List<Point> points = findPointsAtY(i);
      for (Point point : points) {
        int newX = point.x % myNewWidth;
        int newY = (i - oldScreenLineCount) + myAllLines.size() + point.x / myNewWidth;
        myTrackingPoints.put(point, new Point(newX, newY));
      }
    }
    
    int emptyBottomLineCount = getEmptyBottomLineCount();
    screenStartInd = Math.max(screenStartInd, myAllLines.size() - Math.min(myAllLines.size(), myNewHeight) - emptyBottomLineCount);
    screenStartInd = Math.min(screenStartInd, myAllLines.size() - Math.min(myAllLines.size(), myNewHeight));
    historyBuffer.clearAll();
    historyBuffer.addLines(myAllLines.subList(0, screenStartInd));
    screenBuffer.clearAll();
    screenBuffer.addLines(myAllLines.subList(screenStartInd, Math.min(screenStartInd + myNewHeight, myAllLines.size())));
    for (Map.Entry<Point, Point> entry : myTrackingPoints.entrySet()) {
      Point p = entry.getValue();
      if (p != null) {
        p.y -= screenStartInd;
      } else {
        p = new Point(entry.getKey());
        entry.setValue(p);
      }
      p.x = Math.min(myNewWidth, Math.max(0, p.x));
      p.y = Math.min(myNewHeight, Math.max(0, p.y));
    }
  }

  private int getEmptyBottomLineCount() {
    int result = 0;
    while (result < myAllLines.size() && myAllLines.get(myAllLines.size() - result - 1).isNul()) {
      result++;
    }
    return result;
  }

  @NotNull
  private List<Point> findPointsAtY(int y) {
    List<Point> result = Collections.emptyList();
    for (Point key : myTrackingPoints.keySet()) {
      if (key.y == y) {
        if (result.isEmpty()) {
          result = new ArrayList<>();
        }
        result.add(key);
      }
    }
    return result;
  }

  private void addLine(@NotNull TerminalLine line) {
    if (line.isNul()) {
      if (myCurrentLine != null) {
        myCurrentLine = null;
        myCurrentLineLength = 0;
      }
      myAllLines.add(TerminalLine.createEmpty());
      return;
    }
    line.forEachEntry(entry -> {
      if (entry.isNul()) {
        return;
      }
      int entryProcessedLength = 0;
      while (entryProcessedLength < entry.getLength()) {
        if (myCurrentLine != null && myCurrentLineLength == myNewWidth) {
          myCurrentLine.setWrapped(true);
          myCurrentLine = null;
          myCurrentLineLength = 0;
        }
        if (myCurrentLine == null) {
          myCurrentLine = new TerminalLine();
          myCurrentLineLength = 0;
          myAllLines.add(myCurrentLine);
        }
        int len = Math.min(myNewWidth - myCurrentLineLength, entry.getLength() - entryProcessedLength);
        TerminalLine.TextEntry newEntry = subEntry(entry, entryProcessedLength, len);
        myCurrentLine.appendEntry(newEntry);
        myCurrentLineLength += len;
        entryProcessedLength += len;
      }
    });
    if (!line.isWrapped()) {
      myCurrentLine = null;
      myCurrentLineLength = 0;
    }
  }

  @NotNull
  private static TerminalLine.TextEntry subEntry(@NotNull TerminalLine.TextEntry entry, int startInd, int count) {
    if (startInd == 0 && count == entry.getLength()) {
      return entry;
    }
    return new TerminalLine.TextEntry(entry.getStyle(), entry.getText().subBuffer(startInd, count));
  }
}
