package com.jediterm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.log4j.Logger;

public class TextBuffer implements StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(TextBuffer.class);

  private static final int BUF_SIZE = 8192;
  private static final int RUN_SIZE = 128;

  private List<Section> myCompleteSections = new ArrayList<Section>();

  private Section myCurrentSection;
  private int myCurrentRun;
  private int myBufPos;
  private int myTotalLines;

  public TextBuffer() {
    newSection();
  }

  private void newSection() {
    myCurrentSection = new Section();
    myCurrentRun = -1;
    myBufPos = 0;
  }

  public synchronized String getLines() {
    final StringBuffer sb = new StringBuffer();

    final StyledTextConsumer consumer = new StyledTextConsumer() {
      public void consume(int x, int y, TextStyle style, CharBuffer characters) {
        if (x == 0) sb.append('\n');
        sb.append(characters);
      }
    };
    int currentLine = -myTotalLines;
    for (final Section s : myCompleteSections) {
      currentLine = s.processRunsComplete(currentLine, currentLine, 0, consumer);
    }

    myCurrentSection.processRunsCurrent(currentLine, currentLine, 0, consumer, myBufPos);

    return sb.toString();
  }

  @Override
  public void consume(int x, int y, TextStyle style, CharBuffer characters) {
    addToBuffer(style, characters, x == 0);
  }

  public synchronized void addToBuffer(TextStyle style, CharBuffer characters, boolean isNewLine) {
    char[] buf = characters.getBuf();
    int start = characters.getStart();
    int len = characters.getLen();

    myCurrentRun++;
    if (isNewLine) myTotalLines++;
    myBufPos = myCurrentSection.putRun(myCurrentRun, myBufPos, isNewLine, style, buf, start, len);
    if (myBufPos < 0) {

      myCompleteSections.add(myCurrentSection);
      newSection();
      myCurrentRun++;
      myBufPos = myCurrentSection.putRun(myCurrentRun, myBufPos, isNewLine, style, buf, start, len);
      if (myBufPos < 0) {
        logger.error("Can not put run in new section, bailing out");
      }
    }
  }

  public int getLineCount() {
    return myTotalLines;
  }

  public synchronized void processBufferRows(final int firstLine, final int height, final StyledTextConsumer consumer) {
    // firstLine is negative . 0 is the first line in the back buffer.
    // Find start Section
    int currentLine = -myCurrentSection.getLineCount();
    final int lastLine = firstLine + height;
    if (currentLine > firstLine) {
      //Need to look at past sections
      //Look back through them to find the one that contains our first line.
      int i = myCompleteSections.size() - 1;
      for (; i >= 0; i--) {
        currentLine -= myCompleteSections.get(i).getLineCount();
        if (currentLine <= firstLine)
        // This section contains our first line.
        {
          break;
        }
      }
      i = Math.max(i, 0); // if they requested before this scroll buffer return as much as possible.
      for (; i < myCompleteSections.size(); i++) {
        final int startLine = Math.max(firstLine, currentLine);
        final Section s = myCompleteSections.get(i);
        currentLine = s.processRunsComplete(currentLine, startLine, lastLine, consumer);
        if (currentLine >= lastLine) break;
      }
    }
    if (currentLine < lastLine) {
      myCurrentSection.processRunsCurrent(currentLine, Math.max(firstLine, currentLine), lastLine, consumer, myBufPos);
    }
  }


  static class Section {
    private int width;
    private char[] buf = new char[BUF_SIZE];
    private int[] runStarts = new int[RUN_SIZE];
    private TextStyle[] runStyles = new TextStyle[RUN_SIZE];
    private BitSet lineStarts = new BitSet(RUN_SIZE);

    public int putRun(final int currentRun,
                      final int bufPos,
                      final boolean isNewLine,
                      final TextStyle style,
                      final char[] otherBuf,
                      final int start,
                      final int len) {
      if (bufPos + len >= buf.length) {
        complete(currentRun, bufPos);
        return -1;
      }
      ensureArrays(currentRun);
      lineStarts.set(currentRun, isNewLine);
      runStarts[currentRun] = bufPos;
      runStyles[currentRun] = style;
      System.arraycopy(otherBuf, start, buf, bufPos, len);

      return bufPos + len;
    }

    private void ensureArrays(final int currentRun) {
      if (currentRun >= runStarts.length) {
        runStarts = Util.copyOf(runStarts, runStarts.length * 2);
        runStyles = Util.copyOf(runStyles, runStyles.length * 2);
      }
    }

    public void complete(final int currentRun, final int bufPos) {
      runStarts = Util.copyOf(runStarts, currentRun);
      runStyles = Util.copyOf(runStyles, currentRun);
      buf = Util.copyOf(buf, bufPos);
      lineStarts = lineStarts.get(0, currentRun);
    }

    // for a complete section
    public int processRunsComplete(final int firstLine, final int startLine, final int endLine, final StyledTextConsumer consumer) {
      return pumpRunsImpl(firstLine, startLine, endLine, consumer, buf.length);
    }

    // for a current section
    public int processRunsCurrent(final int firstLine,
                                  final int startLine,
                                  final int endLine,
                                  final StyledTextConsumer consumer,
                                  final int bufPos) {
      return pumpRunsImpl(firstLine, startLine, endLine, consumer, bufPos);
    }

    private int pumpRunsImpl(final int firstLine,
                             final int startLine,
                             final int endLine,
                             final StyledTextConsumer consumer,
                             final int bufferEnd) {
      int x = 0;
      int y = firstLine - 1;
      for (int i = 0; i < runStarts.length; i++) {
        if (lineStarts.get(i)) {
          x = 0;
          y++;
        }
        if (y < startLine) continue;
        if (y > endLine) break;
        final int runStart = runStarts[i];
        int runEnd;
        boolean last = false;
        // if we are at the end of the array, or the next runstart is 0 ( ie unfilled),
        // this is the last run.
        if (i == runStarts.length - 1 || runStarts[i + 1] == 0) {
          runEnd = bufferEnd;
          last = true;
        }
        else {
          runEnd = runStarts[i + 1];
        }

        consumer.consume(x, y, runStyles[i], new CharBuffer(buf, runStart, runEnd - runStart));
        x += runEnd - runStart;
        if (last) break;
      }
      return y;
    }

    int getLineCount() {
      return lineStarts.cardinality();
    }
  }
}
