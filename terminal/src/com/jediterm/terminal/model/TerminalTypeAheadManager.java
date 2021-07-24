package com.jediterm.terminal.model;

import com.google.common.base.Ascii;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalTypeAheadManager {
  private static final long MIN_CLEAR_PREDICTIONS_DELAY = TimeUnit.MILLISECONDS.toNanos(500);
  private static final long MAX_TERMINAL_DELAY = TimeUnit.MILLISECONDS.toNanos(3000);
  private static final int LATENCY_MIN_SAMPLES_TO_TURN_ON = 5;
  private static final double LATENCY_TOGGLE_OFF_THRESHOLD = 0.5;

  private static final Logger LOG = Logger.getLogger(TerminalTypeAheadManager.class);

  private final SettingsProvider mySettingsProvider;
  private final TerminalTextBuffer myTerminalTextBuffer;
  private final List<TypeAheadPrediction> myPredictions = new ArrayList<>();
  private final JediTerminal myTerminal;
  private final ClearPredictionsDebouncer myClearPredictionsDebouncer = new ClearPredictionsDebouncer();
  private final LatencyStatistics myLatencyStatistics = new LatencyStatistics();

  // if false, predictions will still be generated for latency statistics but won't be displayed
  private boolean myIsShowingPredictions = false;
  // if true, new predictions will only be generated if the user isn't typing for a certain amount of time
  private volatile boolean myOutOfSyncDetected = false;
  private long myLastTypedTime;
  // if we need more chars to match a prediction, we buffer remaining chars and wait for new ones
  private String myTerminalDataBuffer = "";
  // guards the terminal prompt. All predictions that try to move the cursor beyond leftmost cursor position are tentative
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
    if (!mySettingsProvider.getTypeAheadSettings().isEnabled() || myOutOfSyncDetected) return;
    LOG.debug("onTerminalData: " + data.replace("\u001b", "ESC")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u0007", "BEL")
            .replace(" ", "<S>")
            .replace("\b", "\\b"));

    String terminalData = myTerminalDataBuffer + data;
    myTerminalDataBuffer = "";
    TypeaheadStringReader terminalDataReader = new TypeaheadStringReader(terminalData);

    myTerminalTextBuffer.lock();
    try {
      TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();

      if (terminalLineWithCursor == null) {
        resetState();
        return;
      }

      if (!myPredictions.isEmpty()) {
        updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);
        myClearPredictionsDebouncer.call();
      }

      while (!myPredictions.isEmpty() && terminalDataReader.remainingLength() > 0) {
        if (checkNextPrediction(terminalDataReader, terminalLineWithCursor)) return;
      }

      if (myPredictions.isEmpty() && terminalDataReader.remainingLength() > 0) {
        myOutOfSyncDetected = true;
        resetState();
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public void onKeyEvent(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent) {
    if (!mySettingsProvider.getTypeAheadSettings().isEnabled()) return;
    myTerminalTextBuffer.lock();
    try {
      TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();

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
          return;
        }
      } else {
        myOutOfSyncDetected = false;
        reevaluatePredictorState();
      }

      updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);


      if (myPredictions.isEmpty()) {
        myClearPredictionsDebouncer.call(); // start a timer that will clear predictions
      }
      TypeAheadPrediction prediction = createPrediction(terminalLineWithCursor, keyEvent);
      myPredictions.add(prediction);
      redrawPredictions(terminalLineWithCursor);

      LOG.debug("Created " + keyEvent.myEventType + " prediction");
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public void onResize() {
    myTerminalTextBuffer.lock();
    try {
      resetState();
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public int getCursorX() {
    myTerminalTextBuffer.lock();
    try {
      if (myTerminalTextBuffer.isUsingAlternateBuffer() && !myPredictions.isEmpty()) {
        // otherwise, it will misreport cursor position
        resetState();
      }

      TypeAheadPrediction prediction = getLastVisiblePrediction();
      return prediction == null ? myTerminal.getCursorX() : prediction.myPredictedCursorX + 1;
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  public static class TypeAheadEvent {
    public enum EventType {
      Character,
      Backspace,
      LeftArrow,
      RightArrow,
      AltLeftArrow,
      AltRightArrow,
      Unknown,
    }

    public EventType myEventType;
    // if event is Character it will hold character
    private @Nullable Character myCharacter = null;

    public TypeAheadEvent(EventType eventType) {
      myEventType = eventType;
    }

    public TypeAheadEvent(EventType eventType, char ch) {
      myEventType = eventType;
      myCharacter = ch;
    }

    /**
     *  @see com.jediterm.terminal.TerminalKeyEncoder
     */
    public static @NotNull TerminalTypeAheadManager.TypeAheadEvent fromByteArray(byte[] byteArray) {
      String stringRepresentation = new String(byteArray);
      if (isPrintableUnicode(stringRepresentation.charAt(0))) {
        fromString(stringRepresentation);
      }

      if (compareByteArrays(byteArray, Ascii.DEL)) {
        return new TypeAheadEvent(EventType.Backspace);
      } else if (compareByteArrays(byteArray, Ascii.ESC, 'O', 'D')) {
        return new TypeAheadEvent(EventType.LeftArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[', 'D')) {
        return new TypeAheadEvent(EventType.LeftArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, 'O', 'C')) {
        return new TypeAheadEvent(EventType.RightArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[', 'C')) {
        return new TypeAheadEvent(EventType.RightArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, 'b')) {
        return new TypeAheadEvent(EventType.AltLeftArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[',  '1', ';', '3', 'D')) {
        return new TypeAheadEvent(EventType.AltLeftArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, 'f')) {
        return new TypeAheadEvent(EventType.AltRightArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[',  '1', ';', '3', 'C')) {
        return new TypeAheadEvent(EventType.AltRightArrow);
      } else {
       return new TypeAheadEvent(EventType.Unknown);
      }
    }

    public static @NotNull TerminalTypeAheadManager.TypeAheadEvent fromChar(char ch) {
      if (isPrintableUnicode(ch)) {
        return new TypeAheadEvent(EventType.Character, ch);
      } else {
        return new TypeAheadEvent(EventType.Unknown);
      }
    }

    public static @NotNull List<@NotNull TypeAheadEvent> fromString(@NotNull String string) {
      ArrayList<@NotNull TypeAheadEvent> events = new ArrayList<>();

      if (string.charAt(0) == Ascii.ESC) {
        return Collections.singletonList(fromByteArray(string.getBytes()));
      }

      for (char ch : string.toCharArray()) {
        TypeAheadEvent event = fromChar(ch);
        events.add(event);
        if (event.myEventType == EventType.Unknown) break;
      }

      return events;
    }

    public @Nullable Character getCharacterOrNull() {
      return myCharacter;
    }

    private static boolean compareByteArrays(byte[] byteArray, final int... bytesAsInt) {
      return Arrays.equals(byteArray, CharUtils.makeCode(bytesAsInt));
    }

    /**
     * copied from com.intellij.openapi.util.text.StringUtil
     */
    @Contract(pure = true)
    private static boolean isPrintableUnicode(char c) {
      int t = Character.getType(c);
      return t != Character.UNASSIGNED && t != Character.LINE_SEPARATOR &&
              t != Character.PARAGRAPH_SEPARATOR && t != Character.CONTROL &&
              t != Character.FORMAT && t != Character.PRIVATE_USE &&
              t != Character.SURROGATE;
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
    if (myTerminalTextBuffer.isUsingAlternateBuffer()) {
      return null;
    }

    int cursorX = myTerminal.getCursorX() - 1;
    int cursorY = myTerminal.getCursorY() - 1;
    TerminalLine terminalLine = myTerminalTextBuffer.getLine(cursorY);

    return new TerminalLineWithCursor(terminalLine, cursorX, cursorY);
  }

  private boolean checkNextPrediction(@NotNull TypeaheadStringReader terminalDataReader,
                                      @NotNull TerminalLineWithCursor terminalLineWithCursor) {
    TypeAheadPrediction nextPrediction = getNextPrediction();
    if (nextPrediction == null) {
      return true;
    }

    int readerIndexBeforeMatching = terminalDataReader.myIndex;

    String debugString;
    if (nextPrediction.myKeyEvent.myEventType == TypeAheadEvent.EventType.Character) {
      debugString = nextPrediction.myKeyEvent.myEventType + " char: " + nextPrediction.getCharacterOrNull();
    } else {
      debugString = nextPrediction.myKeyEvent.myEventType.toString();
    }
    switch (nextPrediction.matches(terminalDataReader, terminalLineWithCursor.myCursorX)) {
      case Success:
        LOG.debug("Matched successfully: " + debugString);
        myLatencyStatistics.adjustLatency(nextPrediction);

        if (nextPrediction.getCharacterOrNull() != null) {
          myIsNotPasswordPrompt = true;
        }

        myPredictions.remove(0);
        redrawPredictions(terminalLineWithCursor);
        break;
      case Buffer:
        LOG.debug("Buffered: " + debugString);
        myTerminalDataBuffer = terminalDataReader.myString.substring(readerIndexBeforeMatching);
        return true;
      case Failure:
        LOG.debug("Match failure: " + debugString);
        myOutOfSyncDetected = true;
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
    myTerminalTextBuffer.fireModelChangeEvent();
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
                                            @NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent) {
    TerminalLine terminalLine = terminalLineWithCursor.myTerminalLine;
    int newCursorX = terminalLineWithCursor.myCursorX;

    switch (keyEvent.myEventType) {
      case Character:
        Character ch = keyEvent.getCharacterOrNull();
        if (ch == null) {
          throw new IllegalStateException("TypeAheadKeyboardEvent.Character has myCharacter == null");
        }
        TextStyle typeAheadTextStyle = mySettingsProvider.getTypeAheadSettings().getTextStyle();
        terminalLine.writeString(newCursorX, new CharBuffer(ch, 1), typeAheadTextStyle);
        newCursorX++;
        break;
      case Backspace:
        if (newCursorX > 0) {
          newCursorX--;
          terminalLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
        }
        break;
      case LeftArrow:
      case RightArrow:
        int delta = keyEvent.myEventType == TypeAheadEvent.EventType.RightArrow ? 1 : -1;
        if (0 <= newCursorX + delta && newCursorX + delta < myTerminal.getTerminalWidth()) {
          newCursorX += delta;
        }
        break;
      case AltLeftArrow:
      case AltRightArrow:
        CursorMoveDirection direction = keyEvent.myEventType == TypeAheadEvent.EventType.AltRightArrow
                ? CursorMoveDirection.Forward : CursorMoveDirection.Back;
        newCursorX = moveToWordBoundary(terminalLine.getText(), newCursorX, direction);
        break;
      default:
        throw new IllegalStateException("Events should be filtered but keyEvent is " + keyEvent);
    }

    terminalLineWithCursor.myCursorX = newCursorX;
  }

  private void resetState() {
    boolean fireChange = !myPredictions.isEmpty();

    myTerminalTextBuffer.clearTypeAheadPredictions();
    myPredictions.clear();
    myTerminalDataBuffer = "";
    myLeftMostCursorPosition = null;
    myIsNotPasswordPrompt = false;
    myClearPredictionsDebouncer.terminateCall();

    if (fireChange) {
      myTerminalTextBuffer.fireModelChangeEvent();
    }
  }

  private void reevaluatePredictorState() {
    TerminalTypeAheadSettings settings = mySettingsProvider.getTypeAheadSettings();

    if (!settings.isEnabled() || UIUtil.isWindows) {
      myIsShowingPredictions = false;
    } else if (myLatencyStatistics.getSampleSize() >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
      long latency = myLatencyStatistics.getLatencyMedian();

      if (latency >= settings.getLatencyThreshold()) {
        myIsShowingPredictions = true;
      } else if (latency < settings.getLatencyThreshold() * LATENCY_TOGGLE_OFF_THRESHOLD) {
        myIsShowingPredictions = false;
      }
    }
  }

  private @NotNull TypeAheadPrediction createPrediction(@NotNull TerminalLineWithCursor initialLineWithCursor,
                                                        @NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent) {
    if (getLastPrediction() instanceof HardBoundary) {
      return new HardBoundary(keyEvent, -1);
    }

    TerminalLineWithCursor newLineWCursor = initialLineWithCursor.copy();
    for (TypeAheadPrediction prediction : myPredictions) {
      updateTerminalLinePrediction(newLineWCursor, prediction.myKeyEvent);
    }

    switch (keyEvent.myEventType) {
      case Character:
        Character previousChar = null;
        if (newLineWCursor.myCursorX - 1 >= 0) {
          previousChar = newLineWCursor.myTerminalLine.charAt(newLineWCursor.myCursorX - 1);
        }

        updateTerminalLinePrediction(newLineWCursor, keyEvent);

        boolean hasCharacterPredictions = myPredictions.stream().anyMatch(
                (TypeAheadPrediction prediction) -> prediction.getCharacterOrNull() != null);

        return constructPrediction(
                new CharacterPrediction(keyEvent, newLineWCursor.myCursorX, previousChar),
                myIsNotPasswordPrompt || hasCharacterPredictions
        );
      case Backspace:
        updateTerminalLinePrediction(newLineWCursor, keyEvent);

        return constructPrediction(
                new BackspacePrediction(keyEvent, newLineWCursor.myCursorX),
                myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
        );
      case LeftArrow:
      case RightArrow:
        updateTerminalLinePrediction(newLineWCursor, keyEvent);

        return constructPrediction(
                new CursorMovePrediction(keyEvent, newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, 1),
                myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
                        && newLineWCursor.myCursorX <= newLineWCursor.myTerminalLine.getText().length()
        );
      case AltLeftArrow:
      case AltRightArrow:
        int oldCursorX = newLineWCursor.myCursorX;
        updateTerminalLinePrediction(newLineWCursor, keyEvent);

        int amount = Math.abs(newLineWCursor.myCursorX - oldCursorX);

        return constructPrediction(
                new CursorMovePrediction(keyEvent, newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, amount),
                myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
                        && newLineWCursor.myCursorX <= newLineWCursor.myTerminalLine.getText().length()
        );
      case Unknown:
        return new HardBoundary(keyEvent, -1);
      default:
        throw new IllegalStateException("Unprocessed TypeAheadKeyboardEvent type");
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

    private final Pattern STYLE_REGEX = Pattern.compile("^(\\x1b\\[[0-9;]*m).*");

    @Nullable String eatStyle() {
      Matcher matcher = STYLE_REGEX.matcher(myString.substring(myIndex));

      if (matcher.find()) {
        String match = matcher.group(1);
        myIndex += match.length();
        return match;
      } else {
        return null;
      }
    }
  }

  private final String CSI = (char) CharUtils.ESC + "[";

  private abstract static class TypeAheadPrediction {
    public final long myCreatedTime;

    protected final int myPredictedCursorX;
    protected TypeAheadEvent myKeyEvent;

    private TypeAheadPrediction(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent,
                                int predictedCursorX) {
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
    private HardBoundary(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent, int predictedCursorX) {
      super(keyEvent, predictedCursorX);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      return MatchResult.Failure;
    }
  }

  private static class TentativeBoundary extends TypeAheadPrediction {
    final TypeAheadPrediction myInnerPrediction;

    private TentativeBoundary(@NotNull TypeAheadPrediction innerPrediction) {
      super(innerPrediction.myKeyEvent, innerPrediction.myPredictedCursorX);
      myInnerPrediction = innerPrediction;
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      return myInnerPrediction.matches(stringReader, cursorX);
    }
  }

  private class CharacterPrediction extends TypeAheadPrediction {
    @Nullable Character myPreviousChar;

    private CharacterPrediction(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent,
                                int predictedCursorX,
                                @Nullable Character previousChar) {
      super(keyEvent, predictedCursorX);
      myPreviousChar = previousChar;
    }

    char getCharacter() {
      Character ch = myKeyEvent.getCharacterOrNull();
      if (ch == null) {
        throw new IllegalStateException("TypeAheadKeyboardEvent.Character has myCharacter == null");
      }
      return ch;
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      // remove any styling CSI before checking the char
      String eaten;
      do {
        eaten = stringReader.eatStyle();
      } while (eaten != null && !eaten.isEmpty());

      MatchResult result = MatchResult.Failure;

      if (stringReader.isEOF()) {
        result = MatchResult.Buffer;
      } else if (stringReader.eatChar(getCharacter()) != null) {
        result = MatchResult.Success;
      } else if (myPreviousChar != null) {
        String zshPrediction = "\b" + myPreviousChar + getCharacter();
        result = stringReader.eatGradually(zshPrediction);
      }

      if (result == MatchResult.Success && cursorX != myPredictedCursorX + stringReader.remainingLength()) {
        result = MatchResult.Failure;
      }
      return result;
    }
  }

  private class BackspacePrediction extends TypeAheadPrediction {
    private BackspacePrediction(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent,
                                int predictedCursorX) {
      super(keyEvent, predictedCursorX);
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

    private CursorMovePrediction(@NotNull TerminalTypeAheadManager.TypeAheadEvent keyEvent,
                                 int predictedCursorX,
                                 int cursorY,
                                 int amount) {
      super(keyEvent, predictedCursorX);
      myAmount = amount;
      if (keyEvent.myEventType == TypeAheadEvent.EventType.LeftArrow ||
              keyEvent.myEventType == TypeAheadEvent.EventType.AltLeftArrow) {
        myDirection = CursorMoveDirection.Back;
      } else {
        myDirection = CursorMoveDirection.Forward;
      }
      myCursorY = cursorY;
    }

    private @NotNull MatchResult _matches(TypeaheadStringReader stringReader) {
      MatchResult r1 = stringReader.eatGradually((CSI + myDirection.myChar).repeat(myAmount));
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

  private class ClearPredictionsDebouncer {
    private final ScheduledExecutorService myScheduler = Executors.newScheduledThreadPool(1);
    private ClearPredictions myClearPredictions = null;
    private final Object myLock = new Object();

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

        if (myClearPredictions != null) {
          myClearPredictions.cancel();
        }

        myClearPredictions = new ClearPredictions(interval);
        myScheduler.schedule(myClearPredictions, interval, TimeUnit.NANOSECONDS);
      }
    }

    public void terminateCall() {
      if (myClearPredictions != null) {
        myClearPredictions.cancel();
      }
    }

    private class ClearPredictions implements Runnable {
      private final long myDueTime;
      private boolean myIsActive = true;

      public ClearPredictions(long interval) {
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
            myIsActive = false;
            runNow();
          }
        }
      }

      private void runNow() {
        myTerminalTextBuffer.lock();
        try {
          if (!myPredictions.isEmpty()) {
            LOG.debug("TimeoutPredictionCleaner called");
            resetState();
          }
        } finally {
          myTerminalTextBuffer.unlock();
        }
      }
    }
  }

  static class LatencyStatistics {
    private static final int LATENCY_BUFFER_SIZE = 30;
    private final LinkedList<Long> latencies = new LinkedList<>();

    public void adjustLatency(@NotNull TypeAheadPrediction prediction) {
      latencies.add(System.nanoTime() - prediction.myCreatedTime);

      if (latencies.size() > LATENCY_BUFFER_SIZE) {
        latencies.removeFirst();
      }
    }

    public long getLatencyMedian() {
      if (latencies.isEmpty()) {
        throw new IllegalStateException("Tried to calculate latency with sample size of 0");
      }

      Long[] sortedLatencies = latencies.stream().sorted().toArray(Long[]::new);

      if (sortedLatencies.length % 2 == 0) {
        return (sortedLatencies[sortedLatencies.length / 2 - 1] + sortedLatencies[sortedLatencies.length / 2]) / 2;
      } else {
        return sortedLatencies[sortedLatencies.length / 2];
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
