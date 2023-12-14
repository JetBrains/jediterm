package com.jediterm.terminal.ui;

import com.jediterm.core.Color;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.terminal.*;
import com.jediterm.terminal.SubstringFinder.FindResult;
import com.jediterm.terminal.SubstringFinder.FindResult.FindItem;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 * <p/>
 */
public class JediTermWidget extends JPanel implements TerminalSession, TerminalWidget, TerminalActionProvider {
  private static final Logger LOG = LoggerFactory.getLogger(JediTermWidget.class);

  protected final TerminalPanel myTerminalPanel;
  private final JScrollBar myScrollBar;
  protected final JediTerminal myTerminal;
  private final AtomicReference<Session> myRunningSession = new AtomicReference<>();
  private final JediTermTypeAheadModel myTypeAheadTerminalModel;
  private final TerminalTypeAheadManager myTypeAheadManager;
  private JediTermSearchComponent myFindComponent;
  @SuppressWarnings("removal")
  private final PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TerminalStarter myTerminalStarter;
  private final CompletableFuture<TerminalStarter> myTerminalStarterFuture = new CompletableFuture<>();
  protected final SettingsProvider mySettingsProvider;
  private TerminalActionProvider myNextActionProvider;
  private final JLayeredPane myInnerPanel;
  private final TextProcessing myTextProcessing;
  private final List<TerminalWidgetListener> myListeners = new CopyOnWriteArrayList<>();
  private final Object myExecutorServiceManagerLock = new Object();
  private volatile TerminalExecutorServiceManager myExecutorServiceManager;

  public JediTermWidget(@NotNull SettingsProvider settingsProvider) {
    this(80, 24, settingsProvider);
  }

  public JediTermWidget(int columns, int lines, SettingsProvider settingsProvider) {
    super(new BorderLayout());

    mySettingsProvider = settingsProvider;

    StyleState styleState = createDefaultStyle();

    myTextProcessing = new TextProcessing(settingsProvider.getHyperlinkColor(),
      settingsProvider.getHyperlinkHighlightingMode());

    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(columns, lines, styleState, settingsProvider.getBufferMaxLinesCount(), myTextProcessing);
    myTextProcessing.setTerminalTextBuffer(terminalTextBuffer);

    myTerminalPanel = createTerminalPanel(mySettingsProvider, styleState, terminalTextBuffer);
    myTerminal = createTerminal(myTerminalPanel, terminalTextBuffer, styleState);

    myTypeAheadTerminalModel = new JediTermTypeAheadModel(myTerminal, terminalTextBuffer, settingsProvider);
    myTypeAheadManager = new TerminalTypeAheadManager(myTypeAheadTerminalModel);
    JediTermDebouncerImpl typeAheadDebouncer =
      new JediTermDebouncerImpl(myTypeAheadManager::debounce, TerminalTypeAheadManager.MAX_TERMINAL_DELAY, getExecutorServiceManager());
    myTypeAheadManager.setClearPredictionsDebouncer(typeAheadDebouncer);
    myTerminalPanel.setTypeAheadManager(myTypeAheadManager);

    myTerminal.setModeEnabled(TerminalMode.AltSendsEscape, mySettingsProvider.altSendsEscape());

    myTerminalPanel.addTerminalMouseListener(myTerminal);
    myTerminalPanel.setNextProvider(this);
    myTerminalPanel.setCoordAccessor(myTerminal);

    myPreConnectHandler = createPreConnectHandler(myTerminal);
    myTerminalPanel.addCustomKeyListener(myPreConnectHandler);
    myScrollBar = createScrollBar();

    myInnerPanel = new JLayeredPane();
    myInnerPanel.setFocusable(false);
    setFocusable(false);

    myInnerPanel.setLayout(new TerminalLayout());
    myInnerPanel.add(myTerminalPanel, TerminalLayout.TERMINAL);
    myInnerPanel.add(myScrollBar, TerminalLayout.SCROLL);

    add(myInnerPanel, BorderLayout.CENTER);

    myScrollBar.setModel(myTerminalPanel.getVerticalScrollModel());

    myTerminalPanel.init(myScrollBar);

    myTerminalPanel.setVisible(true);
  }

