package com.jediterm.terminal.ui;

import com.jediterm.core.Color;
import com.jediterm.core.TerminalCoordinates;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.SubstringFinder.FindResult.FindItem;
import com.jediterm.terminal.TextStyle.Option;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.charset.CharacterSets;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.emulator.mouse.TerminalMouseListener;
import com.jediterm.terminal.model.*;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.ui.hyperlinks.LinkInfoEx;
import com.jediterm.terminal.ui.input.AwtMouseEvent;
import com.jediterm.terminal.ui.input.AwtMouseWheelEvent;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.text.AttributedCharacterIterator;
import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jediterm.terminal.ui.UtilKt.isWindows;

public class TerminalPanel extends JComponent implements TerminalDisplay, TerminalActionProvider {
  private static final Logger LOG = LoggerFactory.getLogger(TerminalPanel.class);
  private static final long serialVersionUID = -1048763516632093014L;

  public static final double SCROLL_SPEED = 0.05;

  /*font related*/
  private Font myNormalFont;
  private Font myItalicFont;
  private Font myBoldFont;
  private Font myBoldItalicFont;
  private int myDescent = 0;
  private int mySpaceBetweenLines = 0;
  protected final Dimension myCharSize = new Dimension();
  private boolean myMonospaced;
  private TermSize myTermSize;
  private boolean myInitialSizeSyncDone = false;

  private TerminalStarter myTerminalStarter = null;

  private MouseMode myMouseMode = MouseMode.MOUSE_REPORTING_NONE;
  private Point mySelectionStartPoint = null;
  private TerminalSelection mySelection = null;

  private final TerminalCopyPasteHandler myCopyPasteHandler;

  private final SettingsProvider mySettingsProvider;
  private final TerminalTextBuffer myTerminalTextBuffer;

  final private StyleState myStyleState;

  /*scroll and cursor*/
  final private TerminalCursor myCursor = new TerminalCursor();

  private final BlinkingTextTracker myTextBlinkingTracker = new BlinkingTextTracker();

  //we scroll a window [0, terminal_height] in the range [-history_lines_count, terminal_height]
  private final BoundedRangeModel myBoundedRangeModel = new DefaultBoundedRangeModel(0, 80, 0, 80);

  private boolean myScrollingEnabled = true;
  protected int myClientScrollOrigin;
  private final List<KeyListener> myCustomKeyListeners = new CopyOnWriteArrayList<>();

  private String myWindowTitle = "Terminal";

  private TerminalActionProvider myNextActionProvider;
  private String myInputMethodUncommittedChars;

  private Timer myRepaintTimer;
  private final AtomicInteger scrollDy = new AtomicInteger(0);
  private final AtomicBoolean myHistoryBufferLineCountChanged = new AtomicBoolean(false);
  private final AtomicBoolean needRepaint = new AtomicBoolean(true);

  private int myMaxFPS = 50;
  private int myBlinkingPeriod = 500;
  private TerminalCoordinates myCoordsAccessor;

  private SubstringFinder.FindResult myFindResult;

  private LinkInfo myHoveredHyperlink = null;

  private int myCursorType = Cursor.DEFAULT_CURSOR;
  private final TerminalKeyHandler myTerminalKeyHandler = new TerminalKeyHandler();
  private LinkInfoEx.HoverConsumer myLinkHoverConsumer;
  private TerminalTypeAheadManager myTypeAheadManager;
  private volatile boolean myBracketedPasteMode;
  private boolean myUsingAlternateBuffer = false;
  private boolean myFillCharacterBackgroundIncludingLineSpacing;
  private @Nullable TextStyle myCachedSelectionColor;
  private @Nullable TextStyle myCachedFoundPatternColor;

  public TerminalPanel(@NotNull SettingsProvider settingsProvider, @NotNull TerminalTextBuffer terminalTextBuffer, @NotNull StyleState styleState) {
    mySettingsProvider = settingsProvider;
    myTerminalTextBuffer = terminalTextBuffer;
    myStyleState = styleState;
    myTermSize = new TermSize(terminalTextBuffer.getWidth(), terminalTextBuffer.getHeight());
    myMaxFPS = mySettingsProvider.maxRefreshRate();
    myCopyPasteHandler = createCopyPasteHandler();

    updateScrolling(true);

    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);

