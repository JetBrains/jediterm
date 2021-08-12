package com.jediterm.terminal.model;

import com.google.common.base.Ascii;
import com.jediterm.terminal.util.CharUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

import com.jediterm.terminal.model.TypeAheadTerminalModel.LineWithCursorX;
import static com.jediterm.terminal.model.TypeAheadTerminalModel.LineWithCursorX.moveToWordBoundary;

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

  public TerminalTypeAheadManager(@NotNull TypeAheadTerminalModel terminalModel) {
    myTerminalModel = terminalModel;
  }

  public void onTerminalStateChanged() {
    if (!myTerminalModel.isTypeAheadEnabled() || myOutOfSyncDetected) return;

    myTerminalModel.lock();
    try {
      if (myTerminalModel.isUsingAlternateBuffer()) {
        resetState();
        return;
      }
      TypeAheadTerminalModel.LineWithCursorX lineWithCursorX = myTerminalModel.getCurrentLineWithCursor();

      if (!myPredictions.isEmpty()) {
        updateLeftMostCursorPosition(lineWithCursorX.myCursorX);
        myClearPredictionsDebouncer.call();
      }

      ArrayList<TypeAheadPrediction> removedPredictions = new ArrayList<>();
      while (!myPredictions.isEmpty() && !lineWithCursorX.equals(myPredictions.get(0).myPredictedLineWithCursorX)) {
        removedPredictions.add(myPredictions.remove(0));
      }

      if (myPredictions.isEmpty()) {
        myOutOfSyncDetected = true;
        resetState();
      } else {
        removedPredictions.add(myPredictions.remove(0));
        for (TypeAheadPrediction prediction : removedPredictions) {
          myLatencyStatistics.adjustLatency(prediction);

          if (prediction instanceof CharacterPrediction) {
            myIsNotPasswordPrompt = true;
          }

        }
        applyPredictions();
      }
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

      TypeAheadTerminalModel.LineWithCursorX lineWithCursorX = myTerminalModel.getCurrentLineWithCursor();

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

      updateLeftMostCursorPosition(lineWithCursorX.myCursorX);

      if (myPredictions.isEmpty()) {
        myClearPredictionsDebouncer.call(); // start a timer that will clear predictions
      }
      TypeAheadPrediction prediction = createPrediction(lineWithCursorX, keyEvent);
      myPredictions.add(prediction);
      applyPredictions();

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

      int cursorX = predictions.isEmpty() ?
        myTerminalModel.getCurrentLineWithCursor().myCursorX :
        predictions.get(predictions.size() - 1).myPredictedLineWithCursorX.myCursorX;
      return cursorX + 1;
    } finally {
      myTerminalModel.unlock();
    }
  }

  public static class TypeAheadEvent {
    public enum EventType {
      Character,
      Backspace,
      AltBackspace,
      LeftArrow,
      RightArrow,
      AltLeftArrow,
      AltRightArrow,
      Delete,
      Home,
      End,
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
      return new TypeAheadEvent(sequenceToEventType.getOrDefault(new Sequence(byteArray), EventType.Unknown));
    }

    private static final Map<Sequence, EventType> sequenceToEventType = Map.ofEntries(
      Map.entry(new Sequence(Ascii.ESC, '[', '3', '~'), EventType.Delete),
      Map.entry(new Sequence(Ascii.DEL), EventType.Backspace),
      Map.entry(new Sequence(Ascii.ESC, Ascii.DEL), EventType.AltBackspace),
      Map.entry(new Sequence(Ascii.ESC, 'O', 'D'), EventType.LeftArrow),
      Map.entry(new Sequence(Ascii.ESC, '[', 'D'), EventType.LeftArrow),
      Map.entry(new Sequence(Ascii.ESC, 'O', 'C'), EventType.RightArrow),
      Map.entry(new Sequence(Ascii.ESC, '[', 'C'), EventType.RightArrow),
      Map.entry(new Sequence(Ascii.ESC, 'b'), EventType.AltLeftArrow),
      Map.entry(new Sequence(Ascii.ESC, '[', '1', ';', '3', 'D'), EventType.AltLeftArrow),
      // It's ctrl+left arrow, but behaves just the same
      Map.entry(new Sequence(Ascii.ESC, '[',  '1', ';', '5', 'D'), EventType.AltLeftArrow),
      Map.entry(new Sequence(Ascii.ESC, 'f'), EventType.AltRightArrow),
      Map.entry(new Sequence(Ascii.ESC, '[', '1', ';', '3', 'C'), EventType.AltRightArrow),
      // It's ctrl+right arrow, but behaves just the same
      Map.entry(new Sequence(Ascii.ESC, '[',  '1', ';', '5', 'C'), EventType.AltRightArrow),
      Map.entry(new Sequence(Ascii.ESC, '[', 'H'), EventType.Home),
      Map.entry(new Sequence(Ascii.ESC, 'O', 'H'), EventType.Home),
      Map.entry(new Sequence(Ascii.ESC, '[', 'F'), EventType.End),
      Map.entry(new Sequence(Ascii.ESC, 'O', 'F'), EventType.End)
    );

    private static class Sequence {
      private final byte[] mySequence;

      Sequence(final int... bytesAsInt) {
        mySequence = CharUtils.makeCode(bytesAsInt);
      }

      Sequence(final byte[] sequence) {
        mySequence = sequence;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sequence)) return false;
        Sequence sequence = (Sequence) o;
        return Arrays.equals(mySequence, sequence.mySequence);
      }

      @Override
      public int hashCode() {
        return Arrays.hashCode(mySequence);
      }
    }
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
    myLeftMostCursorPosition = null;
    myIsNotPasswordPrompt = false;
    myClearPredictionsDebouncer.terminateCall();
  }

  private void reevaluatePredictorState() {
    if (!myTerminalModel.isTypeAheadEnabled()) {
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

  private void applyPredictions() {
    List<TypeAheadPrediction> predictions = getVisiblePredictions();
    myTerminalModel.clearPredictions();
    for (TypeAheadPrediction prediction : predictions) {
      int predictedCursorX = prediction.myPredictedLineWithCursorX.myCursorX;
      if (prediction instanceof CharacterPrediction) {
        myTerminalModel.insertCharacter(((CharacterPrediction) prediction).myCharacter, predictedCursorX - 1);
        myTerminalModel.moveCursor(predictedCursorX);
      } else if (prediction instanceof BackspacePrediction) {
        myTerminalModel.moveCursor(predictedCursorX);
        myTerminalModel.removeCharacters(predictedCursorX, ((BackspacePrediction) prediction).myAmount);
      } else if (prediction instanceof CursorMovePrediction) {
        myTerminalModel.moveCursor(predictedCursorX);
      } else if (prediction instanceof DeletePrediction) {
        myTerminalModel.removeCharacters(predictedCursorX, 1);
      } else {
        throw new IllegalStateException("Unsupported prediction type");
      }
    }
    myTerminalModel.forceRedraw();
  }

  private @NotNull TypeAheadPrediction createPrediction(@NotNull TypeAheadTerminalModel.LineWithCursorX initialLineWithCursorX,
                                                        @NotNull TypeAheadEvent keyEvent) {
    if (getLastPrediction() instanceof HardBoundary) {
      return new HardBoundary();
    }

    LineWithCursorX newLineWCursorX;
    TypeAheadPrediction lastPrediction = getLastPrediction();
    if (lastPrediction != null) {
      newLineWCursorX = lastPrediction.myPredictedLineWithCursorX.copy();
    } else {
      newLineWCursorX = initialLineWithCursorX.copy();
    }

    switch (keyEvent.myEventType) {
      case Character:
        if (newLineWCursorX.myCursorX >= myTerminalModel.getTerminalWidth()) {
          return new HardBoundary();
        }

        boolean hasCharacterPredictions = myPredictions.stream().anyMatch(
          (TypeAheadPrediction prediction) -> prediction instanceof CharacterPrediction);

        Character ch = keyEvent.getCharacterOrNull();
        if (ch == null) {
          throw new IllegalStateException("KeyEvent type is Character but keyEvent.myCharacter == null");
        }

        if (newLineWCursorX.myLineText.length() < newLineWCursorX.myCursorX) {
          newLineWCursorX.myLineText.append(" ".repeat(newLineWCursorX.myCursorX - newLineWCursorX.myLineText.length()));
        }
        newLineWCursorX.myLineText.insert(newLineWCursorX.myCursorX, ch);
        newLineWCursorX.myCursorX++;

        if (newLineWCursorX.myLineText.length() > myTerminalModel.getTerminalWidth()) {
          newLineWCursorX.myLineText.delete(myTerminalModel.getTerminalWidth(), newLineWCursorX.myLineText.length());
        }

        return new CharacterPrediction(newLineWCursorX, ch,
          (myIsNotPasswordPrompt || hasCharacterPredictions) && myIsShowingPredictions);
      case Backspace:
        if (newLineWCursorX.myCursorX == 0) {
          return new HardBoundary();
        }

        newLineWCursorX.myCursorX--;
        if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length()) {
          newLineWCursorX.myLineText.deleteCharAt(newLineWCursorX.myCursorX);
        }
        return new BackspacePrediction(newLineWCursorX, 1,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursorX.myCursorX
            && myIsShowingPredictions);
      case AltBackspace:
        int oldCursorX = newLineWCursorX.myCursorX;
        newLineWCursorX.myCursorX =
          moveToWordBoundary(newLineWCursorX.myLineText.toString(), newLineWCursorX.myCursorX, false);

        if (newLineWCursorX.myCursorX < 0) {
          return new HardBoundary();
        }
        int amount = oldCursorX - newLineWCursorX.myCursorX;

        if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length()) {
          newLineWCursorX.myLineText.delete(newLineWCursorX.myCursorX, Math.max(oldCursorX, newLineWCursorX.myLineText.length()));
        }
        return new BackspacePrediction(newLineWCursorX, amount,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursorX.myCursorX
            && myIsShowingPredictions);
      case LeftArrow:
      case RightArrow:
        amount = keyEvent.myEventType == TypeAheadEvent.EventType.RightArrow ? 1 : -1;
        newLineWCursorX.myCursorX += amount;

        if (newLineWCursorX.myCursorX < 0 || newLineWCursorX.myCursorX
          >= Math.max(newLineWCursorX.myLineText.length() + 1, myTerminalModel.getTerminalWidth())) {
          return new HardBoundary();
        }

        return new CursorMovePrediction(newLineWCursorX, amount,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursorX.myCursorX
            && newLineWCursorX.myCursorX <= newLineWCursorX.myLineText.length() && myIsShowingPredictions);
      case AltLeftArrow:
      case AltRightArrow:
        oldCursorX = newLineWCursorX.myCursorX;
        newLineWCursorX.myCursorX = moveToWordBoundary(newLineWCursorX.myLineText.toString(), newLineWCursorX.myCursorX,
          keyEvent.myEventType == TypeAheadEvent.EventType.AltRightArrow);

        if (newLineWCursorX.myCursorX < 0 || newLineWCursorX.myCursorX
          >= Math.max(newLineWCursorX.myLineText.length() + 1, myTerminalModel.getTerminalWidth())) {
          return new HardBoundary();
        }
        amount = newLineWCursorX.myCursorX - oldCursorX;

        return new CursorMovePrediction(newLineWCursorX, amount,
          myLeftMostCursorPosition != null && myLeftMostCursorPosition <= newLineWCursorX.myCursorX
            && newLineWCursorX.myCursorX <= newLineWCursorX.myLineText.length() && myIsShowingPredictions);
      case Delete:
        if (newLineWCursorX.myCursorX < newLineWCursorX.myLineText.length()) {
          newLineWCursorX.myLineText.deleteCharAt(newLineWCursorX.myCursorX);
        }
        return new DeletePrediction(newLineWCursorX, myIsShowingPredictions);
      case Home:
        amount = myLeftMostCursorPosition - newLineWCursorX.myCursorX;
        newLineWCursorX.myCursorX = myLeftMostCursorPosition;
        return new CursorMovePrediction(newLineWCursorX, amount, myIsShowingPredictions);
      case End:
        int newCursorPosition = newLineWCursorX.myLineText.length();
        if (newCursorPosition == myTerminalModel.getTerminalWidth()) {
          newCursorPosition--;
        }
        amount = newCursorPosition - newLineWCursorX.myCursorX;
        newLineWCursorX.myCursorX = newLineWCursorX.myLineText.length();
        return new CursorMovePrediction(newLineWCursorX, amount, myIsShowingPredictions);
      case Unknown:
        return new HardBoundary();
      default:
        throw new IllegalStateException("Unprocessed TypeAheadKeyboardEvent type");
    }
  }

  private abstract static class TypeAheadPrediction {
    public final long myCreatedTime;
    public final boolean myIsNotTentative;

    public final LineWithCursorX myPredictedLineWithCursorX;

    private TypeAheadPrediction(LineWithCursorX predictedLineWithCursorX, boolean isNotTentative) {
      myPredictedLineWithCursorX = predictedLineWithCursorX;
      myIsNotTentative = isNotTentative;

      myCreatedTime = System.nanoTime();
    }
  }

  private static class HardBoundary extends TypeAheadPrediction {
    public HardBoundary() {
      super(new LineWithCursorX(new StringBuffer(), -100), false); // will never match because cursorX can't be negative
    }
  }

  private static class CharacterPrediction extends TypeAheadPrediction {
    public final char myCharacter;

    public CharacterPrediction(LineWithCursorX predictedLineWithCursorX,
                               char character,
                               boolean isNotTentative) {
      super(predictedLineWithCursorX, isNotTentative);
      myCharacter = character;
    }
  }

  private static class BackspacePrediction extends TypeAheadPrediction {
    public final int myAmount;
    public BackspacePrediction(LineWithCursorX predictedLineWithCursorX, int amount, boolean isNotTentative) {
      super(predictedLineWithCursorX, isNotTentative);
      myAmount = amount;
    }
  }

  private static class DeletePrediction extends TypeAheadPrediction {
    public DeletePrediction(LineWithCursorX predictedLineWithCursorX, boolean isNotTentative) {
      super(predictedLineWithCursorX, isNotTentative);
    }
  }

  private static class CursorMovePrediction extends TypeAheadPrediction {
    public final int myAmount;

    public CursorMovePrediction(LineWithCursorX predictedLineWithCursorX,
                                int amount,
                                boolean isNotTentative) {
      super(predictedLineWithCursorX, isNotTentative);
      myAmount = amount;
    }
  }

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
