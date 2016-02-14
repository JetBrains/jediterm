package com.jediterm.terminal.ui;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jediterm.terminal.*;
import com.jediterm.terminal.debug.DebugBufferType;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JediTerm terminal widget with UI implemented in Swing.
 * <p/>
 */
public class JediTermWidget extends JPanel implements TerminalSession, TerminalWidget, TerminalActionProvider {
  private static final Logger LOG = Logger.getLogger(JediTermWidget.class);

  protected final TerminalPanel myTerminalPanel;
  protected final JediTerminal myTerminal;
  protected final AtomicBoolean mySessionRunning = new AtomicBoolean();
  private FindComponent myFindComponent;
  protected PreConnectHandler myPreConnectHandler;
  private TtyConnector myTtyConnector;
  private TerminalStarter myTerminalStarter;
  private Thread myEmuThread;
  private final SettingsProvider mySettingsProvider;
  private TerminalActionProvider myNextActionProvider;
  private JLayeredPane myInnerPanel;

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
    TerminalTextBuffer terminalTextBuffer = new TerminalTextBuffer(columns, lines, styleState, settingsProvider.getBufferMaxLinesCount());

    myTerminalPanel = createTerminalPanel(mySettingsProvider, styleState, terminalTextBuffer);
    myTerminal = new JediTerminal(myTerminalPanel, terminalTextBuffer, styleState);

    myTerminal.setModeEnabled(TerminalMode.AltSendsEscape, mySettingsProvider.altSendsEscape());

    myTerminalPanel.addTerminalMouseListener(myTerminal);
    myTerminalPanel.setNextProvider(this);
    myTerminalPanel.setCoordAccessor(myTerminal);

    myPreConnectHandler = createPreConnectHandler(myTerminal);
    myTerminalPanel.setKeyListener(myPreConnectHandler);
    JScrollBar scrollBar = createScrollBar();

    JPanel terminalPanelWithScrolling = new JPanel(new BorderLayout());

    terminalPanelWithScrolling.add(myTerminalPanel, BorderLayout.CENTER);
    terminalPanelWithScrolling.add(scrollBar, BorderLayout.EAST);
    terminalPanelWithScrolling.setOpaque(false);

    myInnerPanel = new JLayeredPane();
    myInnerPanel.setFocusable(false);

    myInnerPanel.setLayout(new TerminalLayout());
    myInnerPanel.add(terminalPanelWithScrolling, TerminalLayout.TERMINAL);

    add(myInnerPanel, BorderLayout.CENTER);

    scrollBar.setModel(myTerminalPanel.getBoundedRangeModel());
    mySessionRunning.set(false);

    myTerminalPanel.init();

    myTerminalPanel.setVisible(true);
  }


  protected JScrollBar createScrollBar() {
    return new JScrollBar();
  }

  protected StyleState createDefaultStyle() {
    StyleState styleState = new StyleState();
    styleState.setDefaultStyle(mySettingsProvider.getDefaultStyle());
    return styleState;
  }

  protected TerminalPanel createTerminalPanel(SettingsProvider settingsProvider, StyleState styleState, TerminalTextBuffer terminalTextBuffer) {
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
    return new TerminalStarter(terminal, connector);
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
  public TerminalSession createTerminalSession(TtyConnector ttyConnector) {
    setTtyConnector(ttyConnector);
    return this;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void close() {
    myTerminalStarter.close();
  }

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList(new TerminalAction("Find", mySettingsProvider.getFindKeyStrokes(),
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
      myFindComponent = createFindComponent();

      final JComponent component = myFindComponent.getComponent();
      myInnerPanel.add(component, TerminalLayout.FIND);
      myInnerPanel.moveToFront(component);
      myInnerPanel.revalidate();
      myInnerPanel.repaint();
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
      });


      component.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent keyEvent) {
          if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
            myInnerPanel.remove(component);
            myInnerPanel.revalidate();
            myInnerPanel.repaint();
            myInnerPanel.requestFocus();
            myFindComponent = null;
            myTerminalPanel.setFindResult(null);
          } else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
            nextFindResultItem();
          } else {
            super.keyPressed(keyEvent);
          }
        }
      });
    }

  }

  private void nextFindResultItem() {
    myTerminalPanel.selectNextFindResultItem();
  }

  private void textUpdated() {
    findText(myFindComponent.getText());
  }

  private FindComponent createFindComponent() {
    return new FindComponent() {
      private final JTextField myTextField = new JTextField();

      @Override
      public String getText() {
        return myTextField.getText();
      }

      @Override
      public JComponent getComponent() {
        myTextField.setOpaque(true);
        myTextField.setText("");
        myTextField.setPreferredSize(new Dimension(myTerminalPanel.myCharSize.width*30, myTerminalPanel.myCharSize.height+3));
        myTextField.setEditable(true);
        return myTextField;
      }

      @Override
      public void addDocumentChangeListener(DocumentListener listener) {
        myTextField.getDocument().addDocumentListener(listener);
      }
    };
  }

  protected interface FindComponent {
    String getText();

    JComponent getComponent();

    void addDocumentChangeListener(DocumentListener listener);
  }

  private void findText(String text) {
    myTerminalPanel.setFindResult(myTerminal.searchInTerminalTextBuffer(text));
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
          myTerminalPanel.initKeyHandler();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTerminalPanel.requestFocusInWindow();
            }
          });
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
        myTerminalPanel.setKeyListener(myPreConnectHandler);
      }
    }
  }

  public TerminalStarter getTerminalStarter() {
    return myTerminalStarter;
  }

  private static class TerminalLayout implements LayoutManager {
    public static final String TERMINAL = "TERMINAL";
    public static final String FIND = "FIND";

    private Component terminal;
    private Component find;

    @Override
    public void addLayoutComponent(String name, Component comp) {
      if (TERMINAL.equals(name)) {
        terminal = comp;
      } else if (FIND.equals(name)) {
        find = comp;
      } else throw new IllegalArgumentException("unknown component name " + name);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      if (comp == terminal) {
        terminal = null;
      }
      if (comp == find) {
        find = comp;
      }
    }


    @Override
    public Dimension preferredLayoutSize(Container target) {
      synchronized (target.getTreeLock()) {
        Dimension dim = new Dimension(0, 0);

        if (terminal != null) {
          Dimension d = terminal.getPreferredSize();
          dim.width = d.width;
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
          dim.width = d.width;
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

        if (terminal != null) {
          terminal.setBounds(left, top, right - left, bottom - top);
        }

        if (find != null) {
          Dimension d = find.getPreferredSize();
          find.setBounds(right - d.width, top, d.width, d.height);
        }
      }


    }
  }
}