    terminalTextBuffer.addModelListener(this::repaint);
    terminalTextBuffer.addTypeAheadModelListener(this::repaint);
    terminalTextBuffer.addHistoryBufferListener(() -> myHistoryBufferLineCountChanged.set(true));
  }

  void setTypeAheadManager(@NotNull TerminalTypeAheadManager typeAheadManager) {
    myTypeAheadManager = typeAheadManager;
  }

  @NotNull
  protected TerminalCopyPasteHandler createCopyPasteHandler() {
    return new DefaultTerminalCopyPasteHandler();
  }

  @Override
  public void repaint() {
    needRepaint.set(true);
  }

  private void doRepaint() {
    super.repaint();
  }

  protected void reinitFontAndResize() {
    initFont();

    sizeTerminalFromComponent();
  }

  protected void initFont() {
    myNormalFont = createFont();
    myBoldFont = myNormalFont.deriveFont(Font.BOLD);
    myItalicFont = myNormalFont.deriveFont(Font.ITALIC);
    myBoldItalicFont = myNormalFont.deriveFont(Font.BOLD | Font.ITALIC);

    establishFontMetrics();
  }

  public void init(@NotNull JScrollBar scrollBar) {
    initFont();

    setPreferredSize(new java.awt.Dimension(getPixelWidth(), getPixelHeight()));

    setFocusable(true);
    enableInputMethods(true);
    setDoubleBuffered(true);

    setFocusTraversalKeysEnabled(false);

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleHyperlinks(e.getPoint());
      }

      @Override
      public void mouseDragged(final MouseEvent e) {
        if (!isLocalMouseAction(e)) {
          return;
        }

        final Point charCoords = panelToCharCoords(e.getPoint());

        if (mySelection == null) {
          // prevent unlikely case where drag started outside terminal panel
          if (mySelectionStartPoint == null) {
            mySelectionStartPoint = charCoords;
          }
          mySelection = new TerminalSelection(new Point(mySelectionStartPoint));
        }
        repaint();
        mySelection.updateEnd(charCoords);
        if (mySettingsProvider.copyOnSelect()) {
          handleCopyOnSelect();
        }

        if (e.getPoint().y < 0) {
          moveScrollBar((int) ((e.getPoint().y) * SCROLL_SPEED));
        }
        if (e.getPoint().y > getPixelHeight()) {
          moveScrollBar((int) ((e.getPoint().y - getPixelHeight()) * SCROLL_SPEED));
        }
      }
    });

    addMouseWheelListener(e -> {
      if (isLocalMouseAction(e)) {
        handleMouseWheelEvent(e, scrollBar);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (myLinkHoverConsumer != null) {
          myLinkHoverConsumer.onMouseExited();
          myLinkHoverConsumer = null;
        }
        updateHoveredHyperlink(null);
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          if (e.getClickCount() == 1) {
            mySelectionStartPoint = panelToCharCoords(e.getPoint());
            mySelection = null;
            repaint();
          }
        }
      }

      @Override
      public void mouseReleased(final MouseEvent e) {
        requestFocusInWindow();
        repaint();
      }

      @Override
      public void mouseClicked(final MouseEvent e) {
        requestFocusInWindow();
        HyperlinkStyle hyperlink = isFollowLinkEvent(e) ? findHyperlink(e.getPoint()) : null;
        if (hyperlink != null) {
          hyperlink.getLinkInfo().navigate();
        } else if (e.getButton() == MouseEvent.BUTTON1 && isLocalMouseAction(e)) {
          int count = e.getClickCount();
          if (count == 1) {
            // do nothing
          } else if (count == 2) {
            // select word
            final Point charCoords = panelToCharCoords(e.getPoint());
            Point start = SelectionUtil.getPreviousSeparator(charCoords, myTerminalTextBuffer);
            Point stop = SelectionUtil.getNextSeparator(charCoords, myTerminalTextBuffer);
            mySelection = new TerminalSelection(start);
            mySelection.updateEnd(stop);

            if (mySettingsProvider.copyOnSelect()) {
              handleCopyOnSelect();
            }
          } else if (count == 3) {
            // select line
            final Point charCoords = panelToCharCoords(e.getPoint());
            int startLine = charCoords.y;
            while (startLine > -getScrollBuffer().getLineCount()
                    && myTerminalTextBuffer.getLine(startLine - 1).isWrapped()) {
              startLine--;
            }
            int endLine = charCoords.y;
            while (endLine < myTerminalTextBuffer.getHeight()
                    && myTerminalTextBuffer.getLine(endLine).isWrapped()) {
              endLine++;
            }
            mySelection = new TerminalSelection(new Point(0, startLine));
            mySelection.updateEnd(new Point(myTermSize.getColumns(), endLine));

            if (mySettingsProvider.copyOnSelect()) {
              handleCopyOnSelect();
            }
          }
        } else if (e.getButton() == MouseEvent.BUTTON2 && mySettingsProvider.pasteOnMiddleMouseClick() && isLocalMouseAction(e)) {
          handlePasteSelection();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
          HyperlinkStyle contextHyperlink = findHyperlink(e.getPoint());
          TerminalActionProvider provider = getTerminalActionProvider(contextHyperlink != null ? contextHyperlink.getLinkInfo() : null, e);
          JPopupMenu popup = createPopupMenu(provider);
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
        repaint();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        sizeTerminalFromComponent();
      }
    });

    addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        // replace with com.intellij.util.ui.update.UiNotifyConnector#doWhenFirstShown when merged with intellij
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
          SwingUtilities.invokeLater(() -> sizeTerminalFromComponent());
          removeHierarchyListener(this);
        }
      }
    });

    myFillCharacterBackgroundIncludingLineSpacing = mySettingsProvider.shouldFillCharacterBackgroundIncludingLineSpacing();
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myFillCharacterBackgroundIncludingLineSpacing = mySettingsProvider.shouldFillCharacterBackgroundIncludingLineSpacing();
        myCursor.cursorChanged();
      }

      @Override
      public void focusLost(FocusEvent e) {
        myCursor.cursorChanged();

        handleHyperlinks(e.getComponent());
      }
    });

    myBoundedRangeModel.addChangeListener(e -> {
      myClientScrollOrigin = myBoundedRangeModel.getValue();
      repaint();
    });

    createRepaintTimer();
  }

  private boolean isFollowLinkEvent(@NotNull MouseEvent e) {
    return myCursorType == Cursor.HAND_CURSOR && e.getButton() == MouseEvent.BUTTON1;
  }

  protected void handleMouseWheelEvent(@NotNull MouseWheelEvent e, @NotNull JScrollBar scrollBar) {
    if (e.isShiftDown() || e.getUnitsToScroll() == 0 || Math.abs(e.getPreciseWheelRotation()) < 0.01) {
      return;
    }
    moveScrollBar(e.getUnitsToScroll());
    e.consume();
  }

  private void handleHyperlinks(@NotNull java.awt.Point panelPoint) {
    Cell cell = panelPointToCell(panelPoint);
    HyperlinkStyle linkStyle = findHyperlink(cell);
    LinkInfo linkInfo = linkStyle != null ? linkStyle.getLinkInfo() : null;
    LinkInfoEx.HoverConsumer linkHoverConsumer = LinkInfoEx.getHoverConsumer(linkInfo);
    if (linkHoverConsumer != myLinkHoverConsumer) {
      if (myLinkHoverConsumer != null) {
        myLinkHoverConsumer.onMouseExited();
      }
      if (linkHoverConsumer != null) {
        LineCellInterval lineCellInterval = findIntervalWithStyle(cell, linkStyle);
        linkHoverConsumer.onMouseEntered(this, getBounds(lineCellInterval));
      }
    }
    myLinkHoverConsumer = linkHoverConsumer;
    if (linkStyle != null && linkStyle.getHighlightMode() != HyperlinkStyle.HighlightMode.NEVER) {
      updateHoveredHyperlink(linkStyle.getLinkInfo());
    }
    else {
      updateHoveredHyperlink(null);
    }
  }

  private void updateHoveredHyperlink(@Nullable LinkInfo hoveredHyperlink) {
    if (myHoveredHyperlink != hoveredHyperlink) {
      updateCursor(hoveredHyperlink != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
      myHoveredHyperlink = hoveredHyperlink;
      repaint();
    }
  }

  private @NotNull LineCellInterval findIntervalWithStyle(@NotNull Cell initialCell, @NotNull HyperlinkStyle style) {
    int startColumn = initialCell.getColumn();
    while (startColumn > 0 && style == myTerminalTextBuffer.getStyleAt(startColumn - 1, initialCell.getLine())) {
      startColumn--;
    }
    int endColumn = initialCell.getColumn();
    while (endColumn < myTerminalTextBuffer.getWidth() - 1 && style == myTerminalTextBuffer.getStyleAt(endColumn + 1, initialCell.getLine())) {
      endColumn++;
    }
    return new LineCellInterval(initialCell.getLine(), startColumn, endColumn);
  }

  private void handleHyperlinks(Component component) {
    PointerInfo a = MouseInfo.getPointerInfo();
    if (a != null) {
      java.awt.Point b = a.getLocation();
      SwingUtilities.convertPointFromScreen(b, component);
      handleHyperlinks(b);
    }
  }

  private @Nullable HyperlinkStyle findHyperlink(@NotNull java.awt.Point p) {
    return findHyperlink(panelPointToCell(p));
  }

  private @Nullable HyperlinkStyle findHyperlink(@Nullable Cell cell) {
    if (cell != null && cell.getColumn() >= 0 && cell.getColumn() < myTerminalTextBuffer.getWidth() &&
      cell.getLine() >= -myTerminalTextBuffer.getHistoryLinesCount() && cell.getLine() <= myTerminalTextBuffer.getHeight()) {
      TextStyle style = myTerminalTextBuffer.getStyleAt(cell.getColumn(), cell.getLine());
      if (style instanceof HyperlinkStyle) {
        return (HyperlinkStyle) style;
      }
    }
    return null;
  }

  private void updateCursor(int cursorType) {
    if (cursorType != myCursorType) {
      myCursorType = cursorType;
      setCursor(new Cursor(myCursorType));
    }
  }

  private void createRepaintTimer() {
    if (myRepaintTimer != null) {
      myRepaintTimer.stop();
    }
    myRepaintTimer = new Timer(1000 / myMaxFPS, new WeakRedrawTimer(this));
    myRepaintTimer.start();
  }

  public boolean isLocalMouseAction(MouseEvent e) {
    return mySettingsProvider.forceActionOnMouseReporting() || (isMouseReporting() == e.isShiftDown());
  }

  public boolean isRemoteMouseAction(MouseEvent e) {
    return isMouseReporting() && !e.isShiftDown();
  }

  public void setBlinkingPeriod(int blinkingPeriod) {
    myBlinkingPeriod = blinkingPeriod;
  }

  public void setCoordAccessor(TerminalCoordinates coordAccessor) {
    myCoordsAccessor = coordAccessor;
  }

  public void setFindResult(@Nullable SubstringFinder.FindResult findResult) {
    myFindResult = findResult;
    repaint();
  }

  public SubstringFinder.FindResult getFindResult() {
    return myFindResult;
  }

  public @Nullable SubstringFinder.FindResult selectPrevFindResultItem() {
    return selectPrevOrNextFindResultItem(false);
  }

  public @Nullable SubstringFinder.FindResult selectNextFindResultItem() {
    return selectPrevOrNextFindResultItem(true);
  }

  protected @Nullable SubstringFinder.FindResult selectPrevOrNextFindResultItem(boolean next) {
    if (myFindResult != null && !myFindResult.getItems().isEmpty()) {
      FindItem item = next ? myFindResult.nextFindItem() : myFindResult.prevFindItem();
      mySelection = new TerminalSelection(new Point(item.getStart().x, item.getStart().y - myTerminalTextBuffer.getHistoryLinesCount()),
        new Point(item.getEnd().x, item.getEnd().y - myTerminalTextBuffer.getHistoryLinesCount()));
      if (mySelection.getStart().y < getTerminalTextBuffer().getHeight() / 2) {
        myBoundedRangeModel.setValue(mySelection.getStart().y - getTerminalTextBuffer().getHeight() / 2);
      }
      else {
        myBoundedRangeModel.setValue(0);
      }
      repaint();
      return myFindResult;
    }
    return null;
  }

  static class WeakRedrawTimer implements ActionListener {

    private WeakReference<TerminalPanel> ref;

    public WeakRedrawTimer(TerminalPanel terminalPanel) {
      this.ref = new WeakReference<TerminalPanel>(terminalPanel);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      TerminalPanel terminalPanel = ref.get();
      if (terminalPanel != null) {
        terminalPanel.myCursor.changeStateIfNeeded();
        terminalPanel.myTextBlinkingTracker.updateState(terminalPanel.mySettingsProvider, terminalPanel);
        terminalPanel.updateScrolling(false);
        if (terminalPanel.needRepaint.getAndSet(false)) {
          try {
            terminalPanel.doRepaint();
          } catch (Exception ex) {
            LOG.error("Error while terminal panel redraw", ex);
          }
        }
      } else { // terminalPanel was garbage collected
        Timer timer = (Timer) e.getSource();
        timer.removeActionListener(this);
        timer.stop();
      }
    }
  }

  @Override
  public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
    myMouseMode = mouseMode;
  }

  @Override
  public void setMouseFormat(@NotNull MouseFormat mouseFormat) {}

  private boolean isMouseReporting() {
    return myMouseMode != MouseMode.MOUSE_REPORTING_NONE;
  }

  /**
   * Scroll to bottom to ensure the cursor will be visible.
   */
  private void scrollToBottom() {
    // Scroll to bottom even if the cursor is on the last line, i.e. it's currently visible.
    // This will address the cases when the scroll is fixed to show some history lines, Enter is hit and after
    // Enter processing, the cursor will be pushed out of visible area unless scroll is reset to screen buffer.
    int delta = 1;
    int zeroBasedCursorY = myCursor.myCursorCoordinates.y - 1;
    if (zeroBasedCursorY + delta >= myBoundedRangeModel.getValue() + myBoundedRangeModel.getExtent()) {
      myBoundedRangeModel.setValue(0);
    }
  }

  private void pageUp() {
    moveScrollBar(-myTermSize.getRows());
  }

  private void pageDown() {
    moveScrollBar(myTermSize.getRows());
  }

  private void scrollUp() {
    moveScrollBar(-1);
  }

  private void scrollDown() {
    moveScrollBar(1);
  }

  private void moveScrollBar(int k) {
    myBoundedRangeModel.setValue(myBoundedRangeModel.getValue() + k);
  }

  protected Font createFont() {
    return mySettingsProvider.getTerminalFont();
  }

  private @NotNull Point panelToCharCoords(final java.awt.Point p) {
    Cell cell = panelPointToCell(p);
    return new Point(cell.getColumn(), cell.getLine());
  }

  private @NotNull Cell panelPointToCell(@NotNull java.awt.Point p) {
    int x = Math.min((p.x - getInsetX()) / myCharSize.width, getColumnCount() - 1);
    x = Math.max(0, x);
    int y = Math.min(p.y / myCharSize.height, getRowCount() - 1) + myClientScrollOrigin;
    return new Cell(y, x);
  }

  private void copySelection(@Nullable Point selectionStart,
                             @Nullable Point selectionEnd,
                             boolean useSystemSelectionClipboardIfAvailable) {
    if (selectionStart == null || selectionEnd == null) {
      return;
    }
    String selectionText = SelectionUtil.getSelectionText(selectionStart, selectionEnd, myTerminalTextBuffer);
    if (selectionText.length() != 0) {
      myCopyPasteHandler.setContents(selectionText, useSystemSelectionClipboardIfAvailable);
    }
  }

  private void pasteFromClipboard(boolean useSystemSelectionClipboardIfAvailable) {
    String text = myCopyPasteHandler.getContents(useSystemSelectionClipboardIfAvailable);

    if (text == null) {
      return;
    }

    try {
      // Sanitize clipboard text to use CR as the line separator.
      // See https://github.com/JetBrains/jediterm/issues/136.
      if (!isWindows()) {
        // On Windows, Java automatically does this CRLF->LF sanitization, but
        // other terminals on Unix typically also do this sanitization, so
        // maybe JediTerm also should.
        text = text.replace("\r\n", "\n");
      }
      text = text.replace('\n', '\r');

      if (myBracketedPasteMode) {
        text = "\u001b[200~" + text + "\u001b[201~";
      }
      myTerminalStarter.sendString(text, true);
    } catch (RuntimeException e) {
      LOG.info("", e);
    }
  }

  @Nullable
  private String getClipboardString() {
    return myCopyPasteHandler.getContents(false);
  }

  protected void drawImage(Graphics2D gfx, BufferedImage image, int x, int y, ImageObserver observer) {
    gfx.drawImage(image, x, y,
            image.getWidth(), image.getHeight(), observer);
  }

  protected BufferedImage createBufferedImage(int width, int height) {
    return new BufferedImage(width, height,
            BufferedImage.TYPE_INT_RGB);
  }

  public @Nullable TermSize getTerminalSizeFromComponent() {
    int columns = (getWidth() - getInsetX()) / myCharSize.width;
    int rows = getHeight() / myCharSize.height;
    return rows > 0 && columns > 0 ? new TermSize(columns, rows) : null;
  }

  private void sizeTerminalFromComponent() {
    if (myTerminalStarter != null) {
      TermSize newSize = getTerminalSizeFromComponent();
      if (newSize != null) {
        newSize = JediTerminal.ensureTermMinimumSize(newSize);
        if (!myTermSize.equals(newSize) || !myInitialSizeSyncDone) {
          myTermSize = newSize;
          myInitialSizeSyncDone = true;
          myTypeAheadManager.onResize();
          myTerminalStarter.postResize(newSize, RequestOrigin.User);
        }
      }
    }
  }

  public void setTerminalStarter(final TerminalStarter terminalStarter) {
    myTerminalStarter = terminalStarter;
    sizeTerminalFromComponent();
  }

  public void addCustomKeyListener(@NotNull KeyListener keyListener) {
    myCustomKeyListeners.add(keyListener);
  }

  public void removeCustomKeyListener(@NotNull KeyListener keyListener) {
    myCustomKeyListeners.remove(keyListener);
  }

  @Override
  public void onResize(@NotNull TermSize newTermSize, @NotNull RequestOrigin origin) {
    myTermSize = newTermSize;

    setPreferredSize(new java.awt.Dimension(getPixelWidth(), getPixelHeight()));
    SwingUtilities.invokeLater(() -> updateScrolling(true));
  }

  private void establishFontMetrics() {
    final BufferedImage img = createBufferedImage(1, 1);
    final Graphics2D graphics = img.createGraphics();
    graphics.setFont(myNormalFont);

    final float lineSpacing = getLineSpacing();
    final FontMetrics fo = graphics.getFontMetrics();

    myCharSize.width = fo.charWidth('W');
    int fontMetricsHeight = fo.getHeight();
    myCharSize.height = (int)Math.ceil(fontMetricsHeight * lineSpacing);
    mySpaceBetweenLines = Math.max(0, ((myCharSize.height - fontMetricsHeight) / 2) * 2);
    myDescent = fo.getDescent();
    if (LOG.isDebugEnabled()) {
      // The magic +2 here is to give lines a tiny bit of extra height to avoid clipping when rendering some Apple
      // emoji, which are slightly higher than the font metrics reported character height :(
      int oldCharHeight = fontMetricsHeight + (int) (lineSpacing * 2) + 2;
      int oldDescent = fo.getDescent() + (int)lineSpacing;
      LOG.debug("charHeight=" + oldCharHeight + "->" + myCharSize.height +
        ", descent=" + oldDescent + "->" + myDescent);
    }

    myMonospaced = isMonospaced(fo);
    if (!myMonospaced) {
      LOG.info("WARNING: Font " + myNormalFont.getName() + " is non-monospaced");
    }

    img.flush();
    graphics.dispose();
  }

  private float getLineSpacing() {
    if (myTerminalTextBuffer.isUsingAlternateBuffer() && mySettingsProvider.shouldDisableLineSpacingForAlternateScreenBuffer()) {
      return 1.0f;
    }
    return mySettingsProvider.getLineSpacing();
  }

  private static boolean isMonospaced(FontMetrics fontMetrics) {
    boolean isMonospaced = true;
    int charWidth = -1;
    for (int codePoint = 0; codePoint < 128; codePoint++) {
      if (Character.isValidCodePoint(codePoint)) {
        char character = (char) codePoint;
        if (isWordCharacter(character)) {
          int w = fontMetrics.charWidth(character);
          if (charWidth != -1) {
            if (w != charWidth) {
              isMonospaced = false;
              break;
            }
          } else {
            charWidth = w;
          }
        }
      }
    }
    return isMonospaced;
  }

  private static boolean isWordCharacter(char character) {
    return Character.isLetterOrDigit(character);
  }

  protected void setupAntialiasing(Graphics graphics) {
    if (graphics instanceof Graphics2D) {
      Graphics2D myGfx = (Graphics2D) graphics;
      final Object mode = mySettingsProvider.useAntialiasing() ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
              : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
      final RenderingHints hints = new RenderingHints(
              RenderingHints.KEY_TEXT_ANTIALIASING, mode);
      myGfx.setRenderingHints(hints);
    }
  }

  @Override
  public @NotNull java.awt.Color getBackground() {
    return AwtTransformers.toAwtColor(getWindowBackground());
  }

  @Override
  public @NotNull java.awt.Color getForeground() {
    return AwtTransformers.toAwtColor(getWindowForeground());
  }

  @Override
  public void paintComponent(final Graphics g) {
    resetColorCache();
    final Graphics2D gfx = (Graphics2D) g;

    setupAntialiasing(gfx);

    gfx.setColor(getBackground());

    gfx.fillRect(0, 0, getWidth(), getHeight());

    try {
      myTerminalTextBuffer.lock();
      // update myClientScrollOrigin as scrollArea might have been invoked after last WeakRedrawTimer action
      updateScrolling(false);
      myTerminalTextBuffer.processHistoryAndScreenLines(myClientScrollOrigin, myTermSize.getRows(), new StyledTextConsumer() {
        final int columnCount = getColumnCount();

        @Override
        public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
          int row = y - startRow;
          drawCharacters(x, row, style, characters, gfx, myFillCharacterBackgroundIncludingLineSpacing);

          if (myFindResult != null) {
            List<Pair<Integer, Integer>> ranges = myFindResult.getRanges(characters);
            if (ranges != null && !ranges.isEmpty()) {
              TextStyle foundPatternStyle = getFoundPattern(style);
              for (Pair<Integer, Integer> range : ranges) {
                CharBuffer foundPatternChars = characters.subBuffer(range);
                drawCharacters(x + range.getFirst(), row, foundPatternStyle, foundPatternChars, gfx);
              }
            }
          }

          if (mySelection != null) {
            Pair<Integer, Integer> interval = mySelection.intersect(x, row + myClientScrollOrigin, characters.length());
            if (interval != null) {
              TextStyle selectionStyle = getSelectionStyle(style);
              CharBuffer selectionChars = characters.subBuffer(interval.getFirst() - x, interval.getSecond());

              drawCharacters(interval.getFirst(), row, selectionStyle, selectionChars, gfx);
            }
          }
        }

        @Override
        public void consumeNul(int x, int y, int nulIndex, TextStyle style, CharBuffer characters, int startRow) {
          int row = y - startRow;
          if (mySelection != null) {
            // compute intersection with all NUL areas, non-breaking
            Pair<Integer, Integer> interval = mySelection.intersect(nulIndex, row + myClientScrollOrigin, columnCount - nulIndex);
            if (interval != null) {
              TextStyle selectionStyle = getSelectionStyle(style);
              drawCharacters(x, row, selectionStyle, characters, gfx);
              return;
            }
          }
          drawCharacters(x, row, style, characters, gfx);
        }

        @Override
        public void consumeQueue(int x, int y, int nulIndex, int startRow) {
          if (x < columnCount) {
            consumeNul(x, y, nulIndex, TextStyle.EMPTY, new CharBuffer(CharUtils.EMPTY_CHAR, columnCount - x), startRow);
          }
        }
      });

      int cursorY = myCursor.getCoordY();
      if (cursorY < getRowCount() && !hasUncommittedChars()) {
        int cursorX = myCursor.getCoordX();
        Pair<Character, TextStyle> sc = myTerminalTextBuffer.getStyledCharAt(cursorX, cursorY);
        String cursorChar = "" + sc.getFirst();
        if (Character.isHighSurrogate(sc.getFirst())) {
          cursorChar += myTerminalTextBuffer.getStyledCharAt(cursorX + 1, cursorY).getFirst();
        }
        TextStyle normalStyle = sc.getSecond() != null ? sc.getSecond() : myStyleState.getCurrent();
        TextStyle cursorStyle;
        if (inSelection(cursorX, cursorY)) {
          cursorStyle = getSelectionStyle(normalStyle);
        }
        else {
          cursorStyle = normalStyle;
        }
        myCursor.drawCursor(cursorChar, gfx, cursorStyle);
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
    resetColorCache();
    drawInputMethodUncommitedChars(gfx);

    drawMargins(gfx, getWidth(), getHeight());
  }

  private void resetColorCache() {
    myCachedSelectionColor = null;
    myCachedFoundPatternColor = null;
  }

  @NotNull
  private TextStyle getSelectionStyle(@NotNull TextStyle style) {
    if (mySettingsProvider.useInverseSelectionColor()) {
      return getInversedStyle(style);
    }
    TextStyle.Builder builder = style.toBuilder();
    TextStyle selectionStyle = getSelectionColor();
    builder.setBackground(selectionStyle.getBackground());
    builder.setForeground(selectionStyle.getForeground());
    if (builder instanceof HyperlinkStyle.Builder) {
      return ((HyperlinkStyle.Builder)builder).build(true);
    }
    return builder.build();
  }

  private @NotNull TextStyle getSelectionColor() {
    TextStyle selectionColor = myCachedSelectionColor;
    if (selectionColor == null) {
      selectionColor = mySettingsProvider.getSelectionColor();
      myCachedSelectionColor = selectionColor;
    }
    return selectionColor;
  }

  private @NotNull TextStyle getFoundPatternColor() {
    TextStyle foundPatternColor = myCachedFoundPatternColor;
    if (foundPatternColor == null) {
      foundPatternColor = mySettingsProvider.getFoundPatternColor();
      myCachedFoundPatternColor = foundPatternColor;
    }
    return foundPatternColor;
  }

  @NotNull
  private TextStyle getFoundPattern(@NotNull TextStyle style) {
    TextStyle.Builder builder = style.toBuilder();
    TextStyle foundPattern = getFoundPatternColor();
    builder.setBackground(foundPattern.getBackground());
    builder.setForeground(foundPattern.getForeground());
    return builder.build();
  }

  private void drawInputMethodUncommitedChars(Graphics2D gfx) {
    if (hasUncommittedChars()) {
      int xCoord = (myCursor.getCoordX() + 1) * myCharSize.width + getInsetX();

      int y = myCursor.getCoordY() + 1;

      int yCoord = y * myCharSize.height - 3;

      int len = (myInputMethodUncommittedChars.length()) * myCharSize.width;

      gfx.setColor(getBackground());
      gfx.fillRect(xCoord, (y - 1) * myCharSize.height - 3, len, myCharSize.height);

      gfx.setColor(getForeground());
      gfx.setFont(myNormalFont);

      gfx.drawString(myInputMethodUncommittedChars, xCoord, yCoord);
      Stroke saved = gfx.getStroke();
      BasicStroke dotted = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{0, 2, 0, 2}, 0);
      gfx.setStroke(dotted);

      gfx.drawLine(xCoord, yCoord, xCoord + len, yCoord);
      gfx.setStroke(saved);
    }
  }

  private boolean hasUncommittedChars() {
    return myInputMethodUncommittedChars != null && myInputMethodUncommittedChars.length() > 0;
  }

  private boolean inSelection(int x, int y) {
    return mySelection != null && mySelection.contains(new Point(x, y));
  }

  @Override
  public void processKeyEvent(final KeyEvent e) {
    handleKeyEvent(e);
  }

  // also called from com.intellij.terminal.JBTerminalPanel
  public void handleKeyEvent(@NotNull KeyEvent e) {
    final int id = e.getID();
    if (id == KeyEvent.KEY_PRESSED) {
      for (KeyListener keyListener : myCustomKeyListeners) {
        keyListener.keyPressed(e);
      }
    } else if (id == KeyEvent.KEY_TYPED) {
      for (KeyListener keyListener : myCustomKeyListeners) {
        keyListener.keyTyped(e);
      }
    }
  }

  public int getPixelWidth() {
    return myCharSize.width * myTermSize.getColumns() + getInsetX();
  }

  public int getPixelHeight() {
    return myCharSize.height * myTermSize.getRows();
  }

  private int getColumnCount() {
    return myTermSize.getColumns();
  }

  private int getRowCount() {
    return myTermSize.getRows();
  }

  public String getWindowTitle() {
    return myWindowTitle;
  }

  @Override
  public @NotNull Color getWindowForeground() {
    return toForeground(mySettingsProvider.getDefaultForeground());
  }

  @Override
  public @NotNull Color getWindowBackground() {
    return toBackground(mySettingsProvider.getDefaultBackground());
  }

  private @NotNull java.awt.Color getEffectiveForeground(@NotNull TextStyle style) {
    Color color = style.hasOption(Option.INVERSE) ? getBackground(style) : getForeground(style);
    return AwtTransformers.toAwtColor(color);
  }

  private @NotNull java.awt.Color getEffectiveBackground(@NotNull TextStyle style) {
    Color color = style.hasOption(Option.INVERSE) ? getForeground(style) : getBackground(style);
    return AwtTransformers.toAwtColor(color);
  }

  private @NotNull Color getForeground(@NotNull TextStyle style) {
    TerminalColor foreground = style.getForeground();
    return foreground != null ? toForeground(foreground) : getWindowForeground();
  }

  private @NotNull Color toForeground(@NotNull TerminalColor terminalColor) {
    if (terminalColor.isIndexed()) {
      return getPalette().getForeground(terminalColor);
    }
    return terminalColor.toColor();
  }

  private @NotNull Color getBackground(@NotNull TextStyle style) {
    TerminalColor background = style.getBackground();
    return background != null ? toBackground(background) : getWindowBackground();
  }

  private @NotNull Color toBackground(@NotNull TerminalColor terminalColor) {
    if (terminalColor.isIndexed()) {
      return getPalette().getBackground(terminalColor);
    }
    return terminalColor.toColor();
  }

  protected int getInsetX() {
    return 4;
  }

  public void addTerminalMouseListener(final TerminalMouseListener listener) {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (mySettingsProvider.enableMouseReporting() && isRemoteMouseAction(e)) {
          Point p = panelToCharCoords(e.getPoint());
          listener.mousePressed(p.x, p.y, new AwtMouseEvent(e));
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (mySettingsProvider.enableMouseReporting() && isRemoteMouseAction(e)) {
          Point p = panelToCharCoords(e.getPoint());
          listener.mouseReleased(p.x, p.y, new AwtMouseEvent(e));
        }
      }
    });

    addMouseWheelListener(e -> {
      if (mySettingsProvider.enableMouseReporting() && isRemoteMouseAction(e)) {
        mySelection = null;
        Point p = panelToCharCoords(e.getPoint());
        listener.mouseWheelMoved(p.x, p.y, new AwtMouseWheelEvent(e));
      }
      if (myTerminalTextBuffer.isUsingAlternateBuffer() && mySettingsProvider.sendArrowKeysInAlternativeMode()){
        //Send Arrow keys instead
        final byte[] arrowKeys;
        if (e.getWheelRotation() < 0) {
          arrowKeys = myTerminalStarter.getTerminal().getCodeForKey(KeyEvent.VK_UP, 0);
        }
        else {
          arrowKeys = myTerminalStarter.getTerminal().getCodeForKey(KeyEvent.VK_DOWN, 0);
        }
        for(int i = 0; i < Math.abs(e.getUnitsToScroll()); i++){
          myTerminalStarter.sendBytes(arrowKeys, false);
        }
        e.consume();
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        if (mySettingsProvider.enableMouseReporting() && isRemoteMouseAction(e)) {
          Point p = panelToCharCoords(e.getPoint());
          listener.mouseMoved(p.x, p.y, new AwtMouseEvent(e));
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (mySettingsProvider.enableMouseReporting() && isRemoteMouseAction(e)) {
          Point p = panelToCharCoords(e.getPoint());
          listener.mouseDragged(p.x, p.y, new AwtMouseEvent(e));
        }
      }
    });
  }

  @NotNull
  KeyListener getTerminalKeyListener() {
    return myTerminalKeyHandler;
  }

  private enum TerminalCursorState {
    SHOWING, HIDDEN, NO_FOCUS;
  }

  private class TerminalCursor {

    // cursor state
    private boolean myCursorIsShown; // blinking state
    private final Point myCursorCoordinates = new Point();
    private @Nullable CursorShape myShape;

    // terminal modes
    private boolean myShouldDrawCursor = true;

    private long myLastCursorChange;
    private boolean myCursorHasChanged;

    public void setX(int x) {
      myCursorCoordinates.x = x;
      cursorChanged();
    }

    public void setY(int y) {
      myCursorCoordinates.y = y;
      cursorChanged();
    }

    public int getCoordX() {
      if (myTypeAheadManager != null) {
        return myTypeAheadManager.getCursorX() - 1;
      }
      return myCursorCoordinates.x;
    }

    public int getCoordY() {
      return myCursorCoordinates.y - 1 - myClientScrollOrigin;
    }

    public void setShouldDrawCursor(boolean shouldDrawCursor) {
      myShouldDrawCursor = shouldDrawCursor;
    }

    public boolean isBlinking() {
      return getEffectiveShape().isBlinking() && (getBlinkingPeriod() > 0);
    }

    public void cursorChanged() {
      myCursorHasChanged = true;
      myLastCursorChange = System.currentTimeMillis();
      repaint();
    }

    private boolean cursorShouldChangeBlinkState(long currentTime) {
      return currentTime - myLastCursorChange > getBlinkingPeriod();
    }

    public void changeStateIfNeeded() {
      if (!isFocusOwner()) {
        return;
      }
      long currentTime = System.currentTimeMillis();
      if (cursorShouldChangeBlinkState(currentTime)) {
        myCursorIsShown = !myCursorIsShown;
        myLastCursorChange = currentTime;
        myCursorHasChanged = false;
        repaint();
      }
    }

    private TerminalCursorState computeBlinkingState() {
      if (!isBlinking() || myCursorHasChanged || myCursorIsShown) {
        return TerminalCursorState.SHOWING;
      }
      return TerminalCursorState.HIDDEN;
    }

    private TerminalCursorState computeCursorState() {
      if (!myShouldDrawCursor) {
        return TerminalCursorState.HIDDEN;
      }
      if (!isFocusOwner()) {
        return TerminalCursorState.NO_FOCUS;
      }
      return computeBlinkingState();
    }

    void drawCursor(String c, Graphics2D gfx, TextStyle style) {
      TerminalCursorState state = computeCursorState();

      // hidden: do nothing
      if (state == TerminalCursorState.HIDDEN) {
        return;
      }

      final int x = getCoordX();
      final int y = getCoordY();
      // Outside bounds of window: do nothing
      if (y < 0 || y >= myTermSize.getRows()) {
        return;
      }

      CharBuffer buf = new CharBuffer(c);
      int xCoord = x * myCharSize.width + getInsetX();
      int yCoord = y * myCharSize.height;
      int textLength = CharUtils.getTextLengthDoubleWidthAware(buf.getBuf(), buf.getStart(), buf.length(), mySettingsProvider.ambiguousCharsAreDoubleWidth());
      int height = Math.min(myCharSize.height, getHeight() - yCoord);
      int width = Math.min(textLength * TerminalPanel.this.myCharSize.width, TerminalPanel.this.getWidth() - xCoord);
      int lineStrokeSize = 2;

      java.awt.Color fgColor = getEffectiveForeground(style);
      TextStyle inversedStyle = getInversedStyle(style);
      java.awt.Color inverseBg = getEffectiveBackground(inversedStyle);

      switch (getEffectiveShape()) {
        case BLINK_BLOCK:
        case STEADY_BLOCK:
          if (state == TerminalCursorState.SHOWING) {
            gfx.setColor(inverseBg);
            gfx.fillRect(xCoord, yCoord, width, height);
            drawCharacters(x, y, inversedStyle, buf, gfx);
          } else {
            gfx.setColor(fgColor);
            gfx.drawRect(xCoord, yCoord, width, height);
          }
          break;

        case BLINK_UNDERLINE:
        case STEADY_UNDERLINE:
          gfx.setColor(fgColor);
          gfx.fillRect(xCoord, yCoord + height, width, lineStrokeSize);
          break;

        case BLINK_VERTICAL_BAR:
        case STEADY_VERTICAL_BAR:
          gfx.setColor(fgColor);
          gfx.fillRect(xCoord, yCoord, lineStrokeSize, height);
          break;
      }
    }

    void setShape(@Nullable CursorShape shape) {
      myShape = shape;
    }

    @NotNull CursorShape getEffectiveShape() {
      return Objects.requireNonNullElse(myShape, CursorShape.BLINK_BLOCK);
    }
  }

  private int getBlinkingPeriod() {
    if (myBlinkingPeriod != mySettingsProvider.caretBlinkingMs()) {
      setBlinkingPeriod(mySettingsProvider.caretBlinkingMs());
    }
    return myBlinkingPeriod;
  }

  protected void drawImage(Graphics2D g, BufferedImage image, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
    g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  @NotNull
  private TextStyle getInversedStyle(@NotNull TextStyle style) {
    TextStyle.Builder builder = new TextStyle.Builder(style);
    builder.setOption(Option.INVERSE, !style.hasOption(Option.INVERSE));
    if (style.getForeground() == null) {
      builder.setForeground(myStyleState.getDefaultForeground());
    }
    if (style.getBackground() == null) {
      builder.setBackground(myStyleState.getDefaultBackground());
    }
    return builder.build();
  }

  private void drawCharacters(int x, int y, TextStyle style, CharBuffer buf, Graphics2D gfx) {
    drawCharacters(x, y, style, buf, gfx, true);
  }

  private void drawCharacters(int x, int y, TextStyle style, CharBuffer buf, Graphics2D gfx,
                              boolean includeSpaceBetweenLines) {
    if (myTextBlinkingTracker.shouldBlinkNow(style)) {
      style = getInversedStyle(style);
    }

    int xCoord = x * myCharSize.width + getInsetX();
    int yCoord = y * myCharSize.height + (includeSpaceBetweenLines ? 0 : mySpaceBetweenLines / 2);

    if (xCoord < 0 || xCoord > getWidth() || yCoord < 0 || yCoord > getHeight()) {
      return;
    }

    int textLength = CharUtils.getTextLengthDoubleWidthAware(buf.getBuf(), buf.getStart(), buf.length(), mySettingsProvider.ambiguousCharsAreDoubleWidth());
    int height = Math.min(myCharSize.height - (includeSpaceBetweenLines ? 0 : mySpaceBetweenLines), getHeight() - yCoord);
    int width = Math.min(textLength * TerminalPanel.this.myCharSize.width, TerminalPanel.this.getWidth() - xCoord);

    if (style instanceof HyperlinkStyle) {
      HyperlinkStyle hyperlinkStyle = (HyperlinkStyle) style;

      if (hyperlinkStyle.getHighlightMode() == HyperlinkStyle.HighlightMode.ALWAYS || (isHoveredHyperlink(hyperlinkStyle) && hyperlinkStyle.getHighlightMode() == HyperlinkStyle.HighlightMode.HOVER)) {

        // substitute text style with the hyperlink highlight style if applicable
        style = hyperlinkStyle.getHighlightStyle();
      }
    }

    java.awt.Color backgroundColor = getEffectiveBackground(style);
    gfx.setColor(backgroundColor);
    gfx.fillRect(xCoord,
            yCoord,
            width,
            height);

    if (buf.isNul()) {
      return; // nothing more to do
    }

    gfx.setColor(getStyleForeground(style));

    drawChars(x, y, buf, style, gfx);

    if (style.hasOption(TextStyle.Option.UNDERLINED)) {
      int baseLine = (y + 1) * myCharSize.height - mySpaceBetweenLines / 2 - myDescent;
      int lineY = baseLine + 3;
      gfx.drawLine(xCoord, lineY, (x + textLength) * myCharSize.width + getInsetX(), lineY);
    }
  }

  private boolean isHoveredHyperlink(@NotNull HyperlinkStyle link) {
    return myHoveredHyperlink == link.getLinkInfo();
  }

  /**
   * Draw every char in separate terminal cell to guaranty equal width for different lines.
   * Nevertheless, to improve kerning we draw word characters as one block for monospaced fonts.
   */
  private void drawChars(int x, int y, @NotNull CharBuffer buf, @NotNull TextStyle style, @NotNull Graphics2D gfx) {
    // workaround to fix Swing bad rendering of bold special chars on Linux
    // TODO required for italic?
    CharBuffer renderingBuffer;
    if (mySettingsProvider.DECCompatibilityMode() && style.hasOption(TextStyle.Option.BOLD)) {
      renderingBuffer = CharUtils.heavyDecCompatibleBuffer(buf);
    } else {
      renderingBuffer = buf;
    }

    BreakIterator iterator = BreakIterator.getCharacterInstance();
    char[] text = renderingBuffer.clone().getBuf();
    iterator.setText(new String(text));
    int endOffset;
    int startOffset = 0;
    while ((endOffset = iterator.next()) != BreakIterator.DONE) {
      endOffset = extendEndOffset(text, iterator, startOffset, endOffset);
      int effectiveEndOffset = shiftDwcToEnd(text, startOffset, endOffset);
      if (effectiveEndOffset == startOffset) {
        startOffset = endOffset;
        continue; // nothing to draw
      }
      Font font = getFontToDisplay(text, startOffset, effectiveEndOffset, style);
      gfx.setFont(font);
      int descent = gfx.getFontMetrics(font).getDescent();
      int baseLine = (y + 1) * myCharSize.height - mySpaceBetweenLines / 2 - descent;
      int charWidth = myCharSize.width;
      int xCoord = (x + startOffset) * charWidth + getInsetX();
      int yCoord = y * myCharSize.height + mySpaceBetweenLines / 2;
      gfx.setClip(xCoord, yCoord, getWidth() - xCoord, getHeight() - yCoord);

      int emptyCells = endOffset - startOffset;
      if (emptyCells >= 2) {
        int drawnWidth = gfx.getFontMetrics(font).charsWidth(text, startOffset, effectiveEndOffset - startOffset);
        int emptySpace = Math.max(0, emptyCells * charWidth - drawnWidth);
        // paint a Unicode symbol closer to the center
        xCoord += emptySpace / 2;
      }
      gfx.drawChars(text, startOffset, effectiveEndOffset - startOffset, xCoord, baseLine);

      startOffset = endOffset;
    }
    gfx.setClip(null);
  }

  private static int shiftDwcToEnd(char[] text, int startOffset, int endOffset) {
    int ind = startOffset;
    for (int i = startOffset; i < endOffset; i++) {
      if (text[i] != CharUtils.DWC) {
        text[ind++] = text[i];
      }
    }
    Arrays.fill(text, ind, endOffset, CharUtils.DWC);
    return ind;
  }

  private static int extendEndOffset(char[] text, @NotNull BreakIterator iterator, int startOffset, int endOffset) {
    while (shouldExtend(text, startOffset, endOffset)) {
      int newEndOffset = iterator.next();
      if (newEndOffset == BreakIterator.DONE) {
        break;
      }
      if (newEndOffset - endOffset == 1 && !isUnicodePart(text, endOffset)) {
        iterator.previous(); // do not eat a plain char following Unicode symbol
        break;
      }
      startOffset = endOffset;
      endOffset = newEndOffset;
    }
    return endOffset;
  }

  private static boolean shouldExtend(char[] text, int startOffset, int endOffset) {
    if (endOffset - startOffset > 1) {
      return true;
    }
    if (isFormatChar(text, startOffset, endOffset)) {
      return true;
    }
    return endOffset < text.length && text[endOffset] == CharUtils.DWC;
  }

  private static boolean isUnicodePart(char[] text, int ind) {
    if (isFormatChar(text, ind, ind + 1)) {
      return true;
    }
    if (text[ind] == CharUtils.DWC) {
      return true;
    }
    return Character.UnicodeBlock.of(text[ind]) == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_ARROWS;
  }

  private static boolean isFormatChar(char[] text, int start, int end) {
    if (end - start == 1) {
      int charCode = text[start];
      // From CMap#getFormatCharGlyph
      if (charCode >= 0x200c) {
        //noinspection RedundantIfStatement
        if ((charCode <= 0x200f) ||
          (charCode >= 0x2028 && charCode <= 0x202e) ||
          (charCode >= 0x206a && charCode <= 0x206f)) {
          return true;
        }
      }
    }
    return false;
  }

  private @NotNull java.awt.Color getStyleForeground(@NotNull TextStyle style) {
    java.awt.Color foreground = getEffectiveForeground(style);
    if (style.hasOption(Option.DIM)) {
      java.awt.Color background = getEffectiveBackground(style);
      foreground = new java.awt.Color((foreground.getRed() + background.getRed()) / 2,
                             (foreground.getGreen() + background.getGreen()) / 2,
                             (foreground.getBlue() + background.getBlue()) / 2,
                             foreground.getAlpha());
    }
    return foreground;
  }

  protected @NotNull Font getFontToDisplay(char[] text, int start, int end, @NotNull TextStyle style) {
    boolean bold = style.hasOption(TextStyle.Option.BOLD);
    boolean italic = style.hasOption(TextStyle.Option.ITALIC);
    // workaround to fix Swing bad rendering of bold special chars on Linux
    if (bold && mySettingsProvider.DECCompatibilityMode() && CharacterSets.isDecBoxChar(text[start])) {
      return myNormalFont;
    }
    return bold ? (italic ? myBoldItalicFont : myBoldFont)
            : (italic ? myItalicFont : myNormalFont);
  }

  private ColorPalette getPalette() {
    return mySettingsProvider.getTerminalColorPalette();
  }

  private void drawMargins(Graphics2D gfx, int width, int height) {
    gfx.setColor(getBackground());
    gfx.fillRect(0, height, getWidth(), getHeight() - height);
    gfx.fillRect(width, 0, getWidth() - width, getHeight());
  }

  // Called in a background thread with myTerminalTextBuffer.lock() acquired
  public void scrollArea(final int scrollRegionTop, final int scrollRegionSize, int dy) {
    scrollDy.addAndGet(dy);
    mySelection = null;
  }

  // should be called on EDT
  public void scrollToShowAllOutput() {
    myTerminalTextBuffer.lock();
    try {
      int historyLines = myTerminalTextBuffer.getHistoryLinesCount();
      if (historyLines > 0) {
        int termHeight = myTermSize.getRows();
        myBoundedRangeModel.setRangeProperties(-historyLines, historyLines + termHeight, -historyLines,
            termHeight, false);
        TerminalModelListener modelListener = new TerminalModelListener() {
          @Override
          public void modelChanged() {
            int zeroBasedCursorY = myCursor.myCursorCoordinates.y - 1;
            if (zeroBasedCursorY + historyLines >= termHeight) {
              myTerminalTextBuffer.removeModelListener(this);
              SwingUtilities.invokeLater(() -> {
                myTerminalTextBuffer.lock();
                try {
                  myBoundedRangeModel.setRangeProperties(0, myTermSize.getRows(),
                      -myTerminalTextBuffer.getHistoryLinesCount(), myTermSize.getRows(), false);
                } finally {
                  myTerminalTextBuffer.unlock();
                }
              });
            }
          }
        };
        myTerminalTextBuffer.addModelListener(modelListener);
        myBoundedRangeModel.addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            myBoundedRangeModel.removeChangeListener(this);
            myTerminalTextBuffer.removeModelListener(modelListener);
          }
        });
      }
    } finally {
      myTerminalTextBuffer.unlock();
    }
  }

  private void updateScrolling(boolean forceUpdate) {
    int dy = scrollDy.getAndSet(0);
    boolean historyBufferLineCountChanged = myHistoryBufferLineCountChanged.getAndSet(false);
    if (dy == 0 && !forceUpdate && !historyBufferLineCountChanged) {
      return;
    }
    if (myScrollingEnabled) {
      int value = myBoundedRangeModel.getValue();
      int historyLineCount = myTerminalTextBuffer.getHistoryLinesCount();
      if (value == 0) {
        myBoundedRangeModel
                .setRangeProperties(0, myTermSize.getRows(), -historyLineCount, myTermSize.getRows(), false);
      } else {
        // if scrolled to a specific area, update scroll to keep showing this area
        myBoundedRangeModel.setRangeProperties(
                Math.min(Math.max(value + dy, -historyLineCount), myTermSize.getRows()),
                myTermSize.getRows(),
                -historyLineCount,
                myTermSize.getRows(), false);
      }
    } else {
      myBoundedRangeModel.setRangeProperties(0, myTermSize.getRows(), 0, myTermSize.getRows(), false);
    }
  }

  public void setCursor(final int x, final int y) {
    myCursor.setX(x);
    myCursor.setY(y);
  }

  @Override
  public void setCursorShape(@Nullable CursorShape cursorShape) {
    myCursor.setShape(cursorShape);
  }

  public void beep() {
    if (mySettingsProvider.audibleBell()) {
      Toolkit.getDefaultToolkit().beep();
    }
  }

  public @Nullable Rectangle getBounds(@NotNull TerminalLineIntervalHighlighting highlighting) {
    TerminalLine line = highlighting.getLine();
    int index = myTerminalTextBuffer.findScreenLineIndex(line);
    if (index >= 0 && !highlighting.isDisposed()) {
      return getBounds(new LineCellInterval(index, highlighting.getStartOffset(), highlighting.getEndOffset() + 1));
    }
    return null;
  }

  private @NotNull Rectangle getBounds(@NotNull LineCellInterval cellInterval) {
    java.awt.Point topLeft = new java.awt.Point(cellInterval.getStartColumn() * myCharSize.width + getInsetX(),
      cellInterval.getLine() * myCharSize.height);
    return new Rectangle(topLeft, new java.awt.Dimension(myCharSize.width * cellInterval.getCellCount(), myCharSize.height));
  }

  /**
   * @deprecated use {@link #getVerticalScrollModel()} instead
   */
  @Deprecated
  public BoundedRangeModel getBoundedRangeModel() {
    return myBoundedRangeModel;
  }

  public @NotNull BoundedRangeModel getVerticalScrollModel() {
    return myBoundedRangeModel;
  }

  public TerminalTextBuffer getTerminalTextBuffer() {
    return myTerminalTextBuffer;
  }

  @Override
  public @Nullable TerminalSelection getSelection() {
    return mySelection;
  }

  @Override
  public boolean ambiguousCharsAreDoubleWidth() {
    return mySettingsProvider.ambiguousCharsAreDoubleWidth();
  }

  @Override
  public void setBracketedPasteMode(boolean bracketedPasteModeEnabled) {
    myBracketedPasteMode = bracketedPasteModeEnabled;
  }

  public LinesBuffer getScrollBuffer() {
    return myTerminalTextBuffer.getHistoryBuffer();
  }

  @Override
  public void setCursorVisible(boolean isCursorVisible) {
    myCursor.setShouldDrawCursor(isCursorVisible);
  }

  protected @NotNull JPopupMenu createPopupMenu(@NotNull TerminalActionProvider actionProvider) {
    JPopupMenu popup = new JPopupMenu();
    TerminalAction.fillMenu(popup, actionProvider);
    return popup;
  }

  private @NotNull TerminalActionProvider getTerminalActionProvider(@Nullable LinkInfo linkInfo, @NotNull MouseEvent e) {
    LinkInfoEx.PopupMenuGroupProvider popupMenuGroupProvider = LinkInfoEx.getPopupMenuGroupProvider(linkInfo);
    if (popupMenuGroupProvider != null) {
      return new TerminalActionProvider() {
        @Override
        public List<TerminalAction> getActions() {
          return popupMenuGroupProvider.getPopupMenuGroup(e);
        }

        @Override
        public TerminalActionProvider getNextProvider() {
          return TerminalPanel.this;
        }

        @Override
        public void setNextProvider(TerminalActionProvider provider) {
        }
      };
    }
    return this;
  }

  @Override
  public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
    myScrollingEnabled = !useAlternateScreenBuffer;
    SwingUtilities.invokeLater(() -> {
      updateScrolling(true);
      if (myUsingAlternateBuffer != myTerminalTextBuffer.isUsingAlternateBuffer()) {
        myUsingAlternateBuffer = myTerminalTextBuffer.isUsingAlternateBuffer();
        if (mySettingsProvider.shouldDisableLineSpacingForAlternateScreenBuffer()) {
          Timer timer = new Timer(10, e -> {});
          timer.addActionListener(e -> {
            reinitFontAndResize();
            timer.stop();
          });
          timer.start();
        }
      }
    });
  }

  public TerminalOutputStream getTerminalOutputStream() {
    return myTerminalStarter;
  }

  @Override
  public void setWindowTitle(@NotNull String windowTitle) {
    myWindowTitle = windowTitle;
  }

  @Override
  public List<TerminalAction> getActions() {
    return List.of(
            new TerminalAction(mySettingsProvider.getOpenUrlActionPresentation(), input -> {
              return openSelectionAsURL();
            }).withEnabledSupplier(this::selectionTextIsUrl),
            new TerminalAction(mySettingsProvider.getCopyActionPresentation(), this::handleCopy) {
              @Override
              public boolean isEnabled(@Nullable KeyEvent e) {
                return e != null || mySelection != null;
              }
            }.withMnemonicKey(KeyEvent.VK_C),
            new TerminalAction(mySettingsProvider.getPasteActionPresentation(), input -> {
              handlePaste();
              return true;
            }).withMnemonicKey(KeyEvent.VK_P).withEnabledSupplier(() -> getClipboardString() != null),
            new TerminalAction(mySettingsProvider.getSelectAllActionPresentation(), input -> {
              selectAll();
              return true;
            }),
            new TerminalAction(mySettingsProvider.getClearBufferActionPresentation(), input -> {
              clearBuffer();
              return true;
            }).withMnemonicKey(KeyEvent.VK_K).withEnabledSupplier(() -> !myTerminalTextBuffer.isUsingAlternateBuffer()).separatorBefore(true),
            new TerminalAction(mySettingsProvider.getPageUpActionPresentation(), input -> {
              pageUp();
              return true;
            }).withEnabledSupplier(() -> !myTerminalTextBuffer.isUsingAlternateBuffer()).separatorBefore(true),
            new TerminalAction(mySettingsProvider.getPageDownActionPresentation(), input -> {
              pageDown();
              return true;
            }).withEnabledSupplier(() -> !myTerminalTextBuffer.isUsingAlternateBuffer()),
            new TerminalAction(mySettingsProvider.getLineUpActionPresentation(), input -> {
              scrollUp();
              return true;
            }).withEnabledSupplier(() -> !myTerminalTextBuffer.isUsingAlternateBuffer()).separatorBefore(true),
            new TerminalAction(mySettingsProvider.getLineDownActionPresentation(), input -> {
              scrollDown();
              return true;
            }));
  }

  public void selectAll() {
    mySelection = new TerminalSelection(new Point(0, -myTerminalTextBuffer.getHistoryLinesCount()),
      new Point(myTermSize.getColumns(), myTerminalTextBuffer.getScreenLinesCount()));
  }

  @NotNull
  private Boolean selectionTextIsUrl() {
    String selectionText = getSelectionText();
    if (selectionText != null) {
      try {
        URI uri = new URI(selectionText);
        //noinspection ResultOfMethodCallIgnored
        uri.toURL();
        return true;
      } catch (Exception e) {
        //pass
      }
    }
    return false;
  }

  @Nullable
  private String getSelectionText() {
    if (mySelection != null) {
      Pair<Point, Point> points = mySelection.pointsForRun(myTermSize.getColumns());

      if (points.getFirst() != null || points.getSecond() != null) {
        return SelectionUtil
                .getSelectionText(points.getFirst(), points.getSecond(), myTerminalTextBuffer);

      }
    }

    return null;
  }

  protected boolean openSelectionAsURL() {
    if (Desktop.isDesktopSupported()) {
      try {
        String selectionText = getSelectionText();

        if (selectionText != null) {
          Desktop.getDesktop().browse(new URI(selectionText));
        }
      } catch (Exception e) {
        //ok then
      }
    }
    return false;
  }

  public void clearBuffer() {
    clearBuffer(true);
  }

  /**
   * @param keepLastLine true to keep last line (e.g. to keep terminal prompt)
   *                     false to clear entire terminal panel (relevant for terminal console)
   */
  protected void clearBuffer(boolean keepLastLine) {
    if (!myTerminalTextBuffer.isUsingAlternateBuffer()) {
      myTerminalTextBuffer.clearHistory();

      if (myCoordsAccessor != null) {
        if (keepLastLine) {
          if (myCoordsAccessor.getY() > 0) {
            TerminalLine lastLine = myTerminalTextBuffer.getLine(myCoordsAccessor.getY() - 1);
            myTerminalTextBuffer.clearScreenBuffer();
            myCoordsAccessor.setY(0);
            myCursor.setY(1);
            myTerminalTextBuffer.addLine(lastLine);
          }
        }
        else {
          myTerminalTextBuffer.clearScreenBuffer();
          myCoordsAccessor.setX(0);
          myCoordsAccessor.setY(1);
          myCursor.setX(0);
          myCursor.setY(1);
        }
      }

      myBoundedRangeModel.setValue(0);
      updateScrolling(true);

      myClientScrollOrigin = myBoundedRangeModel.getValue();
    }
  }

  @Override
  public TerminalActionProvider getNextProvider() {
    return myNextActionProvider;
  }

  @Override
  public void setNextProvider(TerminalActionProvider provider) {
    myNextActionProvider = provider;
  }

  private static final byte ASCII_NUL = 0;
  private static final byte ASCII_ESC = 27;

  private boolean processTerminalKeyPressed(KeyEvent e) {
    if (hasUncommittedChars()) {
      return false;
    }

    try {
      final int keycode = e.getKeyCode();
      final char keychar = e.getKeyChar();

      // numLock does not change the code sent by keypad VK_DELETE
      // although it send the char '.'
      if (keycode == KeyEvent.VK_DELETE && keychar == '.') {
        myTerminalStarter.sendBytes(new byte[]{'.'}, true);
        return true;
      }
      // CTRL + Space is not handled in KeyEvent; handle it manually
      if (keychar == ' ' && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
        myTerminalStarter.sendBytes(new byte[]{ASCII_NUL}, true);
        return true;
      }

      final byte[] code = myTerminalStarter.getTerminal().getCodeForKey(keycode, e.getModifiers());
      if (code != null) {
        myTerminalStarter.sendBytes(code, true);
        if (mySettingsProvider.scrollToBottomOnTyping() && isCodeThatScrolls(keycode)) {
          scrollToBottom();
        }
        return true;
      }
      if (isAltPressedOnly(e) && Character.isDefined(keychar) && mySettingsProvider.altSendsEscape()) {
        // Cannot use e.getKeyChar() on macOS:
        //  Option+f produces e.getKeyChar()='ƒ' (402), but 'f' (102) is needed.
        //  Option+b produces e.getKeyChar()='∫' (8747), but 'b' (98) is needed.
        myTerminalStarter.sendString(new String(new char[]{ASCII_ESC, simpleMapKeyCodeToChar(e)}), true);
        return true;
      }
      if (Character.isISOControl(keychar)) { // keys filtered out here will be processed in processTerminalKeyTyped
        return processCharacter(e);
      }
    }
    catch (Exception ex) {
      LOG.error("Error sending pressed key to emulator", ex);
    }
    return false;
  }

  private static char simpleMapKeyCodeToChar(@NotNull KeyEvent e) {
    // zsh requires proper case of letter
    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
      return Character.toUpperCase((char) e.getKeyCode());
    }
    return Character.toLowerCase((char) e.getKeyCode());
  }

  private static boolean isAltPressedOnly(@NotNull KeyEvent e) {
    int modifiersEx = e.getModifiersEx();
    return (modifiersEx & InputEvent.ALT_DOWN_MASK) != 0 &&
            (modifiersEx & InputEvent.ALT_GRAPH_DOWN_MASK) == 0 &&
            (modifiersEx & InputEvent.CTRL_DOWN_MASK) == 0 &&
            (modifiersEx & InputEvent.SHIFT_DOWN_MASK) == 0;
  }

  private boolean processCharacter(@NotNull KeyEvent e) {
    if (isAltPressedOnly(e) && mySettingsProvider.altSendsEscape()) {
      return false;
    }
    char keyChar = e.getKeyChar();
    final char[] obuffer;
    obuffer = new char[]{keyChar};

    if (keyChar == '`' && (e.getModifiersEx() & InputEvent.META_DOWN_MASK) != 0) {
      // Command + backtick is a short-cut on Mac OSX, so we shouldn't type anything
      return false;
    }

    myTerminalStarter.sendString(new String(obuffer), true);

    if (mySettingsProvider.scrollToBottomOnTyping()) {
      scrollToBottom();
    }
    return true;
  }

  private static boolean isCodeThatScrolls(int keycode) {
    return keycode == KeyEvent.VK_UP
            || keycode == KeyEvent.VK_DOWN
            || keycode == KeyEvent.VK_LEFT
            || keycode == KeyEvent.VK_RIGHT
            || keycode == KeyEvent.VK_BACK_SPACE
            || keycode == KeyEvent.VK_INSERT
            || keycode == KeyEvent.VK_DELETE
            || keycode == KeyEvent.VK_ENTER
            || keycode == KeyEvent.VK_HOME
            || keycode == KeyEvent.VK_END
            || keycode == KeyEvent.VK_PAGE_UP
            || keycode == KeyEvent.VK_PAGE_DOWN;
  }

  private boolean processTerminalKeyTyped(KeyEvent e) {
    if (hasUncommittedChars()) {
      return false;
    }

    if (!Character.isISOControl(e.getKeyChar())) { // keys filtered out here will be processed in processTerminalKeyPressed
      try {
        return processCharacter(e);
      }
      catch (Exception ex) {
        LOG.error("Error sending typed key to emulator", ex);
      }
    }
    return false;
  }

  private class TerminalKeyHandler extends KeyAdapter {

    private boolean myIgnoreNextKeyTypedEvent;

    public TerminalKeyHandler() {
    }

    public void keyPressed(KeyEvent e) {
      if (e.isConsumed()) {
        return;
      }
      myIgnoreNextKeyTypedEvent = false;
      if (TerminalAction.processEvent(TerminalPanel.this, e) || processTerminalKeyPressed(e)) {
        e.consume();
        myIgnoreNextKeyTypedEvent = true;
      }
    }

    public void keyTyped(KeyEvent e) {
      if (e.isConsumed()) {
        return;
      }
      if (myIgnoreNextKeyTypedEvent || processTerminalKeyTyped(e)) {
        e.consume();
      }
    }
  }

  private void handlePaste() {
    pasteFromClipboard(false);
  }

  private void handlePasteSelection() {
    pasteFromClipboard(true);
  }

  /**
   * Copies selected text to clipboard.
   * @param unselect true to unselect currently selected text
   * @param useSystemSelectionClipboardIfAvailable true to use {@link Toolkit#getSystemSelection()} if available
   */
  private void handleCopy(boolean unselect, boolean useSystemSelectionClipboardIfAvailable) {
    if (mySelection != null) {
      Pair<Point, Point> points = mySelection.pointsForRun(myTermSize.getColumns());
      copySelection(points.getFirst(), points.getSecond(), useSystemSelectionClipboardIfAvailable);
      if (unselect) {
        mySelection = null;
        repaint();
      }
    }
  }

  private boolean handleCopy(@Nullable KeyEvent e) {
    boolean ctrlC = e != null && e.getKeyCode() == KeyEvent.VK_C && e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK;
    boolean sendCtrlC = ctrlC && mySelection == null;
    handleCopy(ctrlC, false);
    return !sendCtrlC;
  }

  private void handleCopyOnSelect() {
    handleCopy(false, true);
  }

  /**
   * InputMethod implementation
   * For details read http://docs.oracle.com/javase/7/docs/technotes/guides/imf/api-tutorial.html
   */
  @Override
  protected void processInputMethodEvent(InputMethodEvent e) {
    int commitCount = e.getCommittedCharacterCount();

    if (commitCount > 0) {
      myInputMethodUncommittedChars = null;
      AttributedCharacterIterator text = e.getText();
      if (text != null) {
        StringBuilder sb = new StringBuilder();

        //noinspection ForLoopThatDoesntUseLoopVariable
        for (char c = text.first(); commitCount > 0; c = text.next(), commitCount--) {
          if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
            sb.append(c);
          }
        }

        if (sb.length() > 0) {
          myTerminalStarter.sendString(sb.toString(), true);
        }
      }
    } else {
      myInputMethodUncommittedChars = uncommittedChars(e.getText());
    }
  }

  private static String uncommittedChars(@Nullable AttributedCharacterIterator text) {
    if (text == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder();

    for (char c = text.first(); c != CharacterIterator.DONE; c = text.next()) {
      if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
        sb.append(c);
      }
    }

    return sb.toString();
  }

  @Override
  public InputMethodRequests getInputMethodRequests() {
    return new MyInputMethodRequests();
  }

  private class MyInputMethodRequests implements InputMethodRequests {
    @Override
    public Rectangle getTextLocation(TextHitInfo offset) {
      Rectangle r = new Rectangle(myCursor.getCoordX() * myCharSize.width + getInsetX(), (myCursor.getCoordY() + 1) * myCharSize.height,
              0, 0);
      java.awt.Point p = TerminalPanel.this.getLocationOnScreen();
      r.translate(p.x, p.y);
      return r;
    }

    @Nullable
    @Override
    public TextHitInfo getLocationOffset(int x, int y) {
      return null;
    }

    @Override
    public int getInsertPositionOffset() {
      return 0;
    }

    @Override
    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Override
    public int getCommittedTextLength() {
      return 0;
    }

    @Nullable
    @Override
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Nullable
    @Override
    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

  }

  public void dispose() {
    myRepaintTimer.stop();
  }
}
