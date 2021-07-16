package com.jediterm.terminal.model;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalTypeAheadManager {

  private static final long LATENCY_THRESHOLD = TimeUnit.MILLISECONDS.toNanos(100); // TODO: move to settings
  private static final long MIN_CLEAR_PREDICTIONS_DELAY = TimeUnit.MILLISECONDS.toNanos(500);
  private static final long MAX_TERMINAL_DELAY = TimeUnit.MILLISECONDS.toNanos(3000);
  private static final int LATENCY_MIN_SAMPLES_TO_TURN_ON = 5;
  private static final int LATENCY_BUFFER_SIZE = 30;
  private static final double LATENCY_TOGGLE_OFF_THRESHOLD = 0.5;

  private static final Logger LOG = Logger.getLogger(TerminalTypeAheadManager.class);

  private final Object LOCK = new Object();
  private final SettingsProvider mySettingsProvider;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final List<TerminalModelListener> myListeners = new CopyOnWriteArrayList<>();
  private final List<TypeAheadPrediction> myPredictions = new ArrayList<>();
  private final JediTerminal myTerminal;
  private final Debouncer myDeferClearingPredictions = new Debouncer(new TimeoutPredictionCleaner());
  private final LatencyStatistics myLatencyStatistics = new LatencyStatistics(LATENCY_BUFFER_SIZE);

  private TextStyle myTypeAheadTextStyle;
  private boolean myIsShowingPredictions = false;
  private boolean myOutOfSyncDetected;
  private long myLastTypedTime;
  private TypeAheadPrediction myLastSuccessfulPrediction = null;
  private String myTerminalDataBuffer = "";
  private Integer myLeftMostCursorPosition = null;
  private boolean myIsNotPasswordPrompt = false;

  public TerminalTypeAheadManager(@NotNull TerminalTextBuffer terminalTextBuffer,
                                  @NotNull JediTerminal terminal,
                                  @NotNull SettingsProvider settingsProvider) {
    myTerminalTextBuffer = terminalTextBuffer;
    myTerminal = terminal;
    mySettingsProvider = settingsProvider;
  }

  public void onTerminalData(@NotNull String data) {
    LOG.debug("onTerminalData: " + data.replace("\u001b", "ESC")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u0007", "BEL")
            .replace(" ", "<S>")
            .replace("\b", "\\b"));

    String terminalData = myTerminalDataBuffer + data;
    myTerminalDataBuffer = "";
    TypeaheadStringReader terminalDataReader = new TypeaheadStringReader(terminalData);

    TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();

    synchronized (LOCK) {
      if (terminalLineWithCursor == null) {
        resetState();
        return;
      }

      if (!myPredictions.isEmpty()) {
        updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);
        myDeferClearingPredictions.call();
      } else {
        resetState();
        return;
      }

      while (!myPredictions.isEmpty() && terminalDataReader.remainingLength() > 0) {
        // TODO: vscode omits some char sequences from sending to the prediction engine, maybe we should too.

        if (checkNextPrediction(terminalDataReader, terminalLineWithCursor)) return;
      }

      // TODO: resetState if not (isEmpty && remaining == 0)?
    }
  }

  public void onKeyEvent(@NotNull KeyEvent keyEvent) {
    TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();

    synchronized (LOCK) {
      if (terminalLineWithCursor == null) {
        resetState();
        return;
      }

      long prevTypedTime = myLastTypedTime;
      myLastTypedTime = System.nanoTime();

      long autoSyncDelay;
      if (myLatencyStatistics.getSampleSize() >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
        autoSyncDelay = Math.min(myLatencyStatistics.getMaxLatency(), MAX_TERMINAL_DELAY);
      } else {
        autoSyncDelay = MAX_TERMINAL_DELAY;
      }

      if (System.nanoTime() - prevTypedTime < autoSyncDelay) {
        if (myOutOfSyncDetected) {
          resetState();
          return;
        }
      } else {
        myOutOfSyncDetected = false;
        reevaluatePredictorState();
      }

      updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);


      if (myPredictions.isEmpty()) {
        myDeferClearingPredictions.call(); // start a timer that will clear predictions
      }
      TypeAheadPrediction prediction = createPrediction(terminalLineWithCursor, keyEvent);
      myPredictions.add(prediction);
      redrawPredictions(terminalLineWithCursor);

      LOG.debug("Created prediction for \"" + keyEvent.getKeyChar() + "\" (" + keyEvent.getKeyCode() + ")");
    }
  }

  public void onResize() {
    synchronized (LOCK) {
      resetState();
    }
  }

  public void addModelListener(@NotNull TerminalModelListener listener) {
    myListeners.add(listener);
  }

  public int getCursorX() {
    synchronized (LOCK) {
      if (myTerminalTextBuffer.isUsingAlternateBuffer() && !myPredictions.isEmpty()) {
        // otherwise it will misreport cursor position
        resetState();
      }

      TypeAheadPrediction prediction = getLastVisiblePrediction();
      return prediction == null ? myTerminal.getCursorX() : prediction.myPredictedCursorX + 1;
    }
  }

  private @Nullable TypeAheadPrediction getNextPrediction() {
    return myPredictions.isEmpty() ? null : myPredictions.get(0);
  }

  private @Nullable TypeAheadPrediction getLastPrediction() {
    return myPredictions.isEmpty() ? null : myPredictions.get(myPredictions.size() - 1);
  }

  private @Nullable TypeAheadPrediction getLastVisiblePrediction() {
    int lastVisiblePredictionIndex = 0;
    while (lastVisiblePredictionIndex < myPredictions.size()
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof TentativeBoundary)
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof HardBoundary)) {
      lastVisiblePredictionIndex++;
    }
    lastVisiblePredictionIndex--;

    return lastVisiblePredictionIndex >= 0 ? myPredictions.get(lastVisiblePredictionIndex) : null;
  }

  private @Nullable TerminalLineWithCursor getTerminalLineWithCursor() {
    myTerminalTextBuffer.lock();

    try {
      if (myTerminalTextBuffer.isUsingAlternateBuffer()) {
        return null;
      }

      int cursorX = myTerminal.getCursorX() - 1;
      int cursorY = myTerminal.getCursorY() - 1;
      TerminalLine terminalLine = myTerminalTextBuffer.getLine(cursorY);

      return new TerminalLineWithCursor(terminalLine, cursorX, cursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private boolean checkNextPrediction(@NotNull TypeaheadStringReader terminalDataReader,
                                      @NotNull TerminalLineWithCursor terminalLineWithCursor) {
    TypeAheadPrediction nextPrediction = getNextPrediction();
    if (nextPrediction == null) {
      return true;
    }

    int readerIndexBeforeMatching = terminalDataReader.myIndex;

    String debugString = "char \"" + nextPrediction.myKeyEvent.getKeyChar() + "\" " + nextPrediction.myKeyEvent.getKeyCode();
    switch (nextPrediction.matches(terminalDataReader, terminalLineWithCursor.myCursorX)) {
      case Success:
        LOG.debug("Matched successfully: " + debugString);
        myLatencyStatistics.adjustLatency(nextPrediction);

        if (nextPrediction.getCharacterOrNull() != null) {
          myIsNotPasswordPrompt = true;
        }

        myLastSuccessfulPrediction = myPredictions.get(0);
        myPredictions.remove(0);
        nextPrediction.myInitialLine.setTypeAheadLine(null);
        redrawPredictions(terminalLineWithCursor);
        break;
      case Buffer:
        LOG.debug("Buffered: " + debugString);
        myTerminalDataBuffer = terminalDataReader.myString.substring(readerIndexBeforeMatching);
        return true;
      case Failure:
        LOG.debug("Match failure: " + debugString);
        myOutOfSyncDetected = true; // TODO: move to resetState()?
        resetState();
    }
    return false;
  }

  private void redrawPredictions(@NotNull TerminalLineWithCursor terminalLineWithCursor) {
    TerminalLineWithCursor newTerminalLineWithCursor = terminalLineWithCursor.copy();

    TypeAheadPrediction lastVisiblePrediction = getLastVisiblePrediction();

    if (lastVisiblePrediction != null) {
      for (TypeAheadPrediction prediction : myPredictions) {
        updateTerminalLinePrediction(newTerminalLineWithCursor, prediction.myKeyEvent);
        if (lastVisiblePrediction == prediction) {
          break;
        }
      }
    }

    TerminalLine predictedLine = newTerminalLineWithCursor.myTerminalLine;
    terminalLineWithCursor.myTerminalLine.setTypeAheadLine(predictedLine);
    fireModelChanged();
  }

  private void updateLeftMostCursorPosition(int cursorX) {
    if (myLeftMostCursorPosition == null) {
      myLeftMostCursorPosition = cursorX;
    } else {
      myLeftMostCursorPosition = Math.min(myLeftMostCursorPosition, cursorX);
    }
  }

  private static class TerminalLineWithCursor {
    final @NotNull TerminalLine myTerminalLine;
    int myCursorX;
    int myCursorY;

    TerminalLineWithCursor(@NotNull TerminalLine terminalLine, int cursorX, int cursorY) {
      myTerminalLine = terminalLine;
      myCursorX = cursorX;
      myCursorY = cursorY;
    }

    @NotNull TerminalLineWithCursor copy() {
      return new TerminalLineWithCursor(myTerminalLine.copy(), myCursorX, myCursorY);
    }
  }

  private void updateTerminalLinePrediction(@NotNull TerminalLineWithCursor terminalLineWithCursor,
                                            @NotNull KeyEvent keyEvent) {
    TerminalLine terminalLine = terminalLineWithCursor.myTerminalLine;
    int newCursorX = terminalLineWithCursor.myCursorX;

    if (KeyEventHelper.isKeyTypedEvent(keyEvent)) {
      terminalLine.writeString(newCursorX, new CharBuffer(keyEvent.getKeyChar(), 1), getTextStyle());
      newCursorX++;
    } else if (KeyEventHelper.isBackspace(keyEvent)) {
      if (newCursorX > 0) {
        newCursorX--;
        terminalLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
      }
    } else if (KeyEventHelper.isArrowKey(keyEvent)) {
      int delta = keyEvent.getKeyCode() == KeyEvent.VK_RIGHT ? 1 : -1;
      if (0 <= newCursorX + delta && newCursorX + delta < myTerminal.getTerminalWidth()) {
        newCursorX += delta;
      }
    } else if (KeyEventHelper.isAltArrowKey(keyEvent)) {
      CursorMoveDirection direction = keyEvent.getKeyCode() == KeyEvent.VK_RIGHT ? CursorMoveDirection.Forward : CursorMoveDirection.Back;
      newCursorX = moveToWordBoundary(terminalLine.getText(), newCursorX, direction);
    } else {
      throw new IllegalStateException("Characters should be filtered but typedChar contained key code " + keyEvent.getKeyCode());
    }

    terminalLineWithCursor.myCursorX = newCursorX;
  }

  private void resetState() {
    boolean fireChange = !myPredictions.isEmpty();

    for (TypeAheadPrediction prediction : myPredictions) {
      prediction.myInitialLine.setTypeAheadLine(null);
    }
    if (myLastSuccessfulPrediction != null) {
      myLastSuccessfulPrediction.myInitialLine.setTypeAheadLine(null);
    }

    myPredictions.clear();
    myTerminalDataBuffer = "";
    myLeftMostCursorPosition = null;
    myIsNotPasswordPrompt = false;
    myLastSuccessfulPrediction = null;
    myDeferClearingPredictions.terminateCall();

    if (fireChange) {
      fireModelChanged();
    }
  }

  private void reevaluatePredictorState() {
    if (!mySettingsProvider.isTypeAheadEnabled()) {
      myIsShowingPredictions = false;
    } else if (myLatencyStatistics.getSampleSize() >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
      long latency = myLatencyStatistics.getLatencyMedian();
      if (latency >= LATENCY_THRESHOLD) {
        myIsShowingPredictions = true;
      } else if (latency < LATENCY_THRESHOLD * LATENCY_TOGGLE_OFF_THRESHOLD) {
        myIsShowingPredictions = false;
      }
    }
  }

  private @NotNull TypeAheadPrediction createPrediction(@NotNull TerminalLineWithCursor initialLineWithCursor,
                                                        @NotNull KeyEvent keyEvent) {
    TerminalLine initialLine = initialLineWithCursor.myTerminalLine;

    if (getLastPrediction() instanceof HardBoundary) {
      return new HardBoundary(initialLine, keyEvent, -1);
    }

    TerminalLineWithCursor newLineWCursor = initialLineWithCursor.copy();
    for (TypeAheadPrediction prediction : myPredictions) {
      updateTerminalLinePrediction(newLineWCursor, prediction.myKeyEvent);
    }

    if (KeyEventHelper.isKeyTypedEvent(keyEvent)) {
      updateTerminalLinePrediction(newLineWCursor, keyEvent);

      boolean hasCharacterPredictions = myPredictions.stream().anyMatch(
              (TypeAheadPrediction prediction) -> prediction.getCharacterOrNull() != null);

      return constructPrediction(
              new CharacterPrediction(initialLine, keyEvent, newLineWCursor.myCursorX),
              myIsNotPasswordPrompt || hasCharacterPredictions
      );
    } else if (KeyEventHelper.isBackspace(keyEvent)) {
      updateTerminalLinePrediction(newLineWCursor, keyEvent);

      return constructPrediction(
              new BackspacePrediction(initialLine, keyEvent, newLineWCursor.myCursorX),
              myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
      );
    } else if (KeyEventHelper.isArrowKey(keyEvent)) {
      updateTerminalLinePrediction(newLineWCursor, keyEvent);

      return constructPrediction(
              new CursorMovePrediction(initialLine, keyEvent, newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, 1),
              myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
                      && newLineWCursor.myCursorX <= newLineWCursor.myTerminalLine.getText().length()
      );
    } else if (KeyEventHelper.isAltArrowKey(keyEvent)) {
      int oldCursorX = newLineWCursor.myCursorX;
      updateTerminalLinePrediction(newLineWCursor, keyEvent);

      int amount = Math.abs(newLineWCursor.myCursorX - oldCursorX);

      return constructPrediction(
              new CursorMovePrediction(initialLine, keyEvent, newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, amount),
              myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
                      && newLineWCursor.myCursorX <= newLineWCursor.myTerminalLine.getText().length()
      );
    } else {
      return new HardBoundary(initialLine, keyEvent, -1);
    }
  }

  private @NotNull TypeAheadPrediction constructPrediction(@NotNull TypeAheadPrediction prediction,
                                                           boolean isNotTentative) {
    if (myIsShowingPredictions && isNotTentative) {
      return prediction;
    }

    return new TentativeBoundary(prediction);
  }

  private int moveToWordBoundary(@NotNull String text, int index, @NotNull CursorMoveDirection direction) {
    if (direction == CursorMoveDirection.Back) {
      index -= 1;
    }

    boolean ateLeadingWhitespace = false;
    while (index >= 0) {
      if (index >= text.length()) {
        return index;
      }

      char currentChar = text.charAt(index);
      if (Character.isLetterOrDigit(currentChar)) {
        ateLeadingWhitespace = true;
      } else if (ateLeadingWhitespace) {
        break;
      }

      index += direction == CursorMoveDirection.Forward ? 1 : -1;
    }

    if (direction == CursorMoveDirection.Back) {
      index += 1;
    }

    return index;
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

  private enum MatchResult {
    Success,
    Failure,
    Buffer,
  }

  private static class TypeaheadStringReader {
    final String myString;

    private int myIndex = 0;

    TypeaheadStringReader(@NotNull String string) {
      myString = string;
    }

    int remainingLength() {
      return myString.length() - myIndex;
    }

    boolean isEOF() {
      return myString.length() == myIndex;
    }

    @Nullable Character eatChar(char character) {
      if (myString.charAt(myIndex) != character) {
        return null;
      }

      myIndex++;
      return character;
    }

    @SuppressWarnings("unused") // TODO: needed to fix cursor move prediction
    @Nullable String eatString(@NotNull String substr) {
      if (!myString.substring(myIndex, substr.length()).equals(substr)) {
        return null;
      }

      myIndex += substr.length();
      return substr;
    }

    @NotNull MatchResult eatGradually(@NotNull String substr) {
      int prevIndex = myIndex;

      for (int i = 0; i < substr.length(); ++i) {
        if (i > 0 && isEOF()) {
          return MatchResult.Buffer;
        }

        if (eatChar(substr.charAt(i)) == null) {
          this.myIndex = prevIndex;
          return MatchResult.Failure;
        }
      }

      return MatchResult.Success;
    }

    @Nullable String eatRegex(@NotNull Pattern pattern) {
      // TODO: verify correctness
      Matcher matcher = pattern.matcher(myString.substring(myIndex));
      if (!matcher.matches()) {
        return null;
      }

      java.util.regex.MatchResult match = matcher.toMatchResult();
      myIndex += matcher.end();
      return match.group();
    }
  }

  private final String CSI = (char) CharUtils.ESC + "[";

  private abstract static class TypeAheadPrediction {
    public final long myCreatedTime;

    protected final TerminalLine myInitialLine;
    protected final int myPredictedCursorX;
    protected KeyEvent myKeyEvent;

    private TypeAheadPrediction(@NotNull TerminalLine initialLine,
                                @NotNull KeyEvent keyEvent,
                                int predictedCursorX) {
      myInitialLine = initialLine;
      myKeyEvent = keyEvent;
      myPredictedCursorX = predictedCursorX;

      myCreatedTime = System.nanoTime();
    }

    public @Nullable Character getCharacterOrNull() {
      if (this instanceof CharacterPrediction) {
        return ((CharacterPrediction) this).getCharacter();
      }

      if (!(this instanceof TentativeBoundary)) {
        return null;
      }
      TentativeBoundary tentativeBoundary = (TentativeBoundary) this;

      if (tentativeBoundary.myInnerPrediction instanceof CharacterPrediction) {
        return ((CharacterPrediction) tentativeBoundary.myInnerPrediction).getCharacter();
      }

      return null;
    }

    public abstract @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX);
  }

  private static class HardBoundary extends TypeAheadPrediction {
    private HardBoundary(@NotNull TerminalLine initialLine, @NotNull KeyEvent keyEvent, int predictedCursorX) {
      super(initialLine, keyEvent, predictedCursorX);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      return MatchResult.Failure;
    }
  }

  private static class TentativeBoundary extends TypeAheadPrediction {
    final TypeAheadPrediction myInnerPrediction;

    private TentativeBoundary(@NotNull TypeAheadPrediction innerPrediction) {
      super(innerPrediction.myInitialLine, innerPrediction.myKeyEvent, innerPrediction.myPredictedCursorX);
      myInnerPrediction = innerPrediction;
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      return myInnerPrediction.matches(stringReader, cursorX);
    }
  }

  private class CharacterPrediction extends TypeAheadPrediction {
    private CharacterPrediction(@NotNull TerminalLine initialLine,
                                @NotNull KeyEvent keyEvent,
                                int predictedCursorX) {
      super(initialLine, keyEvent, predictedCursorX);
    }

    char getCharacter() {
      return myKeyEvent.getKeyChar();
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      // remove any styling CSI before checking the char
      String eaten;
      Pattern CSI_STYLE_RE = Pattern.compile("^\\x1b\\[[0-9;]*m"); // TODO: test regex
      do {
        eaten = stringReader.eatRegex(CSI_STYLE_RE);
      } while (eaten != null && !eaten.isEmpty());

      MatchResult result = MatchResult.Failure;

      if (stringReader.isEOF()) {
        result = MatchResult.Buffer;
      } else if (stringReader.eatChar(getCharacter()) != null) {
        result = MatchResult.Success;
      } else if (myLastSuccessfulPrediction != null && myLastSuccessfulPrediction.getCharacterOrNull() != null) {
        // vscode #112842
        String zshPrediction = "\b" + myLastSuccessfulPrediction.getCharacterOrNull() + getCharacter();
        result = stringReader.eatGradually(zshPrediction);
      }

      if (result == MatchResult.Success && cursorX != myPredictedCursorX + stringReader.remainingLength()) {
        result = MatchResult.Failure;
      }
      return result;
    }
  }

  private class BackspacePrediction extends TypeAheadPrediction {
    private BackspacePrediction(@NotNull TerminalLine initialLine,
                                @NotNull KeyEvent keyEvent,
                                int predictedCursorX) {
      super(initialLine, keyEvent, predictedCursorX);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      MatchResult result;

      MatchResult r1 = stringReader.eatGradually("\b" + CSI + "K");
      if (r1 != MatchResult.Failure) {
        result = r1;
      } else {
        result = stringReader.eatGradually("\b \b");
      }

      if (result == MatchResult.Success && cursorX != myPredictedCursorX) {
        result = MatchResult.Failure;
      }
      return result;
    }
  }

  private enum CursorMoveDirection {
    Forward('C'),
    Back('D');

    CursorMoveDirection(char ch) {
      myChar = ch;
    }

    char myChar;
  }

  private class CursorMovePrediction extends TypeAheadPrediction {
    private final int myAmount;
    private final CursorMoveDirection myDirection;
    private final int myCursorY;

    private CursorMovePrediction(@NotNull TerminalLine initialLine,
                                 @NotNull KeyEvent keyEvent,
                                 int predictedCursorX,
                                 int cursorY,
                                 int amount) {
      super(initialLine, keyEvent, predictedCursorX);
      myAmount = amount;
      myDirection = keyEvent.getKeyCode() == KeyEvent.VK_LEFT ? CursorMoveDirection.Back : CursorMoveDirection.Forward;
      myCursorY = cursorY;
    }

    private @NotNull MatchResult _matches(TypeaheadStringReader stringReader) {
      MatchResult r1 = stringReader.eatGradually((CSI + myDirection.myChar).repeat(myAmount)); // FIXME: https://github.com/microsoft/vscode/commit/47bbc2f8e7da11305e7d2414b91f0ab8041bbc73
      if (r1 != MatchResult.Failure) {
        return r1;
      }

      if (myDirection == CursorMoveDirection.Back) {
        MatchResult r2 = stringReader.eatGradually("\b".repeat(myAmount));
        if (r2 != MatchResult.Failure) {
          return r2;
        }
      }

      MatchResult r3 = stringReader.eatGradually(CSI + myAmount + myDirection.myChar);
      if (r3 != MatchResult.Failure) {
        return r3;
      }

      return stringReader.eatGradually(CSI + (myCursorY + 1) + ";" + (myPredictedCursorX + 1) + "H");
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      MatchResult result = _matches(stringReader);

      if (result == MatchResult.Success && cursorX != myPredictedCursorX) {
        result = MatchResult.Failure;
      }

      return result;
    }
  }

  private static class KeyEventHelper {
    private final static int allModifiersMask = InputEvent.ALT_DOWN_MASK
            | InputEvent.SHIFT_DOWN_MASK
            | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK
            | InputEvent.ALT_GRAPH_DOWN_MASK;

    static boolean isKeyTypedEvent(@NotNull KeyEvent keyEvent) {
      return keyEvent.getKeyCode() == KeyEvent.VK_UNDEFINED;
    }

    static boolean isBackspace(@NotNull KeyEvent keyEvent) {
      return keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE
              && (keyEvent.getModifiersEx() & allModifiersMask) == 0;
    }

    static boolean isArrowKey(@NotNull KeyEvent keyEvent) {
      return (keyEvent.getKeyCode() == KeyEvent.VK_LEFT || keyEvent.getKeyCode() == KeyEvent.VK_RIGHT)
              && (keyEvent.getModifiersEx() & allModifiersMask) == 0;
    }

    static boolean isAltArrowKey(@NotNull KeyEvent keyEvent) {
      return (keyEvent.getKeyCode() == KeyEvent.VK_LEFT || keyEvent.getKeyCode() == KeyEvent.VK_RIGHT)
              && (keyEvent.getModifiersEx() & allModifiersMask) == InputEvent.ALT_DOWN_MASK;
    }
  }

  private interface Callback {
    void call();
  }

  private class Debouncer {
    private final ScheduledExecutorService myScheduler = Executors.newScheduledThreadPool(1);
    private TimerTask myTimerTask = null;
    private final Callback myCallback;
    private final Object myLock = new Object();

    public Debouncer(@NotNull Callback callback) {
      this.myCallback = callback;
    }

    public void call() {
      synchronized (myLock) {
        long interval;
        if (myLatencyStatistics.getSampleSize() >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
          interval = Math.min(
                  Math.max(myLatencyStatistics.getMaxLatency() * 3 / 2, MIN_CLEAR_PREDICTIONS_DELAY),
                  MAX_TERMINAL_DELAY
          );
        } else {
          interval = MAX_TERMINAL_DELAY;
        }

        if (myTimerTask != null) {
          myTimerTask.cancel();
        }

        myTimerTask = new TimerTask(interval);
        myScheduler.schedule(myTimerTask, interval, TimeUnit.NANOSECONDS);
      }
    }

    public void terminateCall() {
      if (myTimerTask != null) {
        myTimerTask.cancel();
      }
    }

    // The task that wakes up when the wait time elapses
    private class TimerTask implements Runnable {
      private long myDueTime;
      private boolean myIsActive;

      public TimerTask(long interval) {
        myDueTime = System.nanoTime() + interval;
      }

      public void cancel() {
        synchronized (myLock) {
          myIsActive = false;
        }
      }

      public void run() {
        synchronized (myLock) {
          if (!myIsActive) {
            return;
          }

          long remaining = myDueTime - System.nanoTime();
          if (remaining > 0) { // Re-schedule task
            myScheduler.schedule(this, remaining, TimeUnit.NANOSECONDS);
          } else { // Mark as terminated and invoke callback
            myDueTime = -1;
            try {
              myCallback.call();
            } finally {
              myIsActive = false;
            }
          }
        }
      }
    }
  }

  private class TimeoutPredictionCleaner implements Callback {
    @Override
    public void call() {
      synchronized (LOCK) {
        if (!myPredictions.isEmpty()) {
          LOG.debug("TimeoutPredictionCleaner called");
          resetState();
        }
      }
    }
  }

  static class LatencyStatistics {
    private final LinkedList<Long> latencies = new LinkedList<>();
    private final int myBufferSize;

    LatencyStatistics(int bufferSize) {
      myBufferSize = bufferSize;
    }

    public void adjustLatency(@NotNull TypeAheadPrediction prediction) {
      latencies.add(System.nanoTime() - prediction.myCreatedTime);

      if (latencies.size() > myBufferSize) {
        latencies.removeFirst();
      }
    }

    public long getLatencyMedian() {
      if (latencies.isEmpty()) {
        throw new IllegalStateException("Tried to calculate latency with sample size of 0");
      }

      long[] sorted_latencies = latencies.stream().mapToLong(i -> i).sorted().toArray();

      if (sorted_latencies.length % 2 == 0) {
        return (sorted_latencies[sorted_latencies.length / 2 - 1] + sorted_latencies[sorted_latencies.length / 2]) / 2;
      } else {
        return sorted_latencies[sorted_latencies.length / 2];
      }
    }

    public long getMaxLatency() {
      if (latencies.isEmpty()) {
        throw new IllegalStateException("Tried to get max latency with sample size of 0");
      }

      return Collections.max(latencies);
    }

    private int getSampleSize() {
      return latencies.size();
    }
  }
}
