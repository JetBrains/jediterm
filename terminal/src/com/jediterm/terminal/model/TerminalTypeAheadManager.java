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
  private TypeAheadPrediction myLastSuccessfulPrediction;
  private String myTerminalDataBuffer = "";

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
      } else if (buffer[i] == 27) {
        System.out.print("^");
      } else if (buffer[i] == 13) {
        System.out.print("\\r");
      } else if (buffer[i] == 10) {
        System.out.print("\\n");
      } else {
        System.out.println("\nUnknown char! " + (int) buffer[i]);
      }
    }
    System.out.println();


    String terminalData = myTerminalDataBuffer + new String(buffer, offset, count);
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

        TypeAheadPrediction nextPrediction = getNextPrediction();
        if (nextPrediction == null) {
          return;
        }

        int readerIndexBeforeMatching = terminalDataReader.myIndex;
        switch (nextPrediction.matches(terminalDataReader)) {
          case Success:
            System.out.println("Match: success");
            myLastSuccessfulPrediction = myPredictions.get(0);
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
            break;
          case Buffer:
            System.out.println("Match: buffer");
            myTerminalDataBuffer = terminalData.substring(readerIndexBeforeMatching);
            return;
          case Failure:
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
      return new CharacterPrediction(e.myInitialLine, newTypedChars, e.myPredictedLine, e.myPredictedCursorX, ((CharacterPrediction) e).myCharacter);
    }).collect(Collectors.toList());
  }

  public void typed(char keyChar) { // TODO: change to KeyEvent to process move by word?
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

    for (int i = 0; i < typedChars.length(); ++i) {
      char ch = typedChars.charAt(i);

      if (Character.isLetterOrDigit(ch)) {
        predictedLine.writeString(newCursorX, new CharBuffer(ch, 1), getTextStyle());
        newCursorX++;

        if (i + 1 == typedChars.length()) {
          return new CharacterPrediction(initialLine, typedChars, predictedLine, newCursorX, ch);
        }
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

    return new CharacterPrediction(initialLine, typedChars, predictedLine, newCursorX, 'c'); // TODO: delete
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

  private abstract static class TypeAheadPrediction {
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

    public abstract @NotNull MatchResult matches(TypeaheadStringReader stringReader);
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

  private class CharacterPrediction extends TypeAheadPrediction {
    char myCharacter; // TODO: make private

    private CharacterPrediction(@NotNull TerminalLine initialLine,
                                @NotNull String typedChars,
                                @NotNull TerminalLine predictedLine,
                                int predictedCursorX,
                                char character) {
      super(initialLine, typedChars, predictedLine, predictedCursorX);
      myCharacter = character;
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
                                @NotNull String typedChars,
                                @NotNull TerminalLine predictedLine,
                                int predictedCursorX,
                                boolean isLastChar) {
      super(initialLine, typedChars, predictedLine, predictedCursorX);
      myIsLastChar = isLastChar;
    }


    final String CSI = CharUtils.ESC + "[";

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
