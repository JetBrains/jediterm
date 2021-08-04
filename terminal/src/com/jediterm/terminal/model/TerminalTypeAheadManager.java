package com.jediterm.terminal.model;

import com.google.common.base.Ascii;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.terminal.util.CharUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jediterm.terminal.model.TypeAheadTerminalModel.LineWithCursor.moveToWordBoundary;

public class TerminalTypeAheadManager {
  private static final long MAX_TERMINAL_DELAY = TimeUnit.MILLISECONDS.toNanos(3000);
  private static final int LATENCY_MIN_SAMPLES_TO_TURN_ON = 5;
  private static final double LATENCY_TOGGLE_OFF_THRESHOLD = 0.5;

  private static final Logger LOG = Logger.getLogger(TerminalTypeAheadManager.class);

  private final TypeAheadTerminalModel myTerminalModel;
  private final List<TypeAheadPrediction> myPredictions = new ArrayList<>();
  private final Debouncer myClearPredictionsDebouncer = new Debouncer(new Runnable() {
    @Override
    public void run() {
      myTerminalModel.lock();
      try {
        if (!myPredictions.isEmpty()) {
          LOG.debug("TimeoutPredictionCleaner called");
          resetState();
        }
      } finally {
        myTerminalModel.unlock();
      }
    }
  }, MAX_TERMINAL_DELAY);
  private final LatencyStatistics myLatencyStatistics = new LatencyStatistics();

  // if false, predictions will still be generated for latency statistics but won't be displayed
  private boolean myIsShowingPredictions = false;
  // if true, new predictions will only be generated if the user isn't typing for a certain amount of time
  private volatile boolean myOutOfSyncDetected = false;
  private long myLastTypedTime;
  // guards the terminal prompt. All predictions that try to move the cursor beyond leftmost cursor position are tentative
  private Integer myLeftMostCursorPosition = null;
  private boolean myIsNotPasswordPrompt = false;
  private boolean isAcknowledgeNeeded = true; // PredictionMatcher default state is disabled so we need to enable it

  public TerminalTypeAheadManager(@NotNull TypeAheadTerminalModel terminalModel) {
    myTerminalModel = terminalModel;
  }

  /**
   * @param lastMatchedPredictionID id of last successfully matched prediction.
   *                                If prediction match failed lastMatchedPredictionID should be -predictionID.
   *                                If terminal responded while there were no predictions lastMatchedPredictionID should be 0.
   */
  public void onTerminalStateChanged(@Nullable Integer lastMatchedPredictionID) {
    myTerminalModel.lock();
    try {
      if (lastMatchedPredictionID != null && lastMatchedPredictionID == 0) {
        isAcknowledgeNeeded = true;
      }
      if (!myTerminalModel.isTypeAheadEnabled() || myOutOfSyncDetected) return;

      if (!myPredictions.isEmpty()) {
        updateLeftMostCursorPosition(myTerminalModel.getCurrentLineWithCursor().myCursorX);
        myClearPredictionsDebouncer.call();
      }

      if (lastMatchedPredictionID == null) {
        myTerminalModel.applyPredictions(getVisiblePredictions());
        return;
      }

      if (lastMatchedPredictionID == 0) {
        myOutOfSyncDetected = true;
        resetState();
        return;
      }

      if (myPredictions.isEmpty() || Math.abs(lastMatchedPredictionID) < myPredictions.get(0).myID) {
        return;
      }

      if (myTerminalModel.isUsingAlternateBuffer()) {
        resetState();
        return;
      }

      if (lastMatchedPredictionID < 0) {
        myOutOfSyncDetected = true;
        resetState();
        return;
      }

      while (!myPredictions.isEmpty() && myPredictions.get(0).myID <= lastMatchedPredictionID) {
        TypeAheadPrediction prediction = myPredictions.remove(0);
        myLatencyStatistics.adjustLatency(prediction);
        if (prediction.getCharacterOrNull() != null) {
          myIsNotPasswordPrompt = true;
        }
      }
      myTerminalModel.applyPredictions(getVisiblePredictions());
    } finally {
      myTerminalModel.unlock();
    }
  }

