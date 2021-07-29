package com.jediterm.terminal.model;

import com.jediterm.terminal.model.TerminalTypeAheadManager.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PredictionMatcher {
  private static final Logger LOG = Logger.getLogger(PredictionMatcher.class);

  private final Object LOCK = new Object();
  private final @NotNull List<@NotNull TypeAheadPrediction> myPredictions = new ArrayList<>();
  private @Nullable Consumer<@NotNull Integer> myCallback;
  private boolean isEnabled = true;
  // if we need more chars to match a prediction, we buffer remaining chars and wait for new ones
  private String myTerminalDataBuffer = "";

  public void matchPrediction(@NotNull TypeAheadPrediction prediction) {
    synchronized (LOCK) {
      if (prediction instanceof AcknowledgePrediction) {
        isEnabled = true;
        return;
      }
      if (!isEnabled) return;
      if (prediction instanceof ClearPredictions) {
        myPredictions.clear();
        return;
      }
      myPredictions.add(prediction);
    }
  }

  public void onTerminalData(@NotNull String data, int cursorX) {
    LOG.debug("onTerminalData: " + data.replace("\u001b", "ESC")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\u0007", "BEL")
      .replace(" ", "<S>")
      .replace("\b", "\\b"));

    synchronized (LOCK) {
      if (!isEnabled) return;

      String terminalData = myTerminalDataBuffer + data;
      myTerminalDataBuffer = "";
      TypeaheadStringReader terminalDataReader = new TypeaheadStringReader(terminalData);

      int result = 0;
      while (!myPredictions.isEmpty() && terminalDataReader.remainingLength() > 0) {
        int matchResult = checkNextPrediction(terminalDataReader, cursorX);
        if (matchResult > 0) {
          result = matchResult;
        } else if (matchResult == 0) {
          break;
        } else {
          resetState(false);
          result = matchResult;
          break;
        }
      }
      if (result != 0 && myCallback != null) {
        myCallback.accept(result);
      }
      if (myPredictions.isEmpty() && terminalDataReader.remainingLength() > 0 && myCallback != null) {
        resetState(true);
        myCallback.accept(0);
      }
    }
  }

  public void setCallback(@NotNull Consumer<@NotNull Integer> callback) {
    myCallback = callback;
  }

  private int checkNextPrediction(@NotNull TypeaheadStringReader terminalDataReader, int cursorX) {
    if (myPredictions.isEmpty()) {
      return 0;
    }
    TypeAheadPrediction nextPrediction = myPredictions.get(0);

    int readerIndexBeforeMatching = terminalDataReader.getIndex();

    String debugString;
    if (nextPrediction.getCharacterOrNull() != null) {
      debugString = nextPrediction.getClass().getSimpleName() + " char: " + nextPrediction.getCharacterOrNull();
    } else {
      debugString = nextPrediction.getClass().getSimpleName();
    }
    switch (nextPrediction.matches(terminalDataReader, cursorX)) {
      case Success:
        LOG.debug("Matched successfully: " + debugString);
        myPredictions.remove(0);
        return nextPrediction.myID;
      case Buffer:
        LOG.debug("Buffered: " + debugString);
        myTerminalDataBuffer = terminalDataReader.myString.substring(readerIndexBeforeMatching);
        return 0;
      case Failure:
        LOG.debug("Match failure: " + debugString);
        return -nextPrediction.myID;
      default:
        throw new IllegalStateException("Unknown case");
    }
  }

  private void resetState(boolean disable) {
    myTerminalDataBuffer = "";
    myPredictions.clear();
    if (disable) {
      isEnabled = false;
    }
  }
}
