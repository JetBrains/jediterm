package com.jediterm.terminal.ui;

import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jediterm.terminal.*;
import com.jediterm.terminal.TextStyle.Option;
import com.jediterm.terminal.display.*;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.mouse.TerminalMouseListener;
import com.jediterm.terminal.ui.settings.SettingsProvider;
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
import java.awt.image.ImageObserver;
import java.lang.ref.WeakReference;
import java.util.List;

public class TerminalPanel extends JComponent implements TerminalDisplay, ClipboardOwner, StyledTextConsumer, TerminalActionProvider {
  private static final Logger LOG = Logger.getLogger(TerminalPanel.class);
  private static final long serialVersionUID = -1048763516632093014L;
  private static final double FPS = 50;

  public static final double SCROLL_SPEED = 0.05;

  /*images*/
  private BufferedImage myImage;
  private BufferedImage myImageForSelection;
  protected Graphics2D myGfx;
  private Graphics2D myGfxForSelection;

  private final Component myTerminalPanel = this;

  /*font related*/
  private Font myNormalFont;
  private Font myBoldFont;
  private int myDescent = 0;
  protected Dimension myCharSize = new Dimension();
  protected Dimension myTermSize = new Dimension(80, 24);

  private TerminalStarter myTerminalStarter = null;

  protected TerminalSelection mySelection = null;
  private Clipboard myClipboard;

  private TerminalPanelListener myTerminalPanelListener;

  private SettingsProvider mySettingsProvider;
  final private BackBuffer myBackBuffer;

  final private StyleState myStyleState;

  /*scroll and cursor*/
  final private TerminalCursor myCursor = new TerminalCursor();
  private final BoundedRangeModel myBoundedRangeModel = new DefaultBoundedRangeModel(0, 80, 0, 80);
  protected int myClientScrollOrigin;
  protected int newClientScrollOrigin;
  protected KeyListener myKeyListener;
  private long myLastCursorChange;
  private boolean myCursorIsShown;
  private long myLastResize;
  private boolean myScrollingEnabled = true;

  private String myWindowTitle = "Terminal";

  private TerminalActionProvider myNextActionProvider;

  public TerminalPanel(@NotNull SettingsProvider settingsProvider, @NotNull BackBuffer backBuffer, @NotNull StyleState styleState) {
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
        if (mySettingsProvider.emulateX11CopyPaste()) {
          handleCopy(false);
        }

        if (e.getPoint().y < 0) {
          moveScrollBar((int) ((e.getPoint().y) * SCROLL_SPEED));
        }
        if (e.getPoint().y > getPixelHeight()) {
          moveScrollBar((int) ((e.getPoint().y - getPixelHeight()) * SCROLL_SPEED));
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
          } else if (count == 2) {
            // select word
            final Point charCoords = panelToCharCoords(e.getPoint());
            Point start = SelectionUtil.getPreviousSeparator(charCoords, myBackBuffer);
            Point stop = SelectionUtil.getNextSeparator(charCoords, myBackBuffer);
            mySelection = new TerminalSelection(start);
            mySelection.updateEnd(stop, myTermSize.width);

            if (mySettingsProvider.emulateX11CopyPaste()) {
              handleCopy(false);
            }
          } else if (count == 3) {
            // select line
            final Point charCoords = panelToCharCoords(e.getPoint());
            int startLine = charCoords.y;
            while (startLine > - getScrollBuffer().getLineCount() 
                && myBackBuffer.getLine(startLine - 1).isWrapped()) {
              startLine--;
            }
            int endLine = charCoords.y;
            while (endLine < myBackBuffer.getHeight()
                && myBackBuffer.getLine(endLine).isWrapped()) {
              endLine++;
            }
            mySelection = new TerminalSelection(new Point(0, startLine));
            mySelection.updateEnd(new Point(myTermSize.width, endLine), myTermSize.width);

            if (mySettingsProvider.emulateX11CopyPaste()) {
              handleCopy(false);
            }
          }
        } else if (e.getButton() == MouseEvent.BUTTON2 && mySettingsProvider.emulateX11CopyPaste()) {
          handlePaste();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
          JPopupMenu popup = createPopupMenu();
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

    Timer redrawTimer = new Timer((int) (1000 / FPS), new WeakRedrawTimer(this));
    setDoubleBuffered(true);
    redrawTimer.start();
    repaint();
  }

