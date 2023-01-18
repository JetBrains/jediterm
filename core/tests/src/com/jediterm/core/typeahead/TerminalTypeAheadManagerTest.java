package com.jediterm.core.typeahead;

import com.jediterm.core.typeahead.TerminalTypeAheadManager.LatencyStatistics;
import com.jediterm.core.typeahead.TerminalTypeAheadManager.TypeAheadEvent;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TerminalTypeAheadManagerTest extends TestCase {

  public void testPasswordPromptDetection() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        assertFalse(didDrawPredictions());
        model.insertString("a");
        manager.onTerminalStateChanged();
        manager.onKeyEvent(TypeAheadEvent.fromChar('b'));
        assertTrue(didDrawPredictions());
      }
    }.fillLatencyStats().run();
  }

  public void testAlternateBufferTrue() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.isUsingAlternateBuffer = true;
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        assertFalse(didDrawPredictions());
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testTypeAheadDisabled() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.typeAheadEnabled = false;
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        manager.onTerminalStateChanged();
        manager.onResize();

        assertTrue(actions.isEmpty());
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testLowLatency() {
    new TestRunner() {
      @Override
      void run() {
        model.latencyThreshold = TimeUnit.MILLISECONDS.toNanos(100);
        TypeAheadEvent.fromString("type ahead").forEach(manager::onKeyEvent);
        model.insertString("type ahead");
        manager.onTerminalStateChanged();
        actions.clear();

        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        assertFalse(didDrawPredictions());
      }
    }.run();
  }

  public void testHighLatency() throws Exception {
    new TestRunner() {
      @Override
      void run() throws Exception {
        model.latencyThreshold = TimeUnit.MILLISECONDS.toNanos(100);
        TypeAheadEvent.fromString("type ahead").forEach(manager::onKeyEvent);
        TimeUnit.MILLISECONDS.sleep(110);
        model.insertString("type ahead");
        manager.onTerminalStateChanged();
        actions.clear();

        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        assertTrue(didDrawPredictions());
      }
    }.run();
  }

  public void testCharacterPrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.InsertChar) {
            assertFalse(found);
            assertEquals('a', ((Action.InsertChar) action).ch);
            assertEquals(0, ((Action.InsertChar) action).index);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testBackspacePrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        model.insertString("a");
        actions.clear();

        manager.onKeyEvent(new TypeAheadEvent(TypeAheadEvent.EventType.Backspace));

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.RemoveCharacters) {
            assertFalse(found);
            assertEquals(0, ((Action.RemoveCharacters) action).from);
            assertEquals(1, ((Action.RemoveCharacters) action).count);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testTentativeBackspacePrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.insertString("a");
        manager.onKeyEvent(new TypeAheadEvent(TypeAheadEvent.EventType.Backspace));

        assertFalse(didDrawPredictions());
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testCursorMovePrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.currentLine += 'a';
        manager.onKeyEvent(new TypeAheadEvent(TypeAheadEvent.EventType.RightArrow));

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.MoveCursor) {
            assertFalse(found);
            assertEquals(1, ((Action.MoveCursor) action).index);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testTentativeCursorMovePrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.insertString("a");
        manager.onKeyEvent(new TypeAheadEvent(TypeAheadEvent.EventType.LeftArrow));

        assertFalse(didDrawPredictions());
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testEnableDebounceOnPrediction() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.CallDebouncer) {
            assertFalse(found);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testDebounceOnTerminalStateChanged() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        manager.onKeyEvent(TypeAheadEvent.fromChar('a'));
        actions.clear();

        model.insertString("a");
        manager.onTerminalStateChanged();

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.CallDebouncer) {
            assertFalse(found);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  public void testTerminateDebounceOnInvalidState() throws Exception {
    new TestRunner() {
      @Override
      void run() {
        model.insertString("a");
        manager.onTerminalStateChanged();

        boolean found = false;
        for (Action action : actions) {
          if (action instanceof Action.TerminateDebouncer) {
            assertFalse(found);
            found = true;
          }
        }
        assertTrue(found);
      }
    }.fillLatencyStats().setIsNotPasswordPrompt().run();
  }

  abstract static class TestRunner {
    protected ArrayList<Action> actions = new ArrayList<>();
    protected MockTypeAheadTerminalModel model = new MockTypeAheadTerminalModel(actions);
    protected MockDebouncer debouncer = new MockDebouncer(actions);
    protected TerminalTypeAheadManager manager = new TerminalTypeAheadManager(model);

    TestRunner() {
      manager.setClearPredictionsDebouncer(debouncer);
    }

    TestRunner fillLatencyStats() throws NoSuchFieldException, IllegalAccessException {
      Field myLatencyStatisticsField = TerminalTypeAheadManager.class.getDeclaredField("myLatencyStatistics");
      myLatencyStatisticsField.setAccessible(true);
      LatencyStatistics myLatencyStatistics = (LatencyStatistics) myLatencyStatisticsField.get(manager);

      Field myLatenciesField = LatencyStatistics.class.getDeclaredField("myLatencies");
      myLatenciesField.setAccessible(true);
      LinkedList<Long> myLatencies = (LinkedList<Long>) myLatenciesField.get(myLatencyStatistics);
      for (int i = 0; i < 10; ++i) {
        myLatencies.add(0L);
      }

      return this;
    }

    TestRunner setIsNotPasswordPrompt() throws NoSuchFieldException, IllegalAccessException {
      Field myIsNotPasswordPrompt = TerminalTypeAheadManager.class.getDeclaredField("myIsNotPasswordPrompt");
      myIsNotPasswordPrompt.setAccessible(true);
      myIsNotPasswordPrompt.set(manager, true);

      return this;
    }

    protected boolean didDrawPredictions() {
      for (Action action : actions) {
        if (action instanceof Action.InsertChar
          || action instanceof Action.RemoveCharacters
          || action instanceof Action.MoveCursor) {
          return true;
        }
      }
      return false;
    }

    abstract void run() throws Exception;
  }

  static abstract class Action {
    static class InsertChar extends Action {
      char ch;
      int index;

      InsertChar(char ch, int index) {
        this.ch = ch;
        this.index = index;
      }
    }

    static class RemoveCharacters extends Action {
      int from;
      int count;

      RemoveCharacters(int from, int count) {
        this.from = from;
        this.count = count;
      }
    }

    static class MoveCursor extends Action {
      int index;

      MoveCursor(int index) {
        this.index = index;
      }
    }

    static class ForceRedraw extends Action {
    }

    static class ClearPredictions extends Action {
    }

    static class CallDebouncer extends Action {
    }

    static class TerminateDebouncer extends Action {
    }
  }

  static class MockTypeAheadTerminalModel implements TypeAheadTerminalModel {
    String currentLine = "";
    int cursorX;
    int terminalWidth = 20;
    long latencyThreshold = 0;
    boolean typeAheadEnabled = true;
    boolean isUsingAlternateBuffer = false;
    ShellType shellType = ShellType.Bash;

    private final List<Action> actions;

    MockTypeAheadTerminalModel(List<Action> actions) {
      this.actions = actions;
    }

    @Override
    public void insertCharacter(char ch, int index) {
      actions.add(new Action.InsertChar(ch, index));
    }

    @Override
    public void removeCharacters(int from, int count) {
      actions.add(new Action.RemoveCharacters(from, count));
    }

    @Override
    public void moveCursor(int index) {
      actions.add(new Action.MoveCursor(index));
    }

    @Override
    public void forceRedraw() {
      actions.add(new Action.ForceRedraw());
    }

    @Override
    public void clearPredictions() {
      actions.add(new Action.ClearPredictions());
    }

    @Override
    public void lock() {
    }

    @Override
    public void unlock() {
    }

    @Override
    public boolean isUsingAlternateBuffer() {
      return isUsingAlternateBuffer;
    }

    @Override
    public @NotNull LineWithCursorX getCurrentLineWithCursor() {
      return new LineWithCursorX(new StringBuffer(currentLine), cursorX);
    }

    @Override
    public int getTerminalWidth() {
      return terminalWidth;
    }

    @Override
    public boolean isTypeAheadEnabled() {
      return typeAheadEnabled;
    }

    @Override
    public long getLatencyThreshold() {
      return latencyThreshold;
    }

    @Override
    public ShellType getShellType() {
      return shellType;
    }

    void insertString(String text) {
      currentLine = currentLine.substring(0, cursorX) + text + currentLine.substring(cursorX);
      cursorX += text.length();
    }
  }

  static class MockDebouncer implements Debouncer {

    private final List<Action> actions;

    MockDebouncer(List<Action> actions) {
      this.actions = actions;
    }

    @Override
    public void call() {
      actions.add(new Action.CallDebouncer());
    }

    @Override
    public void terminateCall() {
      actions.add(new Action.TerminateDebouncer());
    }
  }
}
