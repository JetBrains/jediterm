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
      TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();
      if (!myPredictions.isEmpty()) {
        updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);
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
    long prevTypedTime = myLastTypedTime;
    myLastTypedTime = System.nanoTime();
    if (myOutOfSyncDetected && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - prevTypedTime) < AUTO_SYNC_DELAY) {
      clearPredictions();
      return;
    }

    TerminalLineWithCursor terminalLineWithCursor = getTerminalLineWithCursor();
    updateLeftMostCursorPosition(terminalLineWithCursor.myCursorX);

    synchronized (LOCK) {
      myOutOfSyncDetected = false;
      System.out.println("Typed " + (char) keyEvent.getKeyCode() + " (" + keyEvent.getKeyCode() + ")");

      TypeAheadPrediction prediction = createPrediction(terminalLineWithCursor, keyEvent);
      myPredictions.add(prediction);
      redrawPredictions(terminalLineWithCursor);
    }
  }

  public void onResize() {
    clearPredictions();
  }

  public void addModelListener(@NotNull TerminalModelListener listener) {
    myListeners.add(listener);
  }

  public int getCursorX() {
    TypeAheadPrediction prediction = getLastVisiblePrediction();
    return prediction == null ? myTerminal.getCursorX() : prediction.myPredictedCursorX + 1;
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

  private @NotNull TerminalLineWithCursor getTerminalLineWithCursor() {
    myTerminalTextBuffer.lock();
    int cursorX, cursorY;
    TerminalLine terminalLine;

    try {
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
    switch (nextPrediction.matches(terminalDataReader)) {
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
        clearPredictions();
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

    public abstract @NotNull MatchResult matches(TypeaheadStringReader stringReader);
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
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
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
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      return myInnerPrediction.matches(stringReader);
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

      if (stringReader.eatChar(getCharacter()) != null) {
        return MatchResult.Success;
      }

      if (myLastSuccessfulPrediction.getCharacterOrNull() != null) {
        // vscode #112842
        String zshPrediction = "\b" + myLastSuccessfulPrediction.getCharacterOrNull() + getCharacter();
        MatchResult zshMatchResult = stringReader.eatGradually(zshPrediction);
        if (zshMatchResult != MatchResult.Failure) {
          return zshMatchResult;
        }
      }

      stringReader.myIndex = startIndex;
      return MatchResult.Failure;
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
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
      if (myIsLastChar) {
        MatchResult r1 = stringReader.eatGradually("\b" + CSI + "K");
        if (r1 != MatchResult.Failure) {
          return r1;
        }

        return stringReader.eatGradually("\b \b");
      }

      return MatchResult.Failure;
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

    @Override
    public @NotNull MatchResult matches(TypeaheadStringReader stringReader) {
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

      MatchResult r4 = stringReader.eatGradually(CSI + (myCursorY + 1) + ";" + (myPredictedCursorX + 1) + "H"); // TODO: check if this works
      if (r4 != MatchResult.Failure) {
        return r4;
      }

      return MatchResult.Failure;
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
}