  public void onKeyEvent(@NotNull TypeAheadEvent keyEvent) {
    if (!myTerminalModel.isTypeAheadEnabled()) return;
    myTerminalModel.lock();
    try {
      if (myTerminalModel.isUsingAlternateBuffer()) {
        resetState();
        return;
      }

      TypeAheadTerminalModel.LineWithCursor lineWithCursor = myTerminalModel.getCurrentLineWithCursor();

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

      updateLeftMostCursorPosition(lineWithCursor.myCursorX);


      if (myPredictions.isEmpty()) {
        myClearPredictionsDebouncer.call(); // start a timer that will clear predictions
      }
      TypeAheadPrediction prediction = createPrediction(lineWithCursor, keyEvent);
      myPredictions.add(prediction);
      if (isAcknowledgeNeeded) {
        isAcknowledgeNeeded = false;
        myTerminalModel.matchPrediction(new AcknowledgePrediction());
      }
      myTerminalModel.matchPrediction(prediction);
      myTerminalModel.applyPredictions(getVisiblePredictions());

      LOG.debug("Created " + keyEvent.myEventType + " prediction");
    } finally {
      myTerminalModel.unlock();
    }
  }

  public void onResize() {
    myTerminalModel.lock();
    try {
      resetState();
    } finally {
      myTerminalModel.unlock();
    }
  }

