package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jediterm.terminal.SubstringFinder.FindResult;
import com.jediterm.terminal.SubstringFinder.FindResult.FindItem;
import com.jediterm.terminal.*;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.TextProcessing;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 * <p/>
 */
public class JediTermWidget extends JPanel implements TerminalSession, TerminalWidget, TerminalActionProvider {
  private static final Logger LOG = Logger.getLogger(JediTermWidget.class);

  protected final TerminalPanel myTerminalPanel;
  private final JScrollBar myScrollBar;
  protected final JediTerminal myTerminal;
  protected final AtomicBoolean mySessionRunning = new AtomicBoolean();
  private SearchComponent myFindComponent;
  private final PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TerminalStarter myTerminalStarter;
  private Thread myEmuThread;
  protected final SettingsProvider mySettingsProvider;
  private TerminalActionProvider myNextActionProvider;
  private final JLayeredPane myLayeredPane;
  private final TextProcessing myTextProcessing;
  private final List<TerminalWidgetListener> myListeners = new CopyOnWriteArrayList<>();

  public JediTermWidget(@NotNull SettingsProvider settingsProvider) {
    this(80, 24, settingsProvider);
  }

  public JediTermWidget(Dimension dimension, SettingsProvider settingsProvider) {
    this(dimension.width, dimension.height, settingsProvider);
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
    myTerminal = new JediTerminal(myTerminalPanel, terminalTextBuffer, styleState);

    myTerminal.setModeEnabled(TerminalMode.AltSendsEscape, mySettingsProvider.altSendsEscape());

    myTerminalPanel.addTerminalMouseListener(myTerminal);
    myTerminalPanel.setNextProvider(this);
    myTerminalPanel.setCoordAccessor(myTerminal);

    myPreConnectHandler = createPreConnectHandler(myTerminal);
    myTerminalPanel.addCustomKeyListener(myPreConnectHandler);
    myScrollBar = createScrollBar();
    myScrollBar.setModel(myTerminalPanel.getBoundedRangeModel());

    myLayeredPane = createLayeredPane();
    setupLayeredPane();

    add(myLayeredPane, BorderLayout.CENTER);
    setFocusable(false);

    mySessionRunning.set(false);
    myTerminalPanel.init(myScrollBar);
    myTerminalPanel.setVisible(true);
  }

  protected @NotNull JScrollBar createScrollBar() {
    return new JScrollBar();
  }

  protected @NotNull JLayeredPane createLayeredPane() {
    return new JLayeredPane();
  }