  protected JScrollBar createScrollBar() {
    JScrollBar scrollBar = new JScrollBar();
    scrollBar.setUI(new FindResultScrollBarUI());
    return scrollBar;
  }

  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());
    return styleState;
  }

  protected TerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider, @NotNull StyleState styleState, @NotNull TerminalTextBuffer terminalTextBuffer) {
    return new TerminalPanel(settingsProvider, terminalTextBuffer, styleState);
  }

  protected @NotNull JediTerminal createTerminal(@NotNull TerminalDisplay display,
                                                 @NotNull TerminalTextBuffer textBuffer,
                                                 @NotNull StyleState initialStyleState) {
    return new JediTerminal(display, textBuffer, initialStyleState);
  }

  @SuppressWarnings({"removal", "DeprecatedIsStillUsed"})
  @Deprecated(forRemoval = true)
  private PreConnectHandler createPreConnectHandler(JediTerminal terminal) {
    return new PreConnectHandler(terminal);
  }

  public TerminalDisplay getTerminalDisplay() {
    return getTerminalPanel();
  }

  public TerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public final @NotNull TerminalExecutorServiceManager getExecutorServiceManager() {
    TerminalExecutorServiceManager manager = myExecutorServiceManager;
    if (manager != null) return manager;
    synchronized (myExecutorServiceManagerLock) {
      manager = myExecutorServiceManager;
      if (manager == null) {
        manager = createExecutorServiceManager();
        myExecutorServiceManager = manager;
      }
      return manager;
    }
  }

  protected @NotNull TerminalExecutorServiceManager createExecutorServiceManager() {
    return new JediTermExecutorServiceManager();
  }

  @SuppressWarnings("unused")
  public TerminalTypeAheadManager getTypeAheadManager() {
    return myTypeAheadManager;
  }

  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;

    TypeAheadTerminalModel.ShellType shellType;
    if (ttyConnector instanceof ProcessTtyConnector) {
      List<String> commandLine = ((ProcessTtyConnector) myTtyConnector).getCommandLine();
      shellType = TypeAheadTerminalModel.commandLineToShellType(commandLine);
    } else {
      shellType = TypeAheadTerminalModel.ShellType.Unknown;
    }
    myTypeAheadTerminalModel.setShellType(shellType);
    myTerminalStarter = createTerminalStarter(myTerminal, myTtyConnector);
    myTerminalStarterFuture.complete(myTerminalStarter);
    myTerminalPanel.setTerminalStarter(myTerminalStarter);
  }

  protected TerminalStarter createTerminalStarter(@NotNull JediTerminal terminal, @NotNull TtyConnector connector) {
    return new TerminalStarter(terminal, connector,
      new TtyBasedArrayDataStream(connector, myTypeAheadManager::onTerminalStateChanged), myTypeAheadManager, getExecutorServiceManager());
  }

  @Override
  public TtyConnector getTtyConnector() {
    return myTtyConnector;
  }

  @Override
  public Terminal getTerminal() {
    return myTerminal;
  }

  public void start() {
    synchronized (myRunningSession) {
      if (myRunningSession.get() == null) {
        EmulatorTask task = new EmulatorTask(() -> {
          synchronized (myRunningSession) {
            myRunningSession.set(null);
          }
        });
        Future<?> future = getExecutorServiceManager().getUnboundedExecutorService().submit(task);
        myRunningSession.set(new Session(task, future));
      }
      else {
        LOG.error("Should not try to start session again at this point... ");
      }
    }
  }

  /**
   * @deprecated use {@link #close()} instead
   */
  @Deprecated
  public void stop() {
    stopRunningSession();
  }

  private void stopRunningSession() {
    Session session = myRunningSession.get();
    if (session != null) {
      session.stop();
    }
  }

  public boolean isSessionRunning() {
    return myRunningSession.get() != null;
  }

  @Override
  public TerminalTextBuffer getTerminalTextBuffer() {
    return myTerminalPanel.getTerminalTextBuffer();
  }

  @Override
  public boolean requestFocusInWindow() {
    return myTerminalPanel.requestFocusInWindow();
  }

  @Override
  public void requestFocus() {
    myTerminalPanel.requestFocus();
  }

  public boolean canOpenSession() {
    return !isSessionRunning();
  }

  @Override
  public JediTermWidget createTerminalSession(TtyConnector ttyConnector) {
    setTtyConnector(ttyConnector);
    return this;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void close() {
    stopRunningSession();
    if (myTerminalStarter != null) {
      myTerminalStarter.close();
    }
    myTerminalPanel.dispose();
    getExecutorServiceManager().shutdownWhenAllExecuted();
  }

  @Override
  public List<TerminalAction> getActions() {
    return List.of(new TerminalAction(mySettingsProvider.getFindActionPresentation(),
      keyEvent -> {
        showFindText();
        return true;
      }).withMnemonicKey(KeyEvent.VK_F));
  }

  private void showFindText() {
    if (myFindComponent == null) {
      myFindComponent = createSearchComponent();

      final JComponent component = myFindComponent.getComponent();
      myInnerPanel.add(component, TerminalLayout.FIND);
      myInnerPanel.moveToFront(component);
      myInnerPanel.revalidate();
      myInnerPanel.repaint();
      component.requestFocus();

      JediTermSearchComponentListener listener = new JediTermSearchComponentListener() {
        @Override
        public void searchSettingsChanged(@NotNull String textToFind, boolean ignoreCase) {
          findText(textToFind, ignoreCase);
        }

        @Override
        public void hideSearchComponent() {
          myInnerPanel.remove(component);
          myInnerPanel.revalidate();
          myInnerPanel.repaint();
          myFindComponent = null;
          myTerminalPanel.setFindResult(null);
          myTerminalPanel.requestFocusInWindow();
        }

        @Override
        public void selectNextFindResult() {
          myFindComponent.onResultUpdated(myTerminalPanel.selectNextFindResultItem());
        }

        @Override
        public void selectPrevFindResult() {
          myFindComponent.onResultUpdated(myTerminalPanel.selectPrevFindResultItem());
        }
      };
      myFindComponent.addListener(listener);

      myFindComponent.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent keyEvent) {
          if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            listener.hideSearchComponent();
          }
          else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER || keyEvent.getKeyCode() == KeyEvent.VK_DOWN) {
            listener.selectNextFindResult();
          }
          else if (keyEvent.getKeyCode() == KeyEvent.VK_UP) {
            listener.selectPrevFindResult();
          }
        }
      });
    } else {
      myFindComponent.getComponent().requestFocus();
    }
  }

  protected @NotNull JediTermSearchComponent createSearchComponent() {
    return new JediTermDefaultSearchComponent(this);
  }

  private void findText(String text, boolean ignoreCase) {
    FindResult results = TerminalSearchUtil.searchInTerminalTextBuffer(getTerminalTextBuffer(), text, ignoreCase);
    myTerminalPanel.setFindResult(results);
    myFindComponent.onResultUpdated(results);
    myScrollBar.repaint();
  }

  @Override
  public TerminalActionProvider getNextProvider() {
    return myNextActionProvider;
  }

  public void setNextProvider(TerminalActionProvider actionProvider) {
    this.myNextActionProvider = actionProvider;
  }

  private static class Session {
    private final EmulatorTask myEmulatorTask;
    private final Future<?> mySessionFuture;

    public Session(@NotNull EmulatorTask emulatorTask, @NotNull Future<?> sessionFuture) {
      myEmulatorTask = emulatorTask;
      mySessionFuture = sessionFuture;
    }

    void stop() {
      myEmulatorTask.requestStop();
      mySessionFuture.cancel(true);
    }
  }

  private class EmulatorTask implements Runnable {
    private final TerminalStarter myStarter;
    private final Runnable myOnDone;

    public EmulatorTask(@NotNull Runnable onDone) {
      myStarter = Objects.requireNonNull(myTerminalStarter);
      myOnDone = onDone;
    }

    @SuppressWarnings("removal")
    public void run() {
      TtyConnector ttyConnector = myStarter.getTtyConnector();
      try {
        if (ttyConnector.init(myPreConnectHandler)) {
          myTerminalPanel.addCustomKeyListener(myTerminalPanel.getTerminalKeyListener());
          myTerminalPanel.removeCustomKeyListener(myPreConnectHandler);
          myStarter.start();
        }
      }
      catch (Exception e) {
        LOG.error("Exception running terminal", e);
      }
      finally {
        try {
          ttyConnector.close();
        }
        catch (Exception ignored) {
        }
        try {
          for (TerminalWidgetListener listener : myListeners) {
            listener.allSessionsClosed(JediTermWidget.this);
          }
        }
        catch (Exception e) {
          LOG.error("Unhandled exception when closing terminal", e);
        }
        try {
          myOnDone.run();
        }
        catch (Exception e) {
          LOG.error("Unhandled exception when closing terminal", e);
        }
      }
    }

    void requestStop() {
      myStarter.requestEmulatorStop();
    }
  }

  /**
   * @deprecated use {@link #getTtyConnector()} to figure out if session started
   */
  @Deprecated
  public @Nullable TerminalStarter getTerminalStarter() {
    return myTerminalStarter;
  }

  protected void doWithTerminalStarter(@NotNull Consumer<TerminalStarter> consumer) {
    myTerminalStarterFuture.thenAccept(consumer);
  }

  private class FindResultScrollBarUI extends BasicScrollBarUI {

    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
      super.paintTrack(g, c, trackBounds);

      FindResult result = myTerminalPanel.getFindResult();
      if (result != null) {
        int modelHeight = scrollbar.getModel().getMaximum() - scrollbar.getModel().getMinimum();
        int anchorHeight = Math.max(2, trackBounds.height / modelHeight);

        Color color = mySettingsProvider.getTerminalColorPalette()
          .getBackground(Objects.requireNonNull(mySettingsProvider.getFoundPatternColor().getBackground()));
        g.setColor(AwtTransformers.toAwtColor(color));
        for (FindItem r : result.getItems()) {
          int where = trackBounds.height * r.getStart().y / modelHeight;
          g.fillRect(trackBounds.x, trackBounds.y + where, trackBounds.width, anchorHeight);
        }
      }
    }

  }

  private static class TerminalLayout implements LayoutManager {
    public static final String TERMINAL = "TERMINAL";
    public static final String SCROLL = "SCROLL";
    public static final String FIND = "FIND";

    private Component terminal;
    private Component scroll;
    private Component find;

    @Override
    public void addLayoutComponent(String name, Component comp) {
      if (TERMINAL.equals(name)) {
        terminal = comp;
      } else if (FIND.equals(name)) {
        find = comp;
      } else if (SCROLL.equals(name)) {
        scroll = comp;
      } else throw new IllegalArgumentException("unknown component name " + name);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      if (comp == terminal) {
        terminal = null;
      }
      if (comp == scroll) {
        scroll = null;
      }
      if (comp == find) {
        find = null;
      }
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
      synchronized (target.getTreeLock()) {
        Dimension dim = new Dimension(0, 0);

        if (terminal != null) {
          Dimension d = terminal.getPreferredSize();
          dim.width = Math.max(d.width, dim.width);
          dim.height = Math.max(d.height, dim.height);
        }

        if (scroll != null) {
          Dimension d = scroll.getPreferredSize();
          dim.width += d.width;
          dim.height = Math.max(d.height, dim.height);
        }

        if (find != null) {
          Dimension d = find.getPreferredSize();
          dim.width = Math.max(d.width, dim.width);
          dim.height = Math.max(d.height, dim.height);
        }

        Insets insets = target.getInsets();
        dim.width += insets.left + insets.right;
        dim.height += insets.top + insets.bottom;

        return dim;
      }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
      synchronized (target.getTreeLock()) {
        Dimension dim = new Dimension(0, 0);

        if (terminal != null) {
          Dimension d = terminal.getMinimumSize();
          dim.width = Math.max(d.width, dim.width);
          dim.height = Math.max(d.height, dim.height);
        }

        if (scroll != null) {
          Dimension d = scroll.getPreferredSize();
          dim.width += d.width;
          dim.height = Math.max(d.height, dim.height);
        }

        if (find != null) {
          Dimension d = find.getMinimumSize();
          dim.width = Math.max(d.width, dim.width);
          dim.height = Math.max(d.height, dim.height);
        }

        Insets insets = target.getInsets();
        dim.width += insets.left + insets.right;
        dim.height += insets.top + insets.bottom;

        return dim;
      }
    }

    @Override
    public void layoutContainer(Container target) {
      synchronized (target.getTreeLock()) {
        Insets insets = target.getInsets();
        int top = insets.top;
        int bottom = target.getHeight() - insets.bottom;
        int left = insets.left;
        int right = target.getWidth() - insets.right;

        Dimension scrollDim = new Dimension(0, 0);
        if (scroll != null) {
          scrollDim = scroll.getPreferredSize();
          scroll.setBounds(right - scrollDim.width, top, scrollDim.width, bottom - top);
        }

        if (terminal != null) {
          terminal.setBounds(left, top, right - left - scrollDim.width, bottom - top);
        }

        if (find != null) {
          Dimension d = find.getPreferredSize();
          find.setBounds(right - d.width - scrollDim.width, top, d.width, d.height);
        }
      }

    }
  }

  public void addHyperlinkFilter(HyperlinkFilter filter) {
    myTextProcessing.addHyperlinkFilter(filter);
  }

  @Override
  public void addListener(TerminalWidgetListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(TerminalWidgetListener listener) {
    myListeners.remove(listener);
  }
}