  public int getCursorX() {
    myTerminalModel.lock();
    try {
      if (myTerminalModel.isUsingAlternateBuffer() && !myPredictions.isEmpty()) {
        // otherwise, it will misreport cursor position
        resetState();
      }

      List<TypeAheadPrediction> predictions = getVisiblePredictions();

      int terminalCursorX = myTerminalModel.getCurrentLineWithCursor().myCursorX;
      if (predictions.isEmpty()) {
        return terminalCursorX;
      }
      int predictionCursorX = predictions.get(predictions.size() - 1).myPredictedCursorX;
      if (0 <= predictionCursorX && predictionCursorX < myTerminalModel.getTerminalWidth()) {
        return predictionCursorX;
      } else {
        return terminalCursorX;
      }
    } finally {
      myTerminalModel.unlock();
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
     * @see com.jediterm.terminal.TerminalKeyEncoder
     */
    public static @NotNull List<@NotNull TypeAheadEvent> fromByteArray(byte[] byteArray) {
      String stringRepresentation = new String(byteArray);
      if (isPrintableUnicode(stringRepresentation.charAt(0))) {
        return fromString(stringRepresentation);
      }

      return Collections.singletonList(fromSequence(byteArray));
    }

    public static @NotNull TypeAheadEvent fromChar(char ch) {
      if (isPrintableUnicode(ch)) {
        return new TypeAheadEvent(EventType.Character, ch);
      } else {
        return new TypeAheadEvent(EventType.Unknown);
      }
    }

    public static @NotNull List<@NotNull TypeAheadEvent> fromString(@NotNull String string) {
      ArrayList<@NotNull TypeAheadEvent> events = new ArrayList<>();

      if (string.charAt(0) == Ascii.ESC) {
        return Collections.singletonList(fromSequence(string.getBytes()));
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

    private static @NotNull TypeAheadEvent fromSequence(byte[] byteArray) {
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
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[', '1', ';', '3', 'D')) {
        return new TypeAheadEvent(EventType.AltLeftArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, 'f')) {
        return new TypeAheadEvent(EventType.AltRightArrow);
      } else if (compareByteArrays(byteArray, Ascii.ESC, '[', '1', ';', '3', 'C')) {
        return new TypeAheadEvent(EventType.AltRightArrow);
      } else {
        return new TypeAheadEvent(EventType.Unknown);
      }
    }
  }

  public abstract static class TypeAheadPrediction {
    public final long myCreatedTime;
    public final int myID;
    public final boolean myIsNotTentative;

    public final int myPredictedCursorX;

    private static int myNextID = 1;

    protected TypeAheadPrediction(int predictedCursorX, boolean isNotTentative) {
      this(myNextID++, predictedCursorX, isNotTentative);
    }

    protected TypeAheadPrediction(int id, int predictedCursorX, boolean isNotTentative) {
      myPredictedCursorX = predictedCursorX;
      myID = id;
      myIsNotTentative = isNotTentative;

      myCreatedTime = System.nanoTime();
    }

    public @Nullable Character getCharacterOrNull() {
      if (this instanceof CharacterPrediction) {
        return ((CharacterPrediction) this).myCharacter;
      }
      return null;
    }

    public abstract @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX);
  }

  public static class ClearPredictions extends TypeAheadPrediction {
    public ClearPredictions() {
      super(-1, true);
    }

    public ClearPredictions(int id) {
      super(id, -1, true);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      throw new IllegalStateException("ClearPredictions prediction shouldn't be matched against");
    }
  }

  public static class AcknowledgePrediction extends TypeAheadPrediction {
    public AcknowledgePrediction() {
      super(-1, true);
    }

    public AcknowledgePrediction(int id) {
      super(id, -1, true);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      throw new IllegalStateException("AcknowledgeClearPredictions prediction shouldn't be matched against");
    }
  }

  public static class HardBoundary extends TypeAheadPrediction {
    public HardBoundary() {
      super(-1, false);
    }

    public HardBoundary(int id) {
      super(id, -1, false);
    }

    @Override
    public @NotNull MatchResult matches(@NotNull TypeaheadStringReader stringReader, int cursorX) {
      return MatchResult.Failure;
    }
  }

  public static class CharacterPrediction extends TypeAheadPrediction {
    public final char myCharacter;
    public final @Nullable Character myPreviousCharacter;

    public CharacterPrediction(int predictedCursorX,
                               char character,
                               @Nullable Character previousCharacter,
                               boolean isNotTentative) {
      super(predictedCursorX, isNotTentative);
      myCharacter = character;
      myPreviousCharacter = previousCharacter;
    }

    public CharacterPrediction(int id,
                               int predictedCursorX,
                               char character,
                               @Nullable Character previousCharacter,
                               boolean isNotTentative) {
      super(id, predictedCursorX, isNotTentative);
      myCharacter = character;
      myPreviousCharacter = previousCharacter;
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
      } else if (stringReader.eatChar(myCharacter) != null) {
        result = MatchResult.Success;
      } else if (myPreviousCharacter != null) {
        String zshPrediction = "\b" + myPreviousCharacter + myCharacter;
        result = stringReader.eatGradually(zshPrediction);
      }

      if (result == MatchResult.Success && cursorX != myPredictedCursorX + stringReader.remainingLength()) {
        result = MatchResult.Failure;
      }
      return result;
    }
  }

  public static class BackspacePrediction extends TypeAheadPrediction {
    public BackspacePrediction(int predictedCursorX, boolean isNotTentative) {
      super(predictedCursorX, isNotTentative);
    }

    public BackspacePrediction(int id, int predictedCursorX, boolean isNotTentative) {
      super(id, predictedCursorX, isNotTentative);
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

  public static class CursorMovePrediction extends TypeAheadPrediction {
    public final int myAmount;
    public final CursorMoveDirection myDirection;
    public final int myCursorY;

    public CursorMovePrediction(int predictedCursorX,
                                int cursorY,
                                int amount,
                                CursorMoveDirection direction,
                                boolean isNotTentative) {
      super(predictedCursorX, isNotTentative);
      myAmount = amount;
      myDirection = direction;
      myCursorY = cursorY;
    }

    public CursorMovePrediction(int id,
                                int predictedCursorX,
                                int cursorY,
                                int amount,
                                CursorMoveDirection direction,
                                boolean isNotTentative) {
      super(id, predictedCursorX, isNotTentative);
      myAmount = amount;
      myDirection = direction;
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

  public enum CursorMoveDirection {
    Forward('C', 1),
    Back('D', -1);

    CursorMoveDirection(char ch, int delta) {
      myChar = ch;
      myDelta = delta;
    }

    public final char myChar;
    public final int myDelta;
  }

  public enum MatchResult {
    Success,
    Failure,
    Buffer,
  }

  private @Nullable TypeAheadPrediction getLastPrediction() {
    return myPredictions.isEmpty() ? null : myPredictions.get(myPredictions.size() - 1);
  }

  private @NotNull List<@NotNull TypeAheadPrediction> getVisiblePredictions() {
    int lastVisiblePredictionIndex = 0;
    while (lastVisiblePredictionIndex < myPredictions.size()
      && myPredictions.get(lastVisiblePredictionIndex).myIsNotTentative) {
      lastVisiblePredictionIndex++;
    }
    lastVisiblePredictionIndex--;

    return lastVisiblePredictionIndex >= 0 ? myPredictions.subList(0, lastVisiblePredictionIndex + 1) : Collections.emptyList();
  }

  private void updateLeftMostCursorPosition(int cursorX) {
    if (myLeftMostCursorPosition == null) {
      myLeftMostCursorPosition = cursorX;
    } else {
      myLeftMostCursorPosition = Math.min(myLeftMostCursorPosition, cursorX);
    }
  }

  private void resetState() {
    myTerminalModel.clearPredictions();
    myPredictions.clear();
    myTerminalModel.matchPrediction(new ClearPredictions());
    myLeftMostCursorPosition = null;
    myIsNotPasswordPrompt = false;
    myClearPredictionsDebouncer.terminateCall();
  }

  private void reevaluatePredictorState() {
    if (!myTerminalModel.isTypeAheadEnabled() || UIUtil.isWindows) {
      myIsShowingPredictions = false;
    } else if (myLatencyStatistics.getSampleSize() >= LATENCY_MIN_SAMPLES_TO_TURN_ON) {
      long latency = myLatencyStatistics.getLatencyMedian();

      if (latency >= myTerminalModel.getLatencyThreshold()) {
        myIsShowingPredictions = true;
      } else if (latency < myTerminalModel.getLatencyThreshold() * LATENCY_TOGGLE_OFF_THRESHOLD) {
        myIsShowingPredictions = false;
      }
    }
  }

  private @NotNull TypeAheadPrediction createPrediction(@NotNull TypeAheadTerminalModel.LineWithCursor initialLineWithCursor,
                                                        @NotNull TypeAheadEvent keyEvent) {
    if (getLastPrediction() instanceof HardBoundary) {
      return new HardBoundary();
    }

    TypeAheadTerminalModel.LineWithCursor newLineWCursor = initialLineWithCursor.copy();
    for (TypeAheadPrediction prediction : myPredictions) {
      newLineWCursor.applyPrediction(prediction);
    }

    switch (keyEvent.myEventType) {
      case Character:
        Character previousChar = null;
        if (newLineWCursor.myCursorX - 1 >= 0) {
          previousChar = newLineWCursor.myLineText.charAt(newLineWCursor.myCursorX - 1);
        }

        boolean hasCharacterPredictions = myPredictions.stream().anyMatch(
          (TypeAheadPrediction prediction) -> prediction.getCharacterOrNull() != null);

        Character ch = keyEvent.getCharacterOrNull();
        if (ch == null) {
          throw new IllegalStateException("KeyEvent type is Character but keyEvent.myCharacter == null");
        }

        return new CharacterPrediction(newLineWCursor.myCursorX + 1, ch, previousChar,
          (myIsNotPasswordPrompt || hasCharacterPredictions) && myIsShowingPredictions);
      case Backspace:
        return new BackspacePrediction(newLineWCursor.myCursorX - 1,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
            && myIsShowingPredictions);
      case LeftArrow:
      case RightArrow:
        CursorMoveDirection direction = keyEvent.myEventType == TypeAheadEvent.EventType.RightArrow ?
          CursorMoveDirection.Forward : CursorMoveDirection.Back;
        newLineWCursor.myCursorX += direction.myDelta;

        return new CursorMovePrediction(newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, 1, direction,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
            && newLineWCursor.myCursorX <= newLineWCursor.myLineText.length() && myIsShowingPredictions);
      case AltLeftArrow:
      case AltRightArrow:
        int oldCursorX = newLineWCursor.myCursorX;
        direction = keyEvent.myEventType == TerminalTypeAheadManager.TypeAheadEvent.EventType.AltRightArrow ?
          CursorMoveDirection.Forward : CursorMoveDirection.Back;
        newLineWCursor.myCursorX = moveToWordBoundary(newLineWCursor.myLineText.toString(), newLineWCursor.myCursorX, direction);

        int amount = Math.abs(newLineWCursor.myCursorX - oldCursorX);

        return new CursorMovePrediction(newLineWCursor.myCursorX, initialLineWithCursor.myCursorY, amount, direction,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursor.myCursorX
            && newLineWCursor.myCursorX <= newLineWCursor.myLineText.length() && myIsShowingPredictions);
      case Unknown:
        return new HardBoundary();
      default:
        throw new IllegalStateException("Unprocessed TypeAheadKeyboardEvent type");
    }
  }

  static class TypeaheadStringReader {
    final String myString;

    private int myIndex = 0;

    TypeaheadStringReader(@NotNull String string) {
      myString = string;
    }

    int getIndex() {
      return myIndex;
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

  private final static String CSI = (char) CharUtils.ESC + "[";

  private static class LatencyStatistics {
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
