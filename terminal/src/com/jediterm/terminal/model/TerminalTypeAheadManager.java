package com.jediterm.terminal.model;

import com.google.common.base.Ascii;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private TypeAheadPrediction myLastSuccessfulPrediction;
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

  public void onTerminalData(char[] buffer, int offset, int count) {
    System.out.print("OnBeforeProcessChar: ");
    for (int i = offset; i < offset + count; ++i) {
      if (buffer[i] >= 32 && buffer[i] < 127) {
        System.out.print(buffer[i]);
      } else if (buffer[i] == 7) {
        System.out.println("<BEL>");
      } else if (buffer[i] == 27) {
        System.out.print("^");
      } else if (buffer[i] == 13) {
        System.out.print("\\r");
      } else if (buffer[i] == 10) {
        System.out.print("\\n");
      } else {
        System.out.print("(unknown char: " + (int) buffer[i] + ")");
      }
    }
    System.out.println();


    String terminalData = myTerminalDataBuffer + new String(buffer, offset, count);
    myTerminalDataBuffer = "";

    TypeaheadStringReader terminalDataReader = new TypeaheadStringReader(terminalData);

    synchronized (LOCK) {
      while (!myPredictions.isEmpty() && terminalDataReader.remaining() > 0) {
        // TODO: vscode omits some char sequences from sending to the prediction engine, maybe we should too.

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

        updateLeftMostCursorPosition(cursorX);

        TypeAheadPrediction nextPrediction = getNextPrediction();
        if (nextPrediction == null) {
          return;
        }

        int readerIndexBeforeMatching = terminalDataReader.myIndex;
        switch (nextPrediction.matches(terminalDataReader)) {
          case Success:
            System.out.println("Match: success");
            if (nextPrediction instanceof TentativeBoundary
                    && ((TentativeBoundary) nextPrediction).myInnerPrediction instanceof CharacterPrediction) {
              myIsNotPasswordPrompt = true;
            }

            myLastSuccessfulPrediction = myPredictions.get(0);
            myPredictions.remove(0);
            nextPrediction.myInitialLine.setTypeAheadLine(null);
            adjustPredictions(terminalLine, nextPrediction);
            redrawPredictions(terminalLine, cursorX);
            break;
          case Buffer:
            System.out.println("Match: buffer");
            myTerminalDataBuffer = terminalData.substring(readerIndexBeforeMatching);
            return;
          case Failure: // TODO: onFailure needs rework
            System.out.println("Match: failure");
            myOutOfSyncDetected = true;
            clearPredictions();
        }

      }
      /*
      TypeAheadPrediction nextPrediction = getNextPrediction();

      if (nextPrediction != null && nextPrediction.myTypedChars.startsWith("\b")) {
        myTerminalTextBuffer.addModelListener(new TerminalModelListener() {
          @Override
          public void modelChanged() {
            myTerminalTextBuffer.removeModelListener(this);
            checkNextPrediction(terminalDataReader);
          }
        });
        return;
      }

    checkNextPrediction(terminalDataReader);
     */
    }
  }

  private void checkNextPrediction(TypeaheadStringReader stringReader) {
    try {
      doCheckNextPrediction(stringReader);
    } catch (Exception e) {
      LOG.error("Unhandled exception", e);
    }
  }

  private void doCheckNextPrediction(TypeaheadStringReader stringReader) {
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

  private void adjustPredictions(@NotNull TerminalLine terminalLine,
                                 @NotNull TerminalTypeAheadManager.TypeAheadPrediction matchedPrediction) {
    int matchedPredictionSize = matchedPrediction.myKeyEvents.size();

    for (TypeAheadPrediction prediction : myPredictions) {
      if (prediction.myInitialLine != terminalLine) {
        throw new IllegalStateException("Different terminal lines");
      }
      /*
      if (!prediction.myKeyEvents.startsWith(matchedPrediction.myKeyEvents)) {
        throw new IllegalStateException(prediction.myKeyEvents + " is expected to start with " + prediction.myKeyEvents);
      }
       */

      prediction.removeFirstNEvents(matchedPredictionSize);
    }
  }

  private void redrawPredictions(TerminalLine terminalLine, int cursorX) {
    int lastVisiblePredictionIndex = 0;
    while (lastVisiblePredictionIndex < myPredictions.size()
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof TentativeBoundary)
            && !(myPredictions.get(lastVisiblePredictionIndex) instanceof HardBoundary)) {
      lastVisiblePredictionIndex++;
    }
    lastVisiblePredictionIndex--;

    if (lastVisiblePredictionIndex >= 0) {
      TypeAheadPrediction oldPrediction = myPredictions.get(lastVisiblePredictionIndex);
      TerminalLine predictedLine = createTerminalLinePrediction(terminalLine, cursorX, oldPrediction.myKeyEvents).myTerminalLine;
      terminalLine.setTypeAheadLine(predictedLine);
    }

    fireModelChanged();
  }

  private void updateLeftMostCursorPosition(int cursorX) {
    if (myLeftMostCursorPosition == null) {
      myLeftMostCursorPosition = cursorX;
    } else {
      myLeftMostCursorPosition = Math.min(myLeftMostCursorPosition, cursorX);
    }
  }

  private static class TerminalLineWithCursorX {
    TerminalLine myTerminalLine;
    int myCursorX;

    TerminalLineWithCursorX(TerminalLine terminalLine, int cursorX) {
      myTerminalLine = terminalLine;
      myCursorX = cursorX;
    }
  }

  private @NotNull TerminalLineWithCursorX createTerminalLinePrediction(TerminalLine initialLine, int initialCursorX, List<KeyEvent> keyEvents) {
    TerminalLine predictedLine = initialLine.copy();
    int newCursorX = initialCursorX;

    for (KeyEvent keyEvent : keyEvents) {
      if (keyEvent.getKeyCode() == KeyEvent.VK_UNDEFINED) { // KEY_TYPED event
        predictedLine.writeString(newCursorX, new CharBuffer(keyEvent.getKeyChar(), 1), getTextStyle());
        newCursorX++;
      } else { // KEY_PRESSED or KEY_RELEASED
        int eventKeyCode = keyEvent.getKeyCode();

        if (eventKeyCode == KeyEvent.VK_BACK_SPACE) {
          if (newCursorX > 0) {
            newCursorX--;
            predictedLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
          }
        } else if (eventKeyCode == KeyEvent.VK_LEFT) {
          if (newCursorX > 0) {
            newCursorX--;
          }
        } else if (eventKeyCode == KeyEvent.VK_RIGHT) {
          if (newCursorX < myTerminal.getTerminalWidth() - 1) {
            newCursorX++;
          }
        } else if (eventKeyCode == KeyEvent.VK_DELETE) {
          predictedLine.deleteCharacters(newCursorX, 1, TextStyle.EMPTY);
        } else { // TODO: del, alt+>, alt+<, enter
          throw new IllegalStateException("Events should be filtered but keyEvents contained key code " + eventKeyCode);
        }
      }
    }

    return new TerminalLineWithCursorX(predictedLine, newCursorX);
  }

  public void onKeyEvent(KeyEvent keyEvent) {
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
    TerminalLine terminalLine;

    try {
      cursorX = myTerminal.getCursorX() - 1;
      cursorY = myTerminal.getCursorY() - 1;
      terminalLine = myTerminalTextBuffer.getLine(cursorY);
    } finally {
      myTerminalTextBuffer.unlock();
    }

    updateLeftMostCursorPosition(cursorX);

    synchronized (LOCK) {
      myOutOfSyncDetected = false;
      System.out.println("Typed " + (char) keyEvent.getKeyCode() + " (" + keyEvent.getKeyCode() + ")");
      if (terminalLine == null) {
        clearPredictions();
        return;
      }
      TypeAheadPrediction lastPrediction = getLastPrediction();
      List<KeyEvent> keyEventList = lastPrediction != null ? new ArrayList<>(lastPrediction.myKeyEvents) : new ArrayList<>();
      keyEventList.add(keyEvent);
      TypeAheadPrediction prediction = createPrediction(terminalLine, cursorX, keyEventList);
      myPredictions.add(prediction);
      redrawPredictions(terminalLine, cursorX);
    }
  }

  private void clearPredictions() {
    boolean fireChange = !myPredictions.isEmpty();
    for (TypeAheadPrediction prediction : myPredictions) {
      prediction.myInitialLine.setTypeAheadLine(null);
    }
    myPredictions.clear();
    myTerminalDataBuffer = "";
    myLeftMostCursorPosition = null;
    myIsNotPasswordPrompt = false;
    if (fireChange) {
      fireModelChanged();
    }
  }

  private @NotNull TypeAheadPrediction createPrediction(@NotNull TerminalLine initialLine,
                                                        int initialCursorX,
                                                        @NotNull List<KeyEvent> keyEvents) {
    int newCursorX = createTerminalLinePrediction(initialLine, initialCursorX, keyEvents).myCursorX;

    KeyEvent lastKeyEvent = keyEvents.get(keyEvents.size() - 1);
    if (lastKeyEvent.getKeyCode() == KeyEvent.VK_UNDEFINED) { // KEY_TYPED event
      System.out.println("createPrediction: " + lastKeyEvent.getKeyChar() + " (" + (int) lastKeyEvent.getKeyChar() + ")");

      TypeAheadPrediction charPrediction = new CharacterPrediction(initialLine, keyEvents, newCursorX, lastKeyEvent.getKeyChar());

      if (myIsNotPasswordPrompt) {
        return charPrediction;
      }

      for (TypeAheadPrediction prediction : myPredictions) {
        if (prediction instanceof CharacterPrediction
                || (prediction instanceof TentativeBoundary && ((TentativeBoundary) prediction).myInnerPrediction instanceof CharacterPrediction)) {
          return charPrediction;
        }
      }

      return new TentativeBoundary(initialLine, keyEvents, newCursorX, charPrediction);
    } else { // KEY_PRESSED or KEY_RELEASED
      System.out.println("createPrediction: " + lastKeyEvent.getKeyCode());
      int eventKeyCode = lastKeyEvent.getKeyCode();

      if (eventKeyCode == KeyEvent.VK_BACK_SPACE) {
        TypeAheadPrediction backspacePrediction = new BackspacePrediction(initialLine, keyEvents, newCursorX, true); // TODO: delete myIsLastChar or fill with correct data

        if (myLeftMostCursorPosition != null && newCursorX >= myLeftMostCursorPosition) {
          return backspacePrediction;
        }
        return new TentativeBoundary(initialLine, keyEvents, newCursorX, backspacePrediction);
      } else if (eventKeyCode == KeyEvent.VK_LEFT) {
        // TODO
      } else if (eventKeyCode == KeyEvent.VK_RIGHT) {
        // TODO
      } else { // TODO: del, alt+>, alt+<, enter
        return new HardBoundary(initialLine, keyEvents, newCursorX);
      }
    }

    return new CharacterPrediction(initialLine, keyEvents, newCursorX, 'c'); // TODO: delete
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
    TypeAheadPrediction prediction = getLastVisiblePrediction();
    return prediction == null ? myTerminal.getCursorX() : prediction.myPredictedCursorX + 1;
  }

  private enum MatchResult {
    Success,
    Failure,
    Buffer,
  }

  private static class TypeaheadStringReader { // TODO: copied from vscode, needs polish/deleting
    private final String myString;
    private int myIndex = 0;

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

  private abstract static class TypeAheadPrediction {
    private final TerminalLine myInitialLine;
    private final int myPredictedCursorX;
    private List<KeyEvent> myKeyEvents;

    private TypeAheadPrediction(@NotNull TerminalLine initialLine,
                                @NotNull List<KeyEvent> keyEvents,
                                int predictedCursorX) {
      myInitialLine = initialLine;
      myKeyEvents = keyEvents;
      myPredictedCursorX = predictedCursorX;
    }

    public void removeFirstNEvents(int n) {
      myKeyEvents = myKeyEvents.subList(n, myKeyEvents.size());
    }

    public abstract boolean getClearAfterTimeout();

    public abstract @NotNull MatchResult matches(TypeaheadStringReader stringReader);
  }

  private static class HardBoundary extends TypeAheadPrediction {
    private HardBoundary(@NotNull TerminalLine initialLine, @NotNull List<KeyEvent> keyEvents, int predictedCursorX) {
      super(initialLine, keyEvents, predictedCursorX);
    }

    @Override
    public boolean getClearAfterTimeout() {
      return false;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      return MatchResult.Failure;
    }
  }

  private static class TentativeBoundary extends TypeAheadPrediction {
    final TypeAheadPrediction myInnerPrediction;

    private TentativeBoundary(@NotNull TerminalLine initialLine, @NotNull List<KeyEvent> keyEvents, int predictedCursorX, TypeAheadPrediction innerPrediction) {
      super(initialLine, keyEvents, predictedCursorX);
      myInnerPrediction = innerPrediction;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      return myInnerPrediction.matches(stringReader);
    }
  }

  private class CharacterPrediction extends TypeAheadPrediction {
    char myCharacter; // TODO: make private

    private CharacterPrediction(@NotNull TerminalLine initialLine,
                                @NotNull List<KeyEvent> keyEvents,
                                int predictedCursorX,
                                char character) {
      super(initialLine, keyEvents, predictedCursorX);
      myCharacter = character;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
    }

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      int startIndex = stringReader.myIndex;


      // remove any styling CSI before checking the char
      String eaten;
      Pattern CSI_STYLE_RE = Pattern.compile("^\\x1b\\[[0-9;]*m"); // TODO: test regex
      do {
        eaten = stringReader.eatRe(CSI_STYLE_RE);
      } while (eaten != null && !eaten.isEmpty());

      if (stringReader.eof()) {
        return MatchResult.Buffer;
      }

      if (stringReader.eatChar(myCharacter) != null) {
        return MatchResult.Success;
      }

      if (myLastSuccessfulPrediction != null && myLastSuccessfulPrediction instanceof CharacterPrediction) {
        // vscode #112842
        String zshPrediction = "\b" + ((CharacterPrediction) myLastSuccessfulPrediction).myCharacter + myCharacter;
        MatchResult zshMatchResult = stringReader.eatGradually(zshPrediction);
        if (zshMatchResult != MatchResult.Failure) {
          return zshMatchResult;
        }
      }

      stringReader.myIndex = startIndex;
      return MatchResult.Failure;
    }
  }

  private static class BackspacePrediction extends TypeAheadPrediction {
    boolean myIsLastChar;

    private BackspacePrediction(@NotNull TerminalLine initialLine,
                                @NotNull List<KeyEvent> keyEvents,
                                int predictedCursorX,
                                boolean isLastChar) {
      super(initialLine, keyEvents, predictedCursorX);
      myIsLastChar = isLastChar;
    }

    @Override
    public boolean getClearAfterTimeout() {
      return true;
    }

    final String CSI = (char) CharUtils.ESC + "[";

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      if (myIsLastChar) {
        MatchResult r1 = stringReader.eatGradually("\b" + CSI + "K");
        if (r1 != MatchResult.Failure) {
          return r1;
        }

        MatchResult r2 = stringReader.eatGradually("\b \b");
        if (r2 != MatchResult.Failure) {
          return r2;
        }
      }

      return MatchResult.Failure;
    }
  }

}