  private void setupLayeredPane() {
    myLayeredPane.setFocusable(false);

    JPanel terminalWithScrollPanel = new JPanel(new BorderLayout());
    terminalWithScrollPanel.add(myTerminalPanel, BorderLayout.CENTER);
    terminalWithScrollPanel.add(myScrollBar, BorderLayout.EAST);

    myLayeredPane.setLayout(new TerminalLayoutManager(terminalWithScrollPanel, myScrollBar));
    myLayeredPane.add(terminalWithScrollPanel, JLayeredPane.DEFAULT_LAYER);
  }

  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());
    return styleState;
  }

  protected TerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider, @NotNull StyleState styleState, @NotNull TerminalTextBuffer terminalTextBuffer) {
    return new TerminalPanel(settingsProvider, terminalTextBuffer, styleState);
  }

  protected PreConnectHandler createPreConnectHandler(JediTerminal terminal) {
    return new PreConnectHandler(terminal);
  }

  public TerminalDisplay getTerminalDisplay() {
    return getTerminalPanel();
  }

  public TerminalPanel getTerminalPanel() {
    return myTerminalPanel;
  }

  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    myTtyConnector = ttyConnector;

    myTerminalStarter = createTerminalStarter(myTerminal, myTtyConnector);
    myTerminalPanel.setTerminalStarter(myTerminalStarter);
  }

  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new TerminalStarter(terminal, connector, new TtyBasedArrayDataStream(connector));
  }

  @Override
  public TtyConnector getTtyConnector() {
    return myTtyConnector;
  }

  @Override
  public Terminal getTerminal() {
    return myTerminal;
  }

  @Override
  public String getSessionName() {
    if (myTtyConnector != null) {
      return myTtyConnector.getName();
    } else {
      return "Session";
    }
  }

  public void start() {
    if (!mySessionRunning.get()) {
      myEmuThread = new Thread(new EmulatorTask());
      myEmuThread.start();
    } else {
      LOG.error("Should not try to start session again at this point... ");
    }
  }

  public void stop() {
    if (mySessionRunning.get() && myEmuThread != null) {
      myEmuThread.interrupt();
    }
  }

  public boolean isSessionRunning() {
    return mySessionRunning.get();
  }

  public String getBufferText(DebugBufferType type) {
    return type.getValue(this);
  }

  @Override
  public TerminalTextBuffer getTerminalTextBuffer() {
    return myTerminalPanel.getTerminalTextBuffer();
  }

  @Override
  public boolean requestFocusInWindow() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTerminalPanel.requestFocusInWindow();
      }
    });
    return super.requestFocusInWindow();
  }

  @Override
  public void requestFocus() {
    myTerminalPanel.requestFocus();
  }

  public boolean canOpenSession() {
    return !isSessionRunning();
  }

  @Override
  public void setTerminalPanelListener(TerminalPanelListener terminalPanelListener) {
    myTerminalPanel.setTerminalPanelListener(terminalPanelListener);
  }

  @Override
  public TerminalSession getCurrentSession() {
    return this;
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
    stop();
    if (myTerminalStarter != null) {
      myTerminalStarter.close();
    }
    myTerminalPanel.dispose();
  }

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList(new TerminalAction(mySettingsProvider.getFindActionPresentation(),
            new Predicate<KeyEvent>() {
              @Override
              public boolean apply(KeyEvent input) {
                showFindText();
                return true;
              }
            }).withMnemonicKey(KeyEvent.VK_F));
  }

  private void showFindText() {
    if (myFindComponent == null) {
      myFindComponent = createSearchComponent();

      final JComponent component = myFindComponent.getComponent();
      myLayeredPane.add(component, JLayeredPane.PALETTE_LAYER);
      myLayeredPane.revalidate();
      myLayeredPane.repaint();
      component.requestFocus();

      myFindComponent.addDocumentChangeListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          textUpdated();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          textUpdated();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          textUpdated();
        }

        private void textUpdated() {
          findText(myFindComponent.getText(), myFindComponent.ignoreCase());
        }
      });

      myFindComponent.addIgnoreCaseListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          findText(myFindComponent.getText(), myFindComponent.ignoreCase());
        }
      });

      myFindComponent.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent keyEvent) {
          if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            myLayeredPane.remove(component);
            myLayeredPane.revalidate();
            myLayeredPane.repaint();
            myFindComponent = null;
            myTerminalPanel.setFindResult(null);
            myTerminalPanel.requestFocusInWindow();
          } else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER || keyEvent.getKeyCode() == KeyEvent.VK_UP) {
            myFindComponent.nextFindResultItem(myTerminalPanel.selectNextFindResultItem());
          } else if (keyEvent.getKeyCode() == KeyEvent.VK_DOWN) {
            myFindComponent.prevFindResultItem(myTerminalPanel.selectPrevFindResultItem());
          } else {
            super.keyPressed(keyEvent);
          }
        }
      });
    } else {
      myFindComponent.getComponent().requestFocusInWindow();
    }
  }

  protected SearchComponent createSearchComponent() {
    return new SearchPanel();
  }

  protected interface SearchComponent {
    String getText();

    boolean ignoreCase();

    JComponent getComponent();

    void addDocumentChangeListener(DocumentListener listener);

    void addKeyListener(KeyListener listener);

    void addIgnoreCaseListener(ItemListener listener);

    void onResultUpdated(FindResult results);

    void nextFindResultItem(FindItem selectedItem);

    void prevFindResultItem(FindItem selectedItem);
  }

  private void findText(String text, boolean ignoreCase) {
    FindResult results = myTerminal.searchInTerminalTextBuffer(text, ignoreCase);
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

  class EmulatorTask implements Runnable {
    public void run() {
      try {
        mySessionRunning.set(true);
        Thread.currentThread().setName("Connector-" + myTtyConnector.getName());
        if (myTtyConnector.init(myPreConnectHandler)) {
          myTerminalPanel.addCustomKeyListener(myTerminalPanel.getTerminalKeyListener());
          myTerminalPanel.removeCustomKeyListener(myPreConnectHandler);
          myTerminalStarter.start();
        }
      } catch (Exception e) {
        LOG.error("Exception running terminal", e);
      } finally {
        try {
          myTtyConnector.close();
        } catch (Exception e) {
        }
        mySessionRunning.set(false);
        TerminalPanelListener terminalPanelListener = myTerminalPanel.getTerminalPanelListener();
        if (terminalPanelListener != null)
          terminalPanelListener.onSessionChanged(getCurrentSession());
        for (TerminalWidgetListener listener : myListeners) {
          listener.allSessionsClosed(JediTermWidget.this);
        }
        myTerminalPanel.addCustomKeyListener(myPreConnectHandler);
        myTerminalPanel.removeCustomKeyListener(myTerminalPanel.getTerminalKeyListener());
      }
    }
  }

  public TerminalStarter getTerminalStarter() {
    return myTerminalStarter;
  }

  public class SearchPanel extends JPanel implements SearchComponent {

    private final JTextField myTextField = new JTextField();
    private final JLabel label = new JLabel();
    private final JButton prev;
    private final JButton next;
    private final JCheckBox ignoreCaseCheckBox = new JCheckBox("Ignore Case", true);

    public SearchPanel() {
      next = createNextButton();
      next.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          nextFindResultItem(myTerminalPanel.selectNextFindResultItem());
        }
      });

      prev = createPrevButton();
      prev.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          prevFindResultItem(myTerminalPanel.selectPrevFindResultItem());
        }
      });

      myTextField.setPreferredSize(new Dimension(
              myTerminalPanel.myCharSize.width * 30,
              myTerminalPanel.myCharSize.height + 3));
      myTextField.setEditable(true);

      updateLabel(null);

      add(myTextField);
      add(ignoreCaseCheckBox);
      add(label);
      add(next);
      add(prev);

      setOpaque(true);
    }

    protected JButton createNextButton() {
      return new BasicArrowButton(SwingConstants.NORTH);
    }

    protected JButton createPrevButton() {
      return new BasicArrowButton(SwingConstants.SOUTH);
    }

    @Override
    public void nextFindResultItem(FindItem selectedItem) {
      updateLabel(selectedItem);
    }

    @Override
    public void prevFindResultItem(FindItem selectedItem) {
      updateLabel(selectedItem);
    }

    private void updateLabel(FindItem selectedItem) {
      FindResult result = myTerminalPanel.getFindResult();
      label.setText(((selectedItem != null) ? selectedItem.getIndex() : 0)
              + " of " + ((result != null) ? result.getItems().size() : 0));
    }

    @Override
    public void onResultUpdated(FindResult results) {
      updateLabel(null);
    }

    @Override
    public String getText() {
      return myTextField.getText();
    }

    @Override
    public boolean ignoreCase() {
      return ignoreCaseCheckBox.isSelected();
    }

    @Override
    public JComponent getComponent() {
      return this;
    }

    public void requestFocus() {
      myTextField.requestFocus();
    }

    @Override
    public void addDocumentChangeListener(DocumentListener listener) {
      myTextField.getDocument().addDocumentListener(listener);
    }

    @Override
    public void addKeyListener(KeyListener listener) {
      myTextField.addKeyListener(listener);
    }

    @Override
    public void addIgnoreCaseListener(ItemListener listener) {
      ignoreCaseCheckBox.addItemListener(listener);
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

  private static class TerminalLayoutManager implements LayoutManager {

    private final Component myMainChild;
    private final JScrollBar myScrollBar;

    public TerminalLayoutManager(@NotNull Component mainChild, @NotNull JScrollBar scrollBar) {
      myMainChild = mainChild;
      myScrollBar = scrollBar;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container target) {
      Component mainChild = findMainChild(target);
      return mainChild != null ? mainChild.getPreferredSize() : new Dimension(0, 0);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
      Component mainChild = findMainChild(target);
      return mainChild != null ? mainChild.getMinimumSize() : new Dimension(0, 0);
    }

    private @Nullable Component findMainChild(Container target) {
      synchronized (target.getTreeLock()) {
        Component[] children = target.getComponents();
        for (Component child : children) {
          if (child == myMainChild) {
            return child;
          }
        }
        return null;
      }
    }

    @Override
    public void layoutContainer(Container target) {
      synchronized (target.getTreeLock()) {
        Component[] children = target.getComponents();
        Rectangle targetBounds = target.getBounds();
        Insets insets = target.getInsets();
        for (Component child : children) {
          if (child == myMainChild) {
            child.setBounds(insets.left, insets.top,
                            targetBounds.width - insets.left - insets.right,
                            targetBounds.height - insets.top - insets.bottom);
          }
          else {
            // search component
            Dimension searchCompSize = child.getPreferredSize();
            child.setBounds(targetBounds.width - insets.right - myScrollBar.getWidth() - searchCompSize.width,
                            insets.top, searchCompSize.width, searchCompSize.height);

          }
        }
      }
    }
  }
}
