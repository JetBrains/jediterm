package com.jediterm.terminal.ui;

import com.google.common.base.Ascii;
import com.jediterm.terminal.*;
import com.jediterm.terminal.display.*;
import com.jediterm.terminal.emulator.mouse.TerminalMouseListener;
import com.jediterm.terminal.util.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class TerminalPanel extends JComponent implements TerminalDisplay, ClipboardOwner, StyledTextConsumer {
  private static final Logger LOG = Logger.getLogger(TerminalPanel.class);
  private static final long serialVersionUID = -1048763516632093014L;
  private static final double FPS = 50;

  public static final double SCROLL_SPEED = 0.05;

  private BufferedImage myImage;

  private BufferedImage myImageForSelection;

  protected Graphics2D myGfx;

  private Graphics2D myGfxForSelection;

  private final Component myTerminalPanel = this;

  private Font myNormalFont;

  private Font myBoldFont;

  private int myDescent = 0;

  private float myLineSpace = 0;

  protected Dimension myCharSize = new Dimension();

  protected Dimension myTermSize = new Dimension(80, 24);

  private boolean myAntialiasing = true;

  private TerminalStarter myTerminalStarter = null;

  private TerminalSelection mySelection = null;

  private TextStyle mySelectionColor = new TextStyle(Color.WHITE, new Color(82, 109, 165));

  private Clipboard myClipboard;

  private TerminalPanelListener myTerminalPanelListener;

  private SystemSettingsProvider mySettingsProvider;
  final private BackBuffer myBackBuffer;

  final private StyleState myStyleState;

  final private TerminalCursor myCursor = new TerminalCursor();

  private final BoundedRangeModel myBoundedRangeModel = new DefaultBoundedRangeModel(0, 80, 0, 80);

  protected int myClientScrollOrigin;
  protected int newClientScrollOrigin;
  private KeyListener myKeyListener;
  private long myLastCursorChange;
  private boolean myCursorIsShown;
  private long myLastResize;

  private boolean myScrollingEnabled = true;
  private String myWindowTitle = "Terminal";

  private static final int SCALE = UIUtil.isRetina() ? 2 : 1;

  public TerminalPanel(@NotNull SystemSettingsProvider settingsProvider, @NotNull BackBuffer backBuffer, @NotNull StyleState styleState) {
    mySettingsProvider = settingsProvider;
    myBackBuffer = backBuffer;
    myStyleState = styleState;
    myTermSize.width = backBuffer.getWidth();
    myTermSize.height = backBuffer.getHeight();

    updateScrolling();
  }

  public void init() {
    myNormalFont = createFont();
    myBoldFont = myNormalFont.deriveFont(Font.BOLD);

    establishFontMetrics();

    setupImages();
    setUpClipboard();

    setPreferredSize(new Dimension(getPixelWidth(), getPixelHeight()));

    setFocusable(true);
    enableInputMethods(true);

    setFocusTraversalKeysEnabled(false);

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(final MouseEvent e) {
        final Point charCoords = panelToCharCoords(e.getPoint());

        if (mySelection == null) {
          mySelection = new TerminalSelection(new Point(charCoords));
        }
        repaint();
        mySelection.updateEnd(charCoords, myTermSize.width);

        if (e.getPoint().y < 0) {
          moveScrollBar((int)((e.getPoint().y) * SCROLL_SPEED));
        }
        if (e.getPoint().y > getPixelHeight()) {
          moveScrollBar((int)((e.getPoint().y - getPixelHeight()) * SCROLL_SPEED));
        }
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        moveScrollBar(notches);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          if (e.getClickCount() == 1) {
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
        if (e.getButton() == MouseEvent.BUTTON1) {
          int count = e.getClickCount();
          if (count == 1) {
            // do nothing
          }
          else if (count == 2) {
            // select word
            final Point charCoords = panelToCharCoords(e.getPoint());
            Point start = SelectionUtil.getPreviousSeparator(charCoords, myBackBuffer);
            Point stop = SelectionUtil.getNextSeparator(charCoords, myBackBuffer);
            mySelection = new TerminalSelection(start);
            mySelection.updateEnd(stop, myTermSize.width);
          }
          else if (count == 3) {
            // select line
            final Point charCoords = panelToCharCoords(e.getPoint());
            mySelection = new TerminalSelection(new Point(0, charCoords.y));
            mySelection.updateEnd(new Point(myTermSize.width, charCoords.y), myTermSize.width);
          }
        }
        else if (e.getButton() == MouseEvent.BUTTON3) {
          JPopupMenu popup = createPopupMenu(mySelection, getClipboardString());
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
        repaint();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        myLastResize = System.currentTimeMillis();
        sizeTerminalFromComponent();
      }
    });

    myBoundedRangeModel.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        newClientScrollOrigin = myBoundedRangeModel.getValue();
      }
    });

    Timer redrawTimer = new Timer((int)(1000 / FPS), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        redraw();
      }
    });
    setDoubleBuffered(true);
    redrawTimer.start();
    repaint();
  }

  private void moveScrollBar(int k) {
    myBoundedRangeModel.setValue(myBoundedRangeModel.getValue() + k);
  }

  protected Font createFont() {
    return Font.decode("Monospaced-14");
  }

  private Point panelToCharCoords(final Point p) {
    int x = Math.min(p.x / myCharSize.width, getColumnCount() - 1);
    int y = Math.min(p.y / myCharSize.height, getRowCount() - 1)  + myClientScrollOrigin;
    return new Point(x, y);
  }

  void setUpClipboard() {
    myClipboard = Toolkit.getDefaultToolkit().getSystemSelection();
    if (myClipboard == null) {
      myClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
  }

  protected void copySelection(final Point selectionStart, final Point selectionEnd) {
    if (selectionStart == null || selectionEnd == null) {
      return;
    }

    final String selectionText = SelectionUtil
      .getSelectionText(selectionStart, selectionEnd, myBackBuffer);

    if (selectionText.length() != 0) {
      try {
        setCopyContents(new StringSelection(selectionText));
      }
      catch (final IllegalStateException e) {
        LOG.error("Could not set clipboard:", e);
      }
    }
  }

  protected void setCopyContents(StringSelection selection) {
    myClipboard.setContents(selection, this);
  }

  protected void pasteSelection() {
    final String selection = getClipboardString();

    try {
      myTerminalStarter.sendString(selection);
    }
    catch (RuntimeException e) {
      LOG.info(e);
    }
  }

  private String getClipboardString() {
    try {
      return getClipboardContent();
    }
    catch (final Exception e) {
      LOG.info(e);
    }
    return null;
  }

  protected String getClipboardContent() throws IOException, UnsupportedFlavorException {
    try {
      return (String)myClipboard.getData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      LOG.info(e);
      return null;
    }
  }

  /* Do not care
   */
  public void lostOwnership(final Clipboard clipboard, final Transferable contents) {
  }

  private void setupImages() {
    final BufferedImage oldImage = myImage;
    int width = getPixelWidth();
    int height = getPixelHeight();
    if (width > 0 && height > 0) {
      Pair<BufferedImage, Graphics2D> imageAndGfx = createAndInitImage(width, height);
      myImage = imageAndGfx.first;
      myGfx = imageAndGfx.second;

      imageAndGfx = createAndInitImage(width, height);
      myImageForSelection = imageAndGfx.first;
      myGfxForSelection = imageAndGfx.second;

      if (oldImage != null) {
        myGfx.drawImage(oldImage, 0, 0,
                        oldImage.getWidth(), oldImage.getHeight(), myTerminalPanel);
      }
    }
  }

  private Pair<BufferedImage, Graphics2D> createAndInitImage(int width, int height) {
    BufferedImage image = createBufferedImage(width, height);

    Graphics2D gfx = image.createGraphics();

    setupAntialiasing(gfx, myAntialiasing);

    gfx.setColor(getBackground());

    gfx.fillRect(0, 0, width, height);

    return Pair.create(image, gfx);
  }

  protected BufferedImage createBufferedImage(int width, int height) {
    return new BufferedImage(width, height,
                             BufferedImage.TYPE_INT_RGB);
  }

  private void sizeTerminalFromComponent() {
    if (myTerminalStarter != null) {
      final int newWidth = getWidth() / myCharSize.width;
      final int newHeight = getHeight() / myCharSize.height;

      if (newHeight > 0 && newWidth > 0) {
        final Dimension newSize = new Dimension(newWidth, newHeight);

        myTerminalStarter.postResize(newSize, RequestOrigin.User);
      }
    }
  }

  public void setTerminalStarter(final TerminalStarter terminalStarter) {
    myTerminalStarter = terminalStarter;
    sizeTerminalFromComponent();
  }

  public void setKeyListener(final KeyListener keyListener) {
    this.myKeyListener = keyListener;
  }

  public Dimension requestResize(final Dimension newSize,
                                 final RequestOrigin origin,
                                 int cursorY,
                                 JediTerminal.ResizeHandler resizeHandler) {
    if (!newSize.equals(myTermSize)) {
      myBackBuffer.lock();
      try {
        myBackBuffer.resize(newSize, origin, cursorY, resizeHandler);
        myTermSize = (Dimension)newSize.clone();
        // resize images..
        setupImages();

        final Dimension pixelDimension = new Dimension(getPixelWidth(), getPixelHeight());

        setPreferredSize(pixelDimension);
        if (myTerminalPanelListener != null) myTerminalPanelListener.onPanelResize(pixelDimension, origin);
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            updateScrolling();
          }
        });
      }
      finally {
        myBackBuffer.unlock();
      }
    }

    return new Dimension(getPixelWidth(), getPixelHeight());
  }

  public void setTerminalPanelListener(final TerminalPanelListener resizeDelegate) {
    myTerminalPanelListener = resizeDelegate;
  }

  private void establishFontMetrics() {
    final BufferedImage img = createBufferedImage(1, 1);
    final Graphics2D graphics = img.createGraphics();
    graphics.setFont(myNormalFont);

    final FontMetrics fo = graphics.getFontMetrics();
    myDescent = fo.getDescent();
    myCharSize.width = fo.charWidth('@');
    myCharSize.height = fo.getHeight() + (int)(myLineSpace * 2);
    myDescent += myLineSpace;

    img.flush();
    graphics.dispose();
  }

  protected void setupAntialiasing(Graphics graphics, boolean antialiasing) {
    myAntialiasing = antialiasing;
    if (graphics instanceof Graphics2D) {
      Graphics2D myGfx = (Graphics2D)graphics;
      final Object mode = antialiasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                                       : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
      final RenderingHints hints = new RenderingHints(
        RenderingHints.KEY_TEXT_ANTIALIASING, mode);
      myGfx.setRenderingHints(hints);
    }
  }

  @Override
  public Color getBackground() {
    return myStyleState.getCurrent().getDefaultBackground();
  }

  @Override
  public Color getForeground() {
    return myStyleState.getCurrent().getDefaultForeground();
  }

  @Override
  public void paintComponent(final Graphics g) {
    Graphics2D gfx = (Graphics2D)g;
    if (myImage != null) {
      gfx.drawImage(myImage, 0, 0, myTerminalPanel);
      drawMargins(gfx, myImage.getWidth(), myImage.getHeight());
      drawSelection(myImageForSelection, gfx);
      myCursor.drawCursor(gfx);
    }
  }

  @Override
  public void processKeyEvent(final KeyEvent e) {
    final int id = e.getID();
    if (id == KeyEvent.KEY_PRESSED) {
      myKeyListener.keyPressed(e);
    }
    else if (id == KeyEvent.KEY_RELEASED) {
                        /* keyReleased(e); */
    }
    else if (id == KeyEvent.KEY_TYPED) {
      myKeyListener.keyTyped(e);
    }
    e.consume();
  }

  public int getPixelWidth() {
    return myCharSize.width * myTermSize.width;
  }

  public int getPixelHeight() {
    return myCharSize.height * myTermSize.height;
  }

  public int getColumnCount() {
    return myTermSize.width;
  }

  public int getRowCount() {
    return myTermSize.height;
  }

  public String getWindowTitle() {
    return myWindowTitle;
  }

  public void addTerminalMouseListener(final TerminalMouseListener listener) {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        Point p = panelToCharCoords(e.getPoint());
        listener.mousePressed(p.x, p.y, e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point p = panelToCharCoords(e.getPoint());
        listener.mouseReleased(p.x, p.y, e);
      }
    });
  }

  protected void initKeyHandler() {
    setKeyListener(new TerminalKeyHandler(myTerminalStarter, mySettingsProvider));
  }

  public class TerminalCursor {
    private static final long CURSOR_BLINK_PERIOD = 505;

    private boolean myCursorHasChanged;

    protected Point myCursorCoordinates = new Point();

    private boolean myShouldDrawCursor = true;
    private boolean myBlinking = true;

    private boolean calculateIsCursorShown(long currentTime) {
      if (myCursorHasChanged) {
        return true;
      }
      if (cursorShouldChangeBlinkState(currentTime)) {
        return !myCursorIsShown;
      }
      else {
        return myCursorIsShown;
      }
    }

    private boolean cursorShouldChangeBlinkState(long currentTime) {
      return myBlinking && (currentTime - myLastCursorChange > CURSOR_BLINK_PERIOD);
    }

    public void drawCursor(Graphics2D g) {
      if (needsRepaint()) {
        final int y = getCoordY();

        if (y >= 0 && y < myTermSize.height) {
          TextStyle current = myStyleState.getCurrent();

          boolean isCursorShown = calculateIsCursorShown(System.currentTimeMillis());

          g.setXORMode(current.getBackground());
          if (isCursorShown) {
            g.setColor(current.getForeground());
          }
          else {
            g.setColor(current.getBackground());
          }

          g.fillRect(myCursorCoordinates.x * myCharSize.width * SCALE, y * myCharSize.height * SCALE,
                     myCharSize.width * SCALE, myCharSize.height * SCALE);

          myCursorIsShown = isCursorShown;
          myLastCursorChange = System.currentTimeMillis();

          myCursorHasChanged = false;
        }
      }
    }

    public boolean needsRepaint() {
      long currentTime = System.currentTimeMillis();
      return isShouldDrawCursor() &&
             isFocusOwner() &&
             noRecentResize(currentTime) &&
             (myCursorHasChanged || cursorShouldChangeBlinkState(currentTime));
    }

    public void setX(int x) {
      myCursorCoordinates.x = x;
      myCursorHasChanged = true;
    }

    public void setY(int y) {
      myCursorCoordinates.y = y;
      myCursorHasChanged = true;
    }

    public int getCoordX() {
      return myCursorCoordinates.x;
    }

    public int getCoordY() {
      return myCursorCoordinates.y - 1 - myClientScrollOrigin;
    }

    public void setShouldDrawCursor(boolean shouldDrawCursor) {
      myShouldDrawCursor = shouldDrawCursor;
    }

    public boolean isShouldDrawCursor() {
      return myShouldDrawCursor;
    }

    private boolean noRecentResize(long time) {
      return time - myLastResize > CURSOR_BLINK_PERIOD;
    }

    public void setBlinking(boolean blinking) {
      myBlinking = blinking;
    }

    public boolean isBlinking() {
      return myBlinking;
    }
  }

  public void drawSelection(BufferedImage imageForSelection, Graphics2D g) {
    /* which is the top one */
    Point top;
    Point bottom;

    if (mySelection == null) {
      return;
    }

    Point start = mySelection.getStart();
    Point end = mySelection.getEnd();

    if (start.y == end.y) {
                        /* same line */
      if (start.x == end.x) {
        return;
      }
      top = start.x < end.x ? start : end;
      bottom = start.x >= end.x ? start : end;

      copyImage(g, imageForSelection, top.x * myCharSize.width, (top.y - myClientScrollOrigin) * myCharSize.height,
                (bottom.x - top.x) * myCharSize.width, myCharSize.height);
    }
    else {
      top = start.y < end.y ? start : end;
      bottom = start.y > end.y ? start : end;
                        /* to end of first line */
      copyImage(g, imageForSelection, top.x * myCharSize.width, (top.y - myClientScrollOrigin) * myCharSize.height,
                (myTermSize.width - top.x) * myCharSize.width, myCharSize.height);

      if (bottom.y - top.y > 1) {
        /* intermediate lines */
        copyImage(g, imageForSelection, 0, (top.y + 1 - myClientScrollOrigin) * myCharSize.height,
                  myTermSize.width * myCharSize.width, (bottom.y - top.y - 1)
                                                       * myCharSize.height);
      }

      /* from beginning of last line */

      copyImage(g, imageForSelection, 0, (bottom.y - myClientScrollOrigin) * myCharSize.height, bottom.x
                                                                                                * myCharSize.width, myCharSize.height);
    }
  }

  private void copyImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
    g.drawImage(image, x, y, x + width, y + height, x, y, x + width, y + height, null);
  }


  @Override
  public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer buf, int startRow) {
    if (myGfx != null) {
      drawCharacters(x, y, style, buf, myGfx);
    }
    if (myGfxForSelection != null) {
      TextStyle selectionStyle = style.clone();
      selectionStyle.setBackground(mySelectionColor.getBackground());
      selectionStyle.setForeground(mySelectionColor.getForeground());

      drawCharacters(x, y, selectionStyle, buf, myGfxForSelection);
    }
  }

  private void drawCharacters(int x, int y, TextStyle style, CharBuffer buf, Graphics2D gfx) {
    gfx.setColor(myStyleState.getBackground(style.getBackgroundForRun()));
    gfx
      .fillRect(x * myCharSize.width, (y - myClientScrollOrigin) * myCharSize.height, buf.getLength() * myCharSize.width,
                myCharSize.height);

    gfx.setFont(style.hasOption(TextStyle.Option.BOLD) ? myBoldFont : myNormalFont);
    gfx.setColor(myStyleState.getForeground(style.getForegroundForRun()));

    int baseLine = (y + 1 - myClientScrollOrigin) * myCharSize.height - myDescent;


    gfx.drawChars(buf.getBuf(), buf.getStart(), buf.getLength(), x * myCharSize.width, baseLine);

    if (style.hasOption(TextStyle.Option.UNDERLINED)) {
      gfx.drawLine(x * myCharSize.width, baseLine + 1, (x + buf.getLength()) * myCharSize.width, baseLine + 1);
    }
  }

  private void clientScrollOriginChanged(int oldOrigin) {
    int dy = myClientScrollOrigin - oldOrigin;

    int dyPix = dy * myCharSize.height;

    copyAndClearAreaOnScroll(dy, dyPix, myGfx);
    copyAndClearAreaOnScroll(dy, dyPix, myGfxForSelection);

    if (dy < 0) {
      // Scrolling up; Copied down
      // New area at the top to be filled in - can only be from scroll buffer
      //

      myBackBuffer.getScrollBuffer().processLines(myClientScrollOrigin, -dy, this);
    }
    else {
      // Scrolling down; Copied up
      // New area at the bottom to be filled - can be from both

      int oldEnd = oldOrigin + myTermSize.height;

      // Either its the whole amount above the back buffer + some more
      // Or its the whole amount we moved
      // Or we are already out of the scroll buffer
      int portionInScroll = oldEnd < 0 ? Math.min(-oldEnd, dy) : 0;

      int portionInBackBuffer = dy - portionInScroll;

      if (portionInScroll > 0) {
        myBackBuffer.getScrollBuffer().processLines(oldEnd, portionInScroll, this);
      }

      if (portionInBackBuffer > 0) {
        myBackBuffer.processBufferRows(oldEnd + portionInScroll, portionInBackBuffer, this);
      }
    }
  }

  private void copyAndClearAreaOnScroll(int dy, int dyPix, Graphics2D gfx) {
    gfx.copyArea(0, Math.max(0, dyPix),
                 getPixelWidth(), getPixelHeight() - Math.abs(dyPix),
                 0, -dyPix);
    //clear rect before drawing scroll buffer on it
    gfx.setColor(getBackground());
    if (dy < 0) {
      gfx.fillRect(0, 0, getPixelWidth(), Math.abs(dyPix));
    }
    else {
      gfx.fillRect(0, getPixelHeight() - dyPix, getPixelWidth(), dyPix);
    }
  }

  int myNoDamage = 0;
  int myFramesSkipped = 0;

  public void redraw() {
    if (tryRedrawDamagedPartFromBuffer() || myCursor.needsRepaint()) {
      repaint();
    }
  }

  /**
   * This method tries to get a lock for back buffer. If it fails it increments skippedFrames counter and tries next time.
   * After 5 attempts it locks buffer anyway.
   *
   * @return true if was successfully redrawn and there is anything to repaint
   */
  private boolean tryRedrawDamagedPartFromBuffer() {
    final int newOrigin = newClientScrollOrigin;
    if (!myBackBuffer.tryLock()) {
      if (myFramesSkipped >= 5) {
        myBackBuffer.lock();
      }
      else {
        myFramesSkipped++;
        return false;
      }
    }
    try {
      myFramesSkipped = 0;

      boolean serverScroll = pendingScrolls.enact(myGfx, getPixelWidth(), myCharSize.height);

      boolean clientScroll = myClientScrollOrigin != newOrigin;
      if (clientScroll) {
        final int oldOrigin = myClientScrollOrigin;
        myClientScrollOrigin = newOrigin;
        clientScrollOriginChanged(oldOrigin);
      }

      boolean hasDamage = myBackBuffer.hasDamage();
      if (hasDamage) {
        myNoDamage = 0;

        myBackBuffer.processDamagedCells(this);
        myBackBuffer.resetDamage();
      }
      else {
        myNoDamage++;
      }

      return serverScroll || clientScroll || hasDamage;
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  private void drawMargins(Graphics2D gfx, int width, int height) {
    gfx.setColor(getBackground());
    gfx.fillRect(0, height, getWidth(), getHeight() - height);
    gfx.fillRect(width, 0, getWidth() - width, getHeight());
  }

  public void scrollArea(final int scrollRegionTop, final int scrollRegionSize, int dy) {
    if (dy < 0) {
      //Moving lines off the top of the screen
      //TODO: Something to do with application keypad mode
      //TODO: Something to do with the scroll margins

      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          updateScrolling();
        }
      });
    }
    mySelection = null;
    pendingScrolls.add(scrollRegionTop, scrollRegionSize, dy);
  }

  private void updateScrolling() {
    if (myScrollingEnabled) {
      myBoundedRangeModel
        .setRangeProperties(0, myTermSize.height, -myBackBuffer.getScrollBuffer().getLineCount(), myTermSize.height, false);
    }
    else {
      myBoundedRangeModel.setRangeProperties(0, myTermSize.height, 0, myTermSize.height, false);
    }
  }

  private static class PendingScrolls {
    int[] ys = new int[10];
    int[] hs = new int[10];
    int[] dys = new int[10];
    int scrollCount = -1;

    void ensureArrays(int index) {
      int curLen = ys.length;
      if (index >= curLen) {
        ys = Util.copyOf(ys, curLen * 2);
        hs = Util.copyOf(hs, curLen * 2);
        dys = Util.copyOf(dys, curLen * 2);
      }
    }

    void add(int y, int h, int dy) {
      if (dy == 0) return;
      if (scrollCount >= 0 &&
          y == ys[scrollCount] &&
          h == hs[scrollCount]) {
        dys[scrollCount] += dy;
      }
      else {
        scrollCount++;
        ensureArrays(scrollCount);
        ys[scrollCount] = y;
        hs[scrollCount] = h;
        dys[scrollCount] = dy;
      }
    }

    boolean enact(Graphics2D gfx, int width, int charHeight) {
      if (scrollCount < 0) return false;
      for (int i = 0; i <= scrollCount; i++) {
        gfx.copyArea(0, ys[i] * charHeight, width, hs[i] * charHeight, 0, dys[i] * charHeight);
      }
      scrollCount = -1;
      return true;
    }
  }

  final PendingScrolls pendingScrolls = new PendingScrolls();

  public void setCursor(final int x, final int y) {
    myCursor.setX(x);
    myCursor.setY(y);
  }

  public void beep() {
    Toolkit.getDefaultToolkit().beep();
  }

  public void setLineSpace(final float foo) {
    myLineSpace = foo;
  }

  public BoundedRangeModel getBoundedRangeModel() {
    return myBoundedRangeModel;
  }

  public BackBuffer getBackBuffer() {
    return myBackBuffer;
  }

  public LinesBuffer getScrollBuffer() {
    return myBackBuffer.getScrollBuffer();
  }

  public void lock() {
    myBackBuffer.lock();
  }

  public void unlock() {
    myBackBuffer.unlock();
  }

  @Override
  public void setCursorVisible(boolean shouldDrawCursor) {
    myCursor.setShouldDrawCursor(shouldDrawCursor);
  }

  protected JPopupMenu createPopupMenu(final TerminalSelection selection, String content) {
    JPopupMenu popup = new JPopupMenu();

    JMenuItem newSession = new JMenuItem(mySettingsProvider.getNewSessionAction());

    if (mySettingsProvider.getNewSessionKeyStrokes().length > 0) {
      newSession.setAccelerator(mySettingsProvider.getNewSessionKeyStrokes()[0]);
    }

    popup.add(newSession);

    popup.addSeparator();

    ActionListener popupListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if ("Copy".equals(e.getActionCommand())) {
          copySelection(selection.getStart(), selection.getEnd());
        }
        else if ("Paste".equals(e.getActionCommand())) {
          pasteSelection();
        }
      }
    };

    JMenuItem menuItem = new JMenuItem("Copy");
    menuItem.setMnemonic(KeyEvent.VK_C);

    if (mySettingsProvider.getCopyKeyStrokes().length > 0) {
      menuItem.setAccelerator(mySettingsProvider.getCopyKeyStrokes()[0]);
    }

    menuItem.addActionListener(popupListener);
    menuItem.setEnabled(selection != null);
    popup.add(menuItem);
    menuItem = new JMenuItem("Paste");

    if (mySettingsProvider.getPasteKeyStrokes().length > 0) {
      menuItem.setAccelerator(mySettingsProvider.getPasteKeyStrokes()[0]);
    }

    menuItem.setMnemonic(KeyEvent.VK_P);
    menuItem.setEnabled(content != null);
    menuItem.addActionListener(popupListener);
    popup.add(menuItem);

    return popup;
  }

  public void setScrollingEnabled(boolean scrollingEnabled) {
    myScrollingEnabled = scrollingEnabled;

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        updateScrolling();
      }
    });
  }

  @Override
  public void setBlinkingCursor(boolean enabled) {
    myCursor.setBlinking(enabled);
  }

  public TerminalCursor getTerminalCursor() {
    return myCursor;
  }

  public TerminalOutputStream getTerminalOutputStream() {
    return myTerminalStarter;
  }

  @Override
  public void setWindowTitle(String name) {
    myWindowTitle = name;
    if (myTerminalPanelListener != null) {
      myTerminalPanelListener.onTitleChanged(myWindowTitle);
    }
  }

  public void setSelectionColor(TextStyle selectionColor) {
    mySelectionColor = selectionColor;
  }

  public class TerminalKeyHandler implements KeyListener {
    private final TerminalStarter myTerminalStarter;
    private SystemSettingsProvider mySettingsProvider;

    public TerminalKeyHandler(TerminalStarter terminalStarter, SystemSettingsProvider settingsProvider) {
      myTerminalStarter = terminalStarter;
      mySettingsProvider = settingsProvider;
    }

    public void keyPressed(final KeyEvent e) {
      try {
        if (isCopyKeyStroke(e)) {
          handleCopy();
          return;
        }
        if (isPasteKeyStroke(e)) {
          handlePaste();
          return;
        }
        if (isNewSessionKeyStroke(e)) {
          handleNewSession(e);
          return;
        }

        final int keycode = e.getKeyCode();
        final char keychar = e.getKeyChar();

        // numLock does not change the code sent by keypad VK_DELETE
        // although it send the char '.'
        if (keycode == KeyEvent.VK_DELETE && keychar == '.') {
          myTerminalStarter.sendBytes(new byte[]{'.'});
          return;
        }
        // CTRL + Space is not handled in KeyEvent; handle it manually
        else if (keychar == ' ' && (e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
          myTerminalStarter.sendBytes(new byte[]{Ascii.NUL});
          return;
        }

        final byte[] code = myTerminalStarter.getCode(keycode);
        if (code != null) {
          myTerminalStarter.sendBytes(code);
        }
        else if ((keychar & 0xff00) == 0) {
          final byte[] obuffer = new byte[1];
          obuffer[0] = (byte)keychar;
          myTerminalStarter.sendBytes(obuffer);
        }
      }
      catch (final Exception ex) {
        LOG.error("Error sending key to emulator", ex);
      }
    }

    private void handleNewSession(KeyEvent e) {
      mySettingsProvider.getNewSessionAction().actionPerformed(new ActionEvent(e.getSource(), e.getID(), "New Session", e.getModifiers()));
    }

    public void keyTyped(final KeyEvent e) {
      final char keychar = e.getKeyChar();
      if ((keychar & 0xff00) != 0) {
        final char[] foo = new char[1];
        foo[0] = keychar;
        try {
          myTerminalStarter.sendString(new String(foo));
        }
        catch (final RuntimeException ex) {
          LOG.error("Error sending key to emulator", ex);
        }
      }
    }

    //Ignore releases
    public void keyReleased(KeyEvent e) {
    }
  }

  private void handlePaste() {
    pasteSelection();
  }

  private void handleCopy() {
    if (mySelection != null) {
      copySelection(mySelection.getStart(), mySelection.getEnd());
      mySelection = null;
      repaint();
    }
  }

  private boolean isNewSessionKeyStroke(KeyEvent event) {
    for (KeyStroke ks : mySettingsProvider.getNewSessionKeyStrokes()) {
      if (ks.equals(KeyStroke.getKeyStrokeForEvent(event))) {
        return true;
      }
    }
    return false;
  }

  private boolean isCopyKeyStroke(KeyEvent event) {
    if (mySelection == null) {
      return false;
    }
    for (KeyStroke ks : mySettingsProvider.getCopyKeyStrokes()) {
      if (ks.equals(KeyStroke.getKeyStrokeForEvent(event))) {
        return true;
      }
    }
    return false;
  }

  private boolean isPasteKeyStroke(KeyEvent event) {
    for (KeyStroke ks : mySettingsProvider.getPasteKeyStrokes()) {
      if (ks.equals(KeyStroke.getKeyStrokeForEvent(event))) {
        return true;
      }
    }
    return false;
  }
}
