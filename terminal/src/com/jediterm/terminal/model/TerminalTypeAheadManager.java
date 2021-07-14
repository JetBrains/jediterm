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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalTypeAheadManager {

  private static final int AUTO_SYNC_DELAY = 2000;
  private static final int AUTO_CLEAR_PREDICTIONS_DELAY = 1000;
  private static final Logger LOG = Logger.getLogger(TerminalTypeAheadManager.class);

  private final Object LOCK = new Object();
  private final SettingsProvider mySettingsProvider;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final List<TerminalModelListener> myListeners = new CopyOnWriteArrayList<>();
  private final List<TypeAheadPrediction> myPredictions = new ArrayList<>();
  private final JediTerminal myTerminal;
  private final Debouncer myDeferClearingPredictions;

  private TextStyle myTypeAheadTextStyle;
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

    myDeferClearingPredictions = new Debouncer(new TimeoutPredictionCleaner(), AUTO_CLEAR_PREDICTIONS_DELAY);
  }

  public void onTerminalData(String data) {
    System.out.print("OnBeforeProcessChar: ");
    System.out.println(data.replace("\u001b", "ESC")
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

      while (!myPredictions.isEmpty() && terminalDataReader.remaining() > 0) {
        // TODO: vscode omits some char sequences from sending to the prediction engine, maybe we should too.

        if (checkNextPrediction(terminalDataReader, terminalLineWithCursor)) return;
      }
    }
  }

  public void onKeyEvent(KeyEvent keyEvent) {
    if (!mySettingsProvider.isTypeAheadEnabled()) {
      return;
    }

    TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();

    synchronized (LOCK) {
      if (terminalLineWithCursor == null) {
        resetState();
        return;
      }

      long prevTypedTime = myLastTypedTime;
      myLastTypedTime = System.nanoTime();
      if (myOutOfSyncDetected && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - prevTypedTime) < AUTO_SYNC_DELAY) {
        resetState();
        return;
      }

      updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);

      myOutOfSyncDetected = false;
      System.out.println("Typed " + (char) keyEvent.getKeyCode() + " (" + keyEvent.getKeyCode() + ")");

      if (myPredictions.isEmpty()) {
        myDeferClearingPredictions.call(); // start a timer that will clear predictions
      }
      TypeAheadPrediction prediction = createPrediction(terminalLineWithCursor, keyEvent);
      myPredictions.add(prediction);
      redrawPredictions(terminalLineWithCursor);
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

    if (lastVisiblePredictionIndex >= 0) {
      return myPredictions.get(lastVisiblePredictionIndex);
    }

    return null;
  }

  private @Nullable TerminalLineWithCursor getTerminalLineWithCursor() {
    myTerminalTextBuffer.lock();
    int cursorX, cursorY;
    TerminalLine terminalLine;

    try {
      if (myTerminalTextBuffer.isUsingAlternateBuffer()) {
        return null;
      }

      cursorX = myTerminal.getCursorX() - 1;
      cursorY = myTerminal.getCursorY() - 1;
      terminalLine = myTerminalTextBuffer.getLine(cursorY);

      return new TerminalLineWithCursor(terminalLine, cursorX, cursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private boolean checkNextPrediction(TypeaheadStringReader terminalDataReader, TerminalLineWithCursor terminalLineWithCursor) {
    TypeAheadPrediction nextPrediction = getNextPrediction();
    if (nextPrediction == null) {
      return true;
    }

    int readerIndexBeforeMatching = terminalDataReader.myIndex;
    switch (nextPrediction.matches(terminalDataReader, terminalLineWithCursor.myCursorX)) {
      case Success:
        System.out.println("Match: success");
        if (nextPrediction.getCharacterOrNull() != null) {
          myIsNotPasswordPrompt = true;
        }

        myLastSuccessfulPrediction = myPredictions.get(0);
        myPredictions.remove(0);
        nextPrediction.myInitialLine.setTypeAheadLine(null);
        redrawPredictions(terminalLineWithCursor);
        break;
      case Buffer:
        System.out.println("Match: buffer");
        myTerminalDataBuffer = terminalDataReader.myString.substring(readerIndexBeforeMatching);
        return true;
      case Failure: // TODO: onFailure needs rework
        System.out.println("Match: failure");
        myOutOfSyncDetected = true;
        resetState();
    }
    return false;
  }

  private void redrawPredictions(TerminalLineWithCursor terminalLineWithCursor) {
    TerminalLineWithCursor newTerminalLineWithCursor = terminalLineWithCursor.copy();

    int lastVisiblePredictionIndex = 0;
    while (lastVisiblePredictionIndex < myPredictions.size()
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof TentativeBoundary)
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof HardBoundary)) {
      updateTerminalLinePrediction(newTerminalLineWithCursor, myPredictions.get(lastVisiblePredictionIndex).myKeyEvent);
      lastVisiblePredictionIndex++;
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

    TerminalLineWithCursor copy() {
      return new TerminalLineWithCursor(myTerminalLine.copy(), myCursorX, myCursorY);
    }
  }

  private void updateTerminalLinePrediction(TerminalLineWithCursor terminalLineWithCursor, KeyEvent keyEvent) {
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
      newCursorX += keyEvent.getKeyCode() == KeyEvent.VK_RIGHT ? 1 : -1;
    } else if (KeyEventHelper.isAltArrowKey(keyEvent)) {
      CursorMoveDirection direction = keyEvent.getKeyCode() == KeyEvent.VK_RIGHT ? CursorMoveDirection.Forward : CursorMoveDirection.Back;
      newCursorX = moveToWordBoundary(terminalLine.getText(), newCursorX, direction);
    } else if (false) { // TODO: delete or implement del
      terminalLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
    } else { // TODO: del, alt+>, alt+<, enter
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

      boolean hasCharacterPredictions = myPredictions.stream().anyMatch((TypeAheadPrediction prediction) ->
              prediction.getCharacterOrNull() != null);

      return constructPrediction(
              new CharacterPrediction(initialLine, keyEvent, newLineWCursor.myCursorX),
              myIsNotPasswordPrompt || hasCharacterPredictions
      );
    } else if (KeyEventHelper.isBackspace(keyEvent)) {
      updateTerminalLinePrediction(newLineWCursor, keyEvent);

      return constructPrediction(
              new BackspacePrediction(initialLine, keyEvent, newLineWCursor.myCursorX, true), // TODO: delete myIsLastChar or fill with correct data
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
    } else { // TODO: del, alt+>, alt+<.
      return new HardBoundary(initialLine, keyEvent, -1);
    }
  }

  private @NotNull TypeAheadPrediction constructPrediction(TypeAheadPrediction prediction, boolean isNotTentative) {
    if (isNotTentative) {
      return prediction;
    }

    return new TentativeBoundary(prediction);
  }

  private int moveToWordBoundary(String text, int index, CursorMoveDirection direction) {
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
      } else {
        if (ateLeadingWhitespace) {
          break;
        }
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

  private static class TypeaheadStringReader { // TODO: copied from vscode, needs polish/deleting
    private int myIndex = 0;

    final String myString;

    TypeaheadStringReader(String string) {
      myString = string;
    }

    int remaining() {
      return myString.length() - myIndex;
    }

    boolean eof() {
      return myString.length() == myIndex;
    }

    String rest() {
      return myString.substring(myIndex);
    }

    Character eatChar(char character) {
      if (myString.charAt(myIndex) != character) {
        return null;
      }

      myIndex++;
      return character;
    }

    String eatStr(String substr) {
      if (!myString.substring(myIndex, substr.length()).equals(substr)) {
        return null;
      }

      myIndex += substr.length();
      return substr;
    }

    MatchResult eatGradually(String substr) {
      int prevIndex = myIndex;

      for (int i = 0; i < substr.length(); ++i) {
        if (i > 0 && eof()) {
          return MatchResult.Buffer;
        }

        if (eatChar(substr.charAt(i)) == null) {
          this.myIndex = prevIndex;
          return MatchResult.Failure;
        }
      }

      return MatchResult.Success;
    }

    String eatRe(Pattern pattern) {
      // TODO: verify correctness
      Matcher matcher = pattern.matcher(myString.substring(myIndex));
      if (!matcher.matches()) {
        return null;
      }

      java.util.regex.MatchResult match = matcher.toMatchResult();


      myIndex += matcher.end();
      return match.group();
    }

    Integer eatCharCode(int min) {
      return eatCharCode(min, min + 1);
    }

    Integer eatCharCode(int min, int max) {
      int code = myString.charAt(this.myIndex);
      if (code < min || code >= max) {
        return null;
      }

      this.myIndex++;
      return code;
    }
  }

  private final String CSI = (char) CharUtils.ESC + "[";

  private abstract static class TypeAheadPrediction {
    protected final TerminalLine myInitialLine;
    protected final int myPredictedCursorX;
    protected KeyEvent myKeyEvent;

    private TypeAheadPrediction(@NotNull TerminalLine initialLine,
                                @NotNull KeyEvent keyEvent,
                                int predictedCursorX) {
      myInitialLine = initialLine;
      myKeyEvent = keyEvent;
      myPredictedCursorX = predictedCursorX;
    }

    public abstract boolean getClearAfterTimeout();

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

    public abstract @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX);
  }

  private static class HardBoundary extends TypeAheadPrediction {
    private HardBoundary(@NotNull TerminalLine initialLine, @NotNull KeyEvent keyEvent, int predictedCursorX) {
      super(initialLine, keyEvent, predictedCursorX);
    }

    @Override
    public boolean getClearAfterTimeout() {
      return false;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX) {
      return MatchResult.Failure;
    }
  }

  private static class TentativeBoundary extends TypeAheadPrediction {
    final TypeAheadPrediction myInnerPrediction;

    private TentativeBoundary(TypeAheadPrediction innerPrediction) {
      super(innerPrediction.myInitialLine, innerPrediction.myKeyEvent, innerPrediction.myPredictedCursorX);
      myInnerPrediction = innerPrediction;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX) {
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
    public boolean getClearAfterTimeout() {
      return true;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX) {
      // remove any styling CSI before checking the char
      String eaten;
      Pattern CSI_STYLE_RE = Pattern.compile("^\\x1b\\[[0-9;]*m"); // TODO: test regex
      do {
        eaten = stringReader.eatRe(CSI_STYLE_RE);
      } while (eaten != null && !eaten.isEmpty());

      MatchResult result = MatchResult.Failure;

      if (stringReader.eof()) {
        result = MatchResult.Buffer;
      } else if (stringReader.eatChar(getCharacter()) != null) {
        result = MatchResult.Success;
      } else if (myLastSuccessfulPrediction != null && myLastSuccessfulPrediction.getCharacterOrNull() != null) {
        // vscode #112842
        String zshPrediction = "\b" + myLastSuccessfulPrediction.getCharacterOrNull() + getCharacter();
        result = stringReader.eatGradually(zshPrediction);
      }

      if (result == MatchResult.Success && cursorX != myPredictedCursorX + stringReader.remaining()) {
        result = MatchResult.Failure;
      }
      return result;
    }
  }

  private class BackspacePrediction extends TypeAheadPrediction {
    boolean myIsLastChar;

    private BackspacePrediction(@NotNull TerminalLine initialLine,
                                @NotNull KeyEvent keyEvent,
                                int predictedCursorX,
                                boolean isLastChar) {
      super(initialLine, keyEvent, predictedCursorX);
      myIsLastChar = isLastChar;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX) {
      MatchResult result = MatchResult.Failure;

      if (myIsLastChar) {
        MatchResult r1 = stringReader.eatGradually("\b" + CSI + "K");
        if (r1 != MatchResult.Failure) {
          result = r1;
        } else {
          result = stringReader.eatGradually("\b \b");
        }
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
                                 int amount
    ) {
      super(initialLine, keyEvent, predictedCursorX);
      myAmount = amount;
      myDirection = keyEvent.getKeyCode() == KeyEvent.VK_LEFT ? CursorMoveDirection.Back : CursorMoveDirection.Forward;
      myCursorY = cursorY;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
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
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader, int cursorX) {
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

    static boolean isKeyTypedEvent(KeyEvent keyEvent) {
      return keyEvent.getKeyCode() == KeyEvent.VK_UNDEFINED;
    }

    static boolean isBackspace(KeyEvent keyEvent) {
      return keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE
              && (keyEvent.getModifiersEx() & allModifiersMask) == 0;
    }

    static boolean isArrowKey(KeyEvent keyEvent) {
      return (keyEvent.getKeyCode() == KeyEvent.VK_LEFT || keyEvent.getKeyCode() == KeyEvent.VK_RIGHT)
              && (keyEvent.getModifiersEx() & allModifiersMask) == 0;
    }

    static boolean isAltArrowKey(KeyEvent keyEvent) {
      return (keyEvent.getKeyCode() == KeyEvent.VK_LEFT || keyEvent.getKeyCode() == KeyEvent.VK_RIGHT)
              && (keyEvent.getModifiersEx() & allModifiersMask) == InputEvent.ALT_DOWN_MASK;
    }
  }

  public interface Callback {
    void call();
  }

  // inspired by https://stackoverflow.com/questions/4742210/implementing-debounce-in-java
  public static class Debouncer {
    private final ScheduledExecutorService myScheduler = Executors.newScheduledThreadPool(1);
    private TimerTask myTimerTask = null;
    private final Callback myCallback;
    private final int myInterval;
    private final Object myLock = new Object();

    public Debouncer(Callback callback, int interval) {
      this.myCallback = callback;
      this.myInterval = interval;
    }

    public void call() {
      synchronized (myLock) {
        do {
          if (myTimerTask == null) {
            myTimerTask = new TimerTask();
            myScheduler.schedule(myTimerTask, myInterval, TimeUnit.MILLISECONDS);
          }
        } while (myTimerTask == null || !myTimerTask.extend());
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
      private boolean myCanceled;

      public TimerTask() {
        extend();
      }

      public boolean extend() {
        if (myDueTime < 0) // Task has been shutdown
          return false;
        myDueTime = System.currentTimeMillis() + myInterval;
        return true;
      }

      public void cancel() {
        synchronized (myLock) {
          myCanceled = true;
          if (this == myTimerTask) {
            myTimerTask = null;
          }
        }
      }

      public void run() {
        synchronized (myLock) {
          if (myCanceled) {
            return;
          }

          long remaining = myDueTime - System.currentTimeMillis();
          if (remaining > 0) { // Re-schedule task
            myScheduler.schedule(this, remaining, TimeUnit.MILLISECONDS);
          } else { // Mark as terminated and invoke callback
            myDueTime = -1;
            try {
              myCallback.call();
            } finally {
              myTimerTask = null;
            }
          }
        }
      }
    }
  }

  public class TimeoutPredictionCleaner implements Callback {
    @Override
    public void call() {
      synchronized (LOCK) {
        if (!myPredictions.isEmpty()) {
          System.out.println("DEBOUNCE!");
          resetState();
        }
      }
    }
  }
}
