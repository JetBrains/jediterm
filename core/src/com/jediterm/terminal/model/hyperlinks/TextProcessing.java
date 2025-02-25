package com.jediterm.terminal.model.hyperlinks;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class TextProcessing {

  private static final Logger LOG = LoggerFactory.getLogger(TextProcessing.class);

  private final List<AsyncHyperlinkFilter> myHyperlinkFilters = new CopyOnWriteArrayList<>();
  private final TextStyle myHyperlinkColor;
  private final HyperlinkStyle.HighlightMode myHighlightMode;
  private TerminalTextBuffer myTerminalTextBuffer;
  private final List<TerminalHyperlinkListener> myHyperlinkListeners = new CopyOnWriteArrayList<>();

  public TextProcessing(@NotNull TextStyle hyperlinkColor,
                        @NotNull HyperlinkStyle.HighlightMode highlightMode) {
    myHyperlinkColor = hyperlinkColor;
    myHighlightMode = highlightMode;
  }

  public void setTerminalTextBuffer(@NotNull TerminalTextBuffer terminalTextBuffer) {
    myTerminalTextBuffer = terminalTextBuffer;
    terminalTextBuffer.addChangesListener(new TextBufferChangesListener() {
      @Override
      public void linesDiscardedFromHistory(@NotNull List<@NotNull TerminalLine> lines) {
        for (TerminalLine line : lines) {
          TerminalLineUtil.INSTANCE.incModificationCount$core(line);
        }
      }

      @Override
      public void historyCleared() {}

      @Override
      public void widthResized() {}

      @Override
      public void linesChanged(int fromIndex) {}
    });
  }

  public void processHyperlinks(@NotNull LinesStorage linesStorage, @NotNull TerminalLine updatedLine) {
    if (!myHyperlinkFilters.isEmpty()) {
      doProcessHyperlinks(linesStorage, updatedLine);
    }
  }

  private void doProcessHyperlinks(@NotNull LinesStorage linesStorage, @NotNull TerminalLine updatedLine) {
    TerminalLineUtil.INSTANCE.incModificationCount$core(updatedLine);
    LineInfoImpl lineInfo = buildLineInfo(linesStorage, updatedLine);
    if (lineInfo == null) return;
    for (AsyncHyperlinkFilter filter : myHyperlinkFilters) {
      CompletableFuture<LinkResult> resultFuture = filter.apply(lineInfo);
      resultFuture.whenComplete((result, error) -> {
        if (result != null && !result.getItems().isEmpty()) {
          applyLinkResults(result.getItems(), lineInfo);
        }
      });
    }
  }

  private @Nullable TextProcessing.LineInfoImpl buildLineInfo(@NotNull LinesStorage linesStorage, @NotNull TerminalLine updatedLine) {
    myTerminalTextBuffer.lock();
    try {
      int updatedLineInd = linesStorage.indexOf(updatedLine);
      if (updatedLineInd == -1) {
        // When lines arrive fast enough, the line might be pushed to the history buffer already.
        LinesStorage historyLinesStorage = myTerminalTextBuffer.getHistoryLinesStorage();
        updatedLineInd = findHistoryLineInd(historyLinesStorage, updatedLine);
        if (updatedLineInd == -1) {
          LOG.debug("Cannot find line for links processing");
          return null;
        }
        linesStorage = historyLinesStorage;
      }
      int startLineInd = findStartLineInd(linesStorage, updatedLineInd);
      List<TerminalLine> linesToProcess = collectLines(linesStorage, startLineInd, updatedLineInd);
      return new LineInfoImpl(linesToProcess, myTerminalTextBuffer.getWidth());
    }
    finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private @NotNull List<TerminalLine> collectLines(@NotNull LinesStorage linesStorage, int startLineInd, int updatedLineInd) {
    if (startLineInd == updatedLineInd) {
      return List.of(linesStorage.get(startLineInd));
    }
    List<TerminalLine> result = new ArrayList<>(updatedLineInd - startLineInd + 1);
    for (int i = startLineInd; i <= updatedLineInd; i++) {
      result.add(linesStorage.get(i));
    }
    return result;
  }

  private int findStartLineInd(@NotNull LinesStorage linesStorage, int lineInd) {
    int startLineInd = lineInd;
    while (startLineInd > 0 && linesStorage.get(startLineInd - 1).isWrapped()) {
      startLineInd--;
    }
    return startLineInd;
  }

  private void applyLinkResults(@NotNull List<LinkResultItem> linkResultItems, @NotNull TextProcessing.LineInfoImpl lineInfo) {
    myTerminalTextBuffer.lock();
    boolean linkAdded = false;
    try {
      int terminalWidth = myTerminalTextBuffer.getWidth();
      if (lineInfo.myTerminalWidth != terminalWidth) return;
      String lineStr = lineInfo.getLine();
      if (lineStr == null) return;
      String actualLineStr = joinLines(lineInfo.myLinesToProcess, terminalWidth);
      if (!actualLineStr.equals(lineStr)) {
        LOG.warn("Outdated lines when applying hyperlinks");
        return;
      }
      for (LinkResultItem item : linkResultItems) {
        if (item.getStartOffset() < 0 || item.getEndOffset() > lineStr.length()) continue;
        TextStyle style = new HyperlinkStyle(myHyperlinkColor.getForeground(), myHyperlinkColor.getBackground(),
                                             item.getLinkInfo(), myHighlightMode, null);
        int prevLinesLength = 0;
        for (TerminalLine line : lineInfo.myLinesToProcess) {
          int startLineOffset = Math.max(prevLinesLength, item.getStartOffset());
          int endLineOffset = Math.min(prevLinesLength + lineInfo.myTerminalWidth, item.getEndOffset());
          if (startLineOffset < endLineOffset) {
            line.writeString(startLineOffset - prevLinesLength, new CharBuffer(lineStr.substring(startLineOffset, endLineOffset)), style);
            linkAdded = true;
          }
          prevLinesLength += terminalWidth;
        }
      }
    }
    finally {
      myTerminalTextBuffer.unlock();
    }
    if (linkAdded) {
      fireHyperlinksChanged();
    }
  }

  private void fireHyperlinksChanged() {
    for (TerminalHyperlinkListener myHyperlinkListener : myHyperlinkListeners) {
      myHyperlinkListener.hyperlinksChanged();
    }
  }

  public void addHyperlinkListener(@NotNull TerminalHyperlinkListener listener) {
    myHyperlinkListeners.add(listener);
  }

  private int findHistoryLineInd(@NotNull LinesStorage historyLinesStorage, @NotNull TerminalLine line) {
    int lastLineInd = Math.max(0, historyLinesStorage.getSize() - 200); // check only last lines in history buffer
    for (int i = historyLinesStorage.getSize() - 1; i >= lastLineInd; i--) {
      if (historyLinesStorage.get(i) == line) {
        return i;
      }
    }
    return -1;
  }

  public void addHyperlinkFilter(@NotNull HyperlinkFilter filter) {
    addAsyncHyperlinkFilter(new AsyncHyperlinkFilter() {
      @Override
      public @NotNull CompletableFuture<@Nullable LinkResult> apply(@NotNull LineInfo lineInfo) {
        LinkResult result = filter.apply(lineInfo.getLine());
        return CompletableFuture.completedFuture(result);
      }
    });
  }

  public void addAsyncHyperlinkFilter(@NotNull AsyncHyperlinkFilter filter) {
    myHyperlinkFilters.add(filter);
  }

  public @NotNull List<LinkResultItem> applyFilter(@NotNull String line) {
    return myHyperlinkFilters.stream().map(filter -> {
        CompletableFuture<@Nullable LinkResult> resultFuture = filter.apply(new AsyncHyperlinkFilter.LineInfo() {
          @Override
          public @NotNull String getLine() {
            return line;
          }
        });
        try {
          return resultFuture.get(2, TimeUnit.SECONDS);
        }
        catch (Exception e) {
          LOG.info("Failed to find links in {}", line, e);
          return null;
        }
      }).filter(Objects::nonNull)
      .flatMap(result -> result.getItems().stream())
      .collect(Collectors.toList());
  }

  private static @NotNull String joinLines(@NotNull List<TerminalLine> lines, int terminalWidth) {
    StringBuilder result = new StringBuilder();
    int size = lines.size();
    for (int i = 0; i < size; i++) {
      String text = lines.get(i).getText();
      result.append(text);
      if (i < size - 1 && text.length() < terminalWidth) {
        result.append(new CharBuffer(CharUtils.EMPTY_CHAR, terminalWidth - text.length()));
      }
    }
    return result.toString();
  }

  private class LineInfoImpl implements AsyncHyperlinkFilter.LineInfo {
    private final List<TerminalLine> myLinesToProcess;
    private final int[] initialModificationCounts;
    private final int myTerminalWidth;
    private String myCachedLineStr;
    private volatile boolean isUpToDate = true;

    LineInfoImpl(@NotNull List<TerminalLine> linesToProcess, int terminalWidth) {
      myLinesToProcess = linesToProcess;
      initialModificationCounts = new int[linesToProcess.size()];
      int i = 0;
      for (TerminalLine line : linesToProcess) {
        initialModificationCounts[i++] = TerminalLineUtil.INSTANCE.getModificationCount$core(line);
      }
      myTerminalWidth = terminalWidth;
    }

    private boolean isUpToDate() {
      boolean isUpToDate = this.isUpToDate;
      if (isUpToDate) {
        isUpToDate = areLinesUpToDate();
        this.isUpToDate = isUpToDate;
      }
      return isUpToDate;
    }

    private boolean areLinesUpToDate() {
      for (int i = 0; i < myLinesToProcess.size(); i++) {
        TerminalLine line = myLinesToProcess.get(i);
        if (TerminalLineUtil.INSTANCE.getModificationCount$core(line) != initialModificationCounts[i]) {
          return false;
        }
      }
      return true;
    }

    @Override
    public @Nullable String getLine() {
      if (!isUpToDate()) return null;
      if (myCachedLineStr == null) {
        myTerminalTextBuffer.lock();
        try {
          myCachedLineStr = joinLines(myLinesToProcess, myTerminalWidth);
        }
        finally {
          myTerminalTextBuffer.unlock();
        }
      }
      return myCachedLineStr;
    }
  }
}
