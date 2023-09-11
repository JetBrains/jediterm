package com.jediterm.terminal.model;

import com.jediterm.core.compatibility.Point;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class ChangeWidthOperation {
  private static final Logger LOG = LoggerFactory.getLogger(TerminalTextBuffer.class);

  private final TerminalTextBuffer myTextBuffer;
  private final int myNewWidth;
  private final int myNewHeight;
  private final Map<TrackingPoint, Point> myTrackingPoints = new HashMap<>();
  private final List<TerminalLine> myAllLines = new ArrayList<>();
  private TerminalLine myCurrentLine;
  private int myCurrentLineLength;

  ChangeWidthOperation(@NotNull TerminalTextBuffer textBuffer,
                       int newWidth, int newHeight) { 
    myTextBuffer = textBuffer;
    myNewWidth = newWidth;
    myNewHeight = newHeight;
  }

  void addPointToTrack(@NotNull Point point, boolean isForceVisible) {
   if (isForceVisible && (point.y < 0 || point.y >= myTextBuffer.getHeight())) {
     LOG.warn("Registered visible point " + point + " is outside screen: [0, " + (myTextBuffer.getHeight() - 1) + "]");
     point.y = Math.min(Math.max(point.y, 0), myTextBuffer.getHeight() - 1);
   }
   myTrackingPoints.put(new TrackingPoint(point, isForceVisible), null);
  }

  @NotNull
  Point getTrackedPoint(@NotNull Point original) {
    Point result = myTrackingPoints.get(new TrackingPoint(original, false));
    if (result != null) {
      return result;
    }
    result = myTrackingPoints.get(new TrackingPoint(original, true));
    if (result != null) {
      return result;
    }
    LOG.warn("Not tracked point: " + original);
    return original;
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
    if (screenStartInd < 0) {
      throw new IndexOutOfBoundsException("screenStartInd < 0: " + screenStartInd);
    }
    LinesBuffer screenBuffer = myTextBuffer.getScreenBufferOrBackup();
    if (screenBuffer.getLineCount() > myTextBuffer.getHeight()) {
      LOG.warn("Terminal height < screen buffer line count: " + myTextBuffer.getHeight() + " < " + screenBuffer.getLineCount());
    }
    int oldScreenLineCount = Math.min(screenBuffer.getLineCount(), myTextBuffer.getHeight());
    for (int i = 0; i < oldScreenLineCount; i++) {
      List<TrackingPoint> points = findPointsAtY(i);
      for (TrackingPoint point : points) {
        int newX = (myCurrentLineLength + point.getX()) % myNewWidth;
        int newY = myAllLines.size() + (myCurrentLineLength + point.getX()) / myNewWidth;
        if (myCurrentLine != null) {
          newY--;
        }
        myTrackingPoints.put(point, new Point(newX, newY));
      }
      addLine(screenBuffer.getLine(i));
    }
    for (int i = oldScreenLineCount; i < myTextBuffer.getHeight(); i++) {
      List<TrackingPoint> points = findPointsAtY(i);
      for (TrackingPoint point : points) {
        int newX = point.getX() % myNewWidth;
        int newY = (i - oldScreenLineCount) + myAllLines.size() + point.getX() / myNewWidth;
        myTrackingPoints.put(point, new Point(newX, newY));
      }
    }
    
    int emptyBottomLineCount = getEmptyBottomLineCount();
    int bottomMostPointY = 0;
    for (Map.Entry<TrackingPoint, Point> entry : myTrackingPoints.entrySet()) {
      if (entry.getKey().getForceVisible()) {
        Point resultPoint = Objects.requireNonNull(entry.getValue());
        bottomMostPointY = Math.max(bottomMostPointY, resultPoint.y);
      }
    }

    screenStartInd = Math.max(screenStartInd, myAllLines.size() - Math.min(myAllLines.size(), myNewHeight) - emptyBottomLineCount);
    screenStartInd = Math.min(screenStartInd, myAllLines.size() - Math.min(myAllLines.size(), myNewHeight));
    screenStartInd = Math.max(screenStartInd, bottomMostPointY - myNewHeight + 1);
    historyBuffer.clearAll();
    historyBuffer.addLines(myAllLines.subList(0, screenStartInd));
    screenBuffer.clearAll();
    screenBuffer.addLines(myAllLines.subList(screenStartInd, Math.min(screenStartInd + myNewHeight, myAllLines.size())));
    for (Map.Entry<TrackingPoint, Point> entry : myTrackingPoints.entrySet()) {
      Point p = entry.getValue();
      if (p != null) {
        p.y -= screenStartInd;
      } else {
        TrackingPoint key = entry.getKey();
        p = new Point(key.getX(), key.getY());
        entry.setValue(p);
      }
      p.x = Math.min(myNewWidth, Math.max(0, p.x));
      p.y = Math.min(myNewHeight, Math.max(0, p.y));
    }
  }

  private int getEmptyBottomLineCount() {
    int ind = myAllLines.size() - 1;
    while (ind >= 0 && myAllLines.get(ind).isNulOrEmpty()) {
      ind--;
    }
    return myAllLines.size() - 1 - ind;
  }

  @NotNull
  private List<TrackingPoint> findPointsAtY(int y) {
    List<TrackingPoint> result = Collections.emptyList();
    for (TrackingPoint key : myTrackingPoints.keySet()) {
      if (key.getY() == y) {
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

  public static class TrackingPoint {
    private final int myX;
    private final int myY;
    private final boolean myForceVisible;

    public TrackingPoint(Point p, boolean forceVisible) {
      this(p.x, p.y, forceVisible);
    }

    public TrackingPoint(int x, int y, boolean forceVisible) {
      myX = x;
      myY = y;
      myForceVisible = forceVisible;
    }

    public int getX() {
      return myX;
    }

    public int getY() {
      return myY;
    }

    public boolean getForceVisible() {
      return myForceVisible;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TrackingPoint)) return false;
      TrackingPoint that = (TrackingPoint) o;
      return myX == that.myX && myY == that.myY && myForceVisible == that.myForceVisible;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myX, myY, myForceVisible);
    }
  }
}