  protected boolean isRetina() {
    return UIUtil.isRetina();
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
        terminalPanel.redraw();
      } else { // terminalPanel was garbage collected
        Timer timer = (Timer) e.getSource();
        timer.removeActionListener(this);
        timer.stop();
      }
    }
  }

  private void moveScrollBar(int k) {
    myBoundedRangeModel.setValue(myBoundedRangeModel.getValue() + k);
  }

  protected Font createFont() {
    return mySettingsProvider.getTerminalFont();
  }

  protected Point panelToCharCoords(final Point p) {
    int x = Math.min(p.x / myCharSize.width, getColumnCount() - 1);
    int y = Math.min(p.y / myCharSize.height, getRowCount() - 1) + myClientScrollOrigin;
    return new Point(x, y);
  }

  protected Point charToPanelCoords(final Point p) {
    return new Point(p.x * myCharSize.width, (p.y - myClientScrollOrigin) * myCharSize.height);
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
      } catch (final IllegalStateException e) {
        LOG.error("Could not set clipboard:", e);
      }
    }
  }

  protected void setCopyContents(StringSelection selection) {
    myClipboard.setContents(selection, this);
  }

  protected void pasteSelection() {
    final String selection = getClipboardContent();

    try {
      myTerminalStarter.sendString(selection);
    } catch (RuntimeException e) {
      LOG.info(e);
    }
  }

  protected String getClipboardContent() {
    try {
      return (String) myClipboard.getData(DataFlavor.stringFlavor);
    } catch (Exception e) {
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
        drawImage(myGfx, oldImage);
      }
    }
  }

  private void drawImage(Graphics2D gfx, BufferedImage image) {
    drawImage(gfx, image, 0, 0, myTerminalPanel);
  }

  protected void drawImage(Graphics2D gfx, BufferedImage image, int x, int y, ImageObserver observer) {
    gfx.drawImage(image, x, y,
        image.getWidth(), image.getHeight(), observer);
  }

  private Pair<BufferedImage, Graphics2D> createAndInitImage(int width, int height) {
    BufferedImage image = createBufferedImage(width, height);

    Graphics2D gfx = image.createGraphics();

    setupAntialiasing(gfx);

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
        myTermSize = (Dimension) newSize.clone();
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
      } finally {
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

    final float lineSpace = mySettingsProvider.getLineSpace();
    final FontMetrics fo = graphics.getFontMetrics();
    myDescent = fo.getDescent();
    myCharSize.width = fo.charWidth('@');
    myCharSize.height = fo.getHeight() + (int) (lineSpace * 2);
    myDescent += lineSpace;

    img.flush();
    graphics.dispose();
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
  public Color getBackground() {
    return getPalette().getColor(myStyleState.getBackground());
  }

  @Override
  public Color getForeground() {
    return getPalette().getColor(myStyleState.getForeground());
  }

  @Override
  public void paintComponent(final Graphics g) {
    Graphics2D gfx = (Graphics2D) g;
    if (myImage != null) {
      drawImage(gfx, myImage);
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
    } else if (id == KeyEvent.KEY_RELEASED) {
                        /* keyReleased(e); */
    } else if (id == KeyEvent.KEY_TYPED) {
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

  public void initKeyHandler() {
    setKeyListener(new TerminalKeyHandler());
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
      } else {
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

          g.setXORMode(getPalette().getColor(current.getBackground()));
          if (isCursorShown) {
            g.setColor(getPalette().getColor(current.getForeground()));
          } else {
            g.setColor(getPalette().getColor(current.getBackground()));
          }
          g.fillRect(myCursorCoordinates.x * myCharSize.width, y * myCharSize.height,
              myCharSize.width, myCharSize.height);

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
    } else {
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

  private void drawImage(Graphics2D g, BufferedImage image, int x1, int y1, int x2, int y2) {
    drawImage(g, image, x1, y1, x2, y2, x1, y1, x2, y2);
  }

  protected void drawImage(Graphics2D g, BufferedImage image, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
    g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  private void copyImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
    drawImage(g, image, x, y, x + width, y + height, x, y, x + width, y + height);
  }

  @Override
  public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer buf, int startRow) {
    if (myGfx != null) {
      drawCharacters(x, y, style, buf, myGfx);
    }
    if (myGfxForSelection != null) {
      TextStyle selectionStyle = style.clone();
      if(mySettingsProvider.useInverseSelectionColor()) {
        selectionStyle.setOption(Option.INVERSE, !selectionStyle.hasOption(Option.INVERSE));
        if (selectionStyle.getForeground() == null) {
          selectionStyle.setForeground(myStyleState.getForeground());
        }
        if (selectionStyle.getBackground() == null) {
          selectionStyle.setBackground(myStyleState.getBackground());
        }
      } else {
        TextStyle mySelectionStyle = mySettingsProvider.getSelectionColor();
        selectionStyle.setBackground(mySelectionStyle.getBackground());
        selectionStyle.setForeground(mySelectionStyle.getForeground());
      }
      drawCharacters(x, y, selectionStyle, buf, myGfxForSelection);
    }
  }

  private void drawCharacters(int x, int y, TextStyle style, CharBuffer buf, Graphics2D gfx) {
    gfx.setColor(getPalette().getColor(myStyleState.getBackground(style.getBackgroundForRun())));
    gfx
        .fillRect(x * myCharSize.width, (y - myClientScrollOrigin) * myCharSize.height, buf.getLength() * myCharSize.width,
            myCharSize.height);

    gfx.setFont(style.hasOption(TextStyle.Option.BOLD) ? myBoldFont : myNormalFont);
    gfx.setColor(getPalette().getColor(myStyleState.getForeground(style.getForegroundForRun())));

    int baseLine = (y + 1 - myClientScrollOrigin) * myCharSize.height - myDescent;


    gfx.drawChars(buf.getBuf(), buf.getStart(), buf.getLength(), x * myCharSize.width, baseLine);

    if (style.hasOption(TextStyle.Option.UNDERLINED)) {
      gfx.drawLine(x * myCharSize.width, baseLine + 1, (x + buf.getLength()) * myCharSize.width, baseLine + 1);
    }
  }

  private ColorPalette getPalette() {
    return mySettingsProvider.getTerminalColorPalette();
  }

  private void clientScrollOriginChanged(int oldOrigin) {
    int dy = myClientScrollOrigin - oldOrigin;

    int dyPix = dy * myCharSize.height;

    copyAndClearAreaOnScroll(dyPix, myGfx, myImage);
    copyAndClearAreaOnScroll(dyPix, myGfxForSelection, myImageForSelection);

    if (dy < 0) {
      // Scrolling up; Copied down
      // New area at the top to be filled in - can only be from scroll buffer
      //

      myBackBuffer.getScrollBuffer().processLines(myClientScrollOrigin, -dy, this);
    } else {
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

  private void copyAndClearAreaOnScroll(int dy, Graphics2D gfx, BufferedImage image) {
    copyArea(gfx, image, 0, Math.max(0, dy),
        getPixelWidth(), getPixelHeight() - Math.abs(dy),
        0, -dy);

    //clear rect before drawing scroll buffer on it
    gfx.setColor(getBackground());
    if (dy < 0) {
      gfx.fillRect(0, 0, getPixelWidth(), Math.abs(dy));
    } else {
      gfx.fillRect(0, getPixelHeight() - dy, getPixelWidth(), dy);
    }
  }

  private void copyArea(Graphics2D gfx, BufferedImage image, int x, int y, int width, int height, int dx, int dy) {
    if (isRetina()) {
      Pair<BufferedImage, Graphics2D> pair = createAndInitImage(x + width, y + height);

      drawImage(pair.second, image,
          x, y, x + width, y + height
      );
      drawImage(gfx, pair.first,
          x + dx, y + dy, x + dx + width, y + dy + height, //destination
          x, y, x + width, y + height   //source
      );
    } else {
      gfx.copyArea(x, y, width, height, dx, dy);
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
      } else {
        myFramesSkipped++;
        return false;
      }
    }
    try {
      myFramesSkipped = 0;

      boolean serverScroll = pendingScrolls.enact(myGfx, myImage, getPixelWidth(), myCharSize.height);

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
      } else {
        myNoDamage++;
      }

      return serverScroll || clientScroll || hasDamage;
    } finally {
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
    } else {
      myBoundedRangeModel.setRangeProperties(0, myTermSize.height, 0, myTermSize.height, false);
    }
  }

  private class PendingScrolls {
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
      } else {
        scrollCount++;
        ensureArrays(scrollCount);
        ys[scrollCount] = y;
        hs[scrollCount] = h;
        dys[scrollCount] = dy;
      }
    }

    boolean enact(Graphics2D gfx, BufferedImage image, int width, int charHeight) {
      if (scrollCount < 0) return false;
      for (int i = 0; i <= scrollCount; i++) {
        copyArea(gfx, image, 0, ys[i] * charHeight, width, hs[i] * charHeight, 0, dys[i] * charHeight);
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

  protected JPopupMenu createPopupMenu() {
    JPopupMenu popup = new JPopupMenu();

    TerminalAction.addToMenu(popup, this);

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

  @Override
  public List<TerminalAction> getActions() {
    return Lists.newArrayList(
        new TerminalAction("Copy", mySettingsProvider.getCopyKeyStrokes(), new Predicate<KeyEvent>() {
          @Override
          public boolean apply(KeyEvent input) {
            return handleCopy(true);
          }
        }).withMnemonicKey(KeyEvent.VK_C).withEnabledSupplier(new Supplier<Boolean>() {
          @Override
          public Boolean get() {
            return mySelection != null;
          }
        }),
        new TerminalAction("Paste", mySettingsProvider.getPasteKeyStrokes(), new Predicate<KeyEvent>() {
          @Override
          public boolean apply(KeyEvent input) {
            handlePaste();
            return true;
          }
        }).withMnemonicKey(KeyEvent.VK_P).withEnabledSupplier(new Supplier<Boolean>() {
          @Override
          public Boolean get() {
            return getClipboardContent() != null;
          }
        }));
  }

  @Override
  public TerminalActionProvider getNextProvider() {
    return myNextActionProvider;
  }

  @Override
  public void setNextProvider(TerminalActionProvider provider) {
    myNextActionProvider = provider;
  }

  private void processTerminalKeyPressed(KeyEvent e) {
    try {
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
      } else if ((keychar & 0xff00) == 0) {
        final byte[] obuffer = new byte[1];
        obuffer[0] = (byte) keychar;
        myTerminalStarter.sendBytes(obuffer);
      }
    } catch (final Exception ex) {
      LOG.error("Error sending key to emulator", ex);
    }
  }

  private void processTerminalKeyTyped(KeyEvent e) {
    final char keychar = e.getKeyChar();
    if ((keychar & 0xff00) != 0) {
      final char[] foo = new char[1];
      foo[0] = keychar;
      try {
        myTerminalStarter.sendString(new String(foo));
      } catch (final RuntimeException ex) {
        LOG.error("Error sending key to emulator", ex);
      }
    }
  }

  public class TerminalKeyHandler implements KeyListener {

    public TerminalKeyHandler() {
    }

    public void keyPressed(final KeyEvent e) {
      if (!TerminalAction.processEvent(TerminalPanel.this, e)) {
        processTerminalKeyPressed(e);
      }
    }

    public void keyTyped(final KeyEvent e) {
      processTerminalKeyTyped(e);
    }

    //Ignore releases
    public void keyReleased(KeyEvent e) {
    }
  }

  private void handlePaste() {
    pasteSelection();
  }

  // "unselect" is needed to handle Ctrl+C copy shortcut collision with ^C signal shortcut
  private boolean handleCopy(boolean unselect) {
    if (mySelection != null) {
      copySelection(mySelection.getStart(), mySelection.getEnd());
      if (unselect) {
        mySelection = null;
        repaint();
      }
      return true;
    }
    return false;
  }
}
