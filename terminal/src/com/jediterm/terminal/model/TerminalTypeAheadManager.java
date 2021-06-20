package com.jediterm.terminal.model;

import com.google.common.base.Ascii;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TerminalTypeAheadManager {

  private static final int AUTO_SYNC_DELAY = 2000;
  private static final Logger LOG = Logger.getLogger(TerminalTypeAheadManager.class);

  private final Object LOCK = new Object();
  private final SettingsProvider mySettingsProvider;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final List<TerminalModelListener> myListeners = new CopyOnWriteArrayList<>();
  private final List<TypeAheadPrediction> myPredictions = new ArrayList<>();
  private final JediTerminal myTerminal;
  private TextStyle myTypeAheadTextStyle;
  private boolean myOutOfSyncDetected;
  private long myLastTypedTime;

  public TerminalTypeAheadManager(@NotNull TerminalTextBuffer terminalTextBuffer,
                                  @NotNull JediTerminal terminal,
                                  @NotNull SettingsProvider settingsProvider) {
    myTerminalTextBuffer = terminalTextBuffer;
    myTerminal = terminal;
    mySettingsProvider = settingsProvider;
  }

  public void cursorMoved() {
    synchronized (LOCK) {
      TypeAheadPrediction nextPrediction = getNextPrediction();
      if (nextPrediction != null && nextPrediction.myTypedChars.startsWith("\b")) {
        myTerminalTextBuffer.addModelListener(new TerminalModelListener() {
          @Override
          public void modelChanged() {
            myTerminalTextBuffer.removeModelListener(this);
            checkNextPrediction();
          }
        });
        return;
      }
    }
    checkNextPrediction();
  }

  private void checkNextPrediction() {
    try {
      doCheckNextPrediction();
    } catch (Exception e) {
      LOG.error("Unhandled exception", e);
    }
  }

  private void doCheckNextPrediction() {
    myTerminalTextBuffer.lock();
    int cursorX, cursorY;
    TerminalLine terminalLine;
    try {
      cursorX = myTerminal.getCursorX() - 1;
      cursorY = myTerminal.getCursorY() - 1;
      terminalLine = myTerminalTextBuffer.getLine(cursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
    synchronized (LOCK) {
      TypeAheadPrediction nextPrediction = getNextPrediction();
      if (nextPrediction != null) {
        if (nextPrediction.myInitialLine == terminalLine && nextPrediction.myPredictedCursorX == cursorX &&
          nextPrediction.myPredictedLine.getText().equals(terminalLine.getText())) {
          // prediction matched
          myPredictions.remove(0);
          nextPrediction.unregister();
          List<TypeAheadPrediction> newPredictions = createNewPredictions(terminalLine, cursorX, nextPrediction);
          myPredictions.clear();
          myPredictions.addAll(newPredictions);
          TypeAheadPrediction resultPrediction = getLastPrediction();
          if (resultPrediction != null) {
            resultPrediction.register();
          }
          fireModelChanged();
        } else {
          myOutOfSyncDetected = true;
          clearPredictions();
        }
      }
    }
  }

  private @Nullable TypeAheadPrediction getNextPrediction() {
    return myPredictions.isEmpty() ? null : myPredictions.get(0);
  }

  private @Nullable TypeAheadPrediction getLastPrediction() {
    return myPredictions.isEmpty() ? null : myPredictions.get(myPredictions.size() - 1);
  }

  private @NotNull List<TypeAheadPrediction> createNewPredictions(@NotNull TerminalLine terminalLine,
                                                                  int cursorX,
                                                                  @NotNull TerminalTypeAheadManager.TypeAheadPrediction matchedPrediction) {
    TypeAheadPrediction lastPrediction = getLastPrediction();
    return myPredictions.stream().map((e) -> {
      if (e.myInitialLine != terminalLine) {
        throw new IllegalStateException("Different terminal lines");
      }
      if (!e.myTypedChars.startsWith(matchedPrediction.myTypedChars)) {
        throw new IllegalStateException(e.myTypedChars + " is expected to start with " + e.myTypedChars);
      }
      String newTypedChars = e.myTypedChars.substring(matchedPrediction.myTypedChars.length());
      if (e == lastPrediction) {
        return createPrediction(e.myInitialLine, cursorX, newTypedChars);
      }
      return new TypeAheadPrediction(e.myInitialLine, newTypedChars, e.myPredictedLine, e.myPredictedCursorX);
    }).collect(Collectors.toList());
  }

  public void typed(char keyChar) {
    if (!mySettingsProvider.isTypeAheadEnabled()) {
      return;
    }
    long prevTypedTime = myLastTypedTime;
    myLastTypedTime = System.nanoTime();
    if (myOutOfSyncDetected && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - prevTypedTime) < AUTO_SYNC_DELAY) {
      clearPredictions();
      return;
    }
    myTerminalTextBuffer.lock();
    int cursorX, cursorY;
    try {
      cursorX = myTerminal.getCursorX() - 1;
      cursorY = myTerminal.getCursorY() - 1;
    } finally {
      myTerminalTextBuffer.unlock();
    }
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(cursorY);
    synchronized (LOCK) {
      myOutOfSyncDetected = false;
      System.out.println("Typed " + keyChar);
      if (terminalLine == null) {
        clearPredictions();
        return;
      }
      TypeAheadPrediction lastPrediction = getLastPrediction();
      String prevTypedChars = lastPrediction != null ? lastPrediction.myTypedChars : "";
      TypeAheadPrediction prediction = createPrediction(terminalLine, cursorX, prevTypedChars + keyChar);
      if (prediction != null) {
        prediction.register();
        myPredictions.add(prediction);
        fireModelChanged();
      }
    }
  }

  private void clearPredictions() {
    boolean fireChange = !myPredictions.isEmpty();
    for (TypeAheadPrediction prediction : myPredictions) {
      prediction.unregister();
    }
    myPredictions.clear();
    if (fireChange) {
      fireModelChanged();
    }
  }

  private @Nullable TypeAheadPrediction createPrediction(@NotNull TerminalLine initialLine,
                                                         int initialCursorX,
                                                         @NotNull String typedChars) {
    TerminalLine predictedLine = initialLine.copy();
    int newCursorX = initialCursorX;
    for (Character ch : typedChars.toCharArray()) {
      if (Character.isLetterOrDigit(ch)) {
        predictedLine.writeString(newCursorX, new CharBuffer(ch, 1), getTextStyle());
        newCursorX++;
      } else if (ch == Ascii.BS) {
        if (newCursorX > 0) {
          newCursorX--;
          predictedLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
        }
      } else if (ch == Ascii.DEL) {
        predictedLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
      } else if (ch == KeyEvent.VK_LEFT) {
        if (newCursorX > 0) {
          newCursorX--;
        }
      } else if (ch == KeyEvent.VK_RIGHT) {
        if (newCursorX < myTerminal.getTerminalWidth() - 1) {
          newCursorX++;
        }
      } else {
        return null;
      }
    }
    return new TypeAheadPrediction(initialLine, typedChars, predictedLine, newCursorX);
  }

  public void addModelListener(@NotNull TerminalModelListener listener) {
    myListeners.add(listener);
  }

  private void fireModelChanged() {
    for (TerminalModelListener listener : myListeners) {
      listener.modelChanged();
    }
  }

  private @NotNull TextStyle getTextStyle() {
    TextStyle textStyle = myTypeAheadTextStyle;
    if (textStyle == null) {
      textStyle = new TextStyle(null, TerminalColor.rgb(200, 200, 200));
      myTypeAheadTextStyle = textStyle;
    }
    return textStyle;
  }

  public int getCursorX() {
    TypeAheadPrediction prediction = getLastPrediction();
    return prediction == null ? myTerminal.getCursorX() : prediction.myPredictedCursorX + 1;
  }

  private static class TypeAheadPrediction {
    private final TerminalLine myInitialLine;
    private final String myTypedChars;
    private final TerminalLine myPredictedLine;
    private final int myPredictedCursorX;

    private TypeAheadPrediction(@NotNull TerminalLine initialLine,
                                @NotNull String typedChars,
                                @NotNull TerminalLine predictedLine,
                                int predictedCursorX) {
      myInitialLine = initialLine;
      myTypedChars = typedChars;
      myPredictedLine = predictedLine;
      myPredictedCursorX = predictedCursorX;
    }

    public void register() {
      myInitialLine.setTypeAheadLine(myPredictedLine);
    }

    public void unregister() {
      myInitialLine.setTypeAheadLine(null);
    }
  }
}
