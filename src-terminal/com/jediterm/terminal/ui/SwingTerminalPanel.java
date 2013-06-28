/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002-2004 ymnk, JCraft,Inc.
 *  
 * Written by: 2002 ymnk<ymnk@jcaft.com>
 *   
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.jediterm.terminal.ui;

import com.jediterm.terminal.*;
import com.jediterm.terminal.display.*;
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

public class SwingTerminalPanel extends JComponent implements TerminalDisplay, ClipboardOwner, StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(SwingTerminalPanel.class);
  private static final long serialVersionUID = -1048763516632093014L;
  private static final double FPS = 50;

  private BufferedImage myImage;

  protected Graphics2D myGfx;

  private final Component myTerminalPanel = this;

  private Font myNormalFont;

  private Font myBoldFont;

  private int myDescent = 0;

  private int myLineSpace = 0;

  protected Dimension myCharSize = new Dimension();

  protected Dimension myTermSize = new Dimension(80, 24);

  private boolean myAntialiasing = true;

  private TerminalStarter myTerminalStarter = null;

  protected Point mySelectionStart;

  protected Point mySelectionEnd;

  protected boolean mySelectionInProgress;

  private Clipboard myClipboard;

  private ResizePanelDelegate myResizePanelDelegate;

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


  public SwingTerminalPanel(BackBuffer backBuffer, StyleState styleState) {
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

        if (!mySelectionInProgress) {
          mySelectionStart = new Point(charCoords);
          mySelectionInProgress = true;
        }
        repaint();
        mySelectionEnd = charCoords;
        mySelectionEnd.x = Math.min(mySelectionEnd.x + 1, myTermSize.width);
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        myBoundedRangeModel.setValue(myBoundedRangeModel.getValue() + notches);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        mySelectionInProgress = false;

        repaint();
      }

      @Override
      public void mouseClicked(final MouseEvent e) {
        requestFocusInWindow();
        if (e.getButton() == MouseEvent.BUTTON3) {
          JPopupMenu popup = createPopupMenu(mySelectionStart, mySelectionEnd, getClipboardString());
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
        else {
          mySelectionStart = null;
          mySelectionEnd = null;
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

  protected Font createFont() {
    return Font.decode("Monospaced-14");
  }

  private Point panelToCharCoords(final Point p) {
    return new Point(p.x / myCharSize.width, p.y / myCharSize.height + myClientScrollOrigin);
  }

  void setUpClipboard() {
    myClipboard = Toolkit.getDefaultToolkit().getSystemSelection();
    if (myClipboard == null) {
      myClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
  }

  private void copySelection(final Point selectionStart, final Point selectionEnd) {
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
        logger.error("Could not set clipboard:", e);
      }
    }
  }

  protected void setCopyContents(StringSelection selection) {
    myClipboard.setContents(selection, this);
  }

  private void pasteSelection() {
    final String selection = getClipboardString();

    try {
      myTerminalStarter.sendString(selection);
    }
    catch (RuntimeException e) {
      logger.info(e);
    }
  }

  private String getClipboardString() {
    try {
      return getClipboardContent();
    }
    catch (final Exception e) {
      logger.info(e);
    }
    return null;
  }

  protected String getClipboardContent() throws IOException, UnsupportedFlavorException {
    try {
      return (String)myClipboard.getData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      logger.info(e);
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
      myImage = createBufferedImage(width, height);
      myGfx = myImage.createGraphics();

      if (UIUtil.isRetina()) {
        myGfx.scale(0.5, 0.5);
      }
      setupAntialiasing(myGfx, myAntialiasing);

      myGfx.setColor(getBackground());

      myGfx.fillRect(0, 0, width, height);

      if (oldImage != null) {
        myGfx.drawImage(oldImage, 0, 0,
                        oldImage.getWidth(), oldImage.getHeight(), myTerminalPanel);
      }
    }
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
                                 BufferedDisplayTerminal.ResizeHandler resizeHandler) {
    if (!newSize.equals(myTermSize)) {
      myBackBuffer.lock();
      try {
        myBackBuffer.resize(newSize, origin, cursorY, resizeHandler);
        myTermSize = (Dimension)newSize.clone();
        // resize images..
        setupImages();

        final Dimension pixelDimension = new Dimension(getPixelWidth(), getPixelHeight());

        setPreferredSize(pixelDimension);
        if (myResizePanelDelegate != null) myResizePanelDelegate.resizedPanel(pixelDimension, origin);
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

  public void setResizePanelDelegate(final ResizePanelDelegate resizeDelegate) {
    myResizePanelDelegate = resizeDelegate;
  }

  private void establishFontMetrics() {
    final BufferedImage img = createBufferedImage(1, 1);
    final Graphics2D graphics = img.createGraphics();
    graphics.setFont(myNormalFont);

    final FontMetrics fo = graphics.getFontMetrics();
    myDescent = fo.getDescent();
    myCharSize.width = fo.charWidth('@');
    myCharSize.height = fo.getHeight() + myLineSpace * 2;
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
      drawSelection(gfx);
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

          if (isCursorShown) {
            g.setColor(current.getForeground());
          }
          else {
            g.setColor(current.getBackground());
          }
          int scale = 1;
          if (UIUtil.isRetina()) {
            scale = 2;
          }
          g.fillRect(myCursorCoordinates.x * myCharSize.width*scale, y * myCharSize.height*scale,
                     myCharSize.width*scale, myCharSize.height*scale);


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

  public void drawSelection(Graphics2D g) {
                /* which is the top one */
    Point top;
    Point bottom;
    TextStyle current = myStyleState.getCurrent();
    g.setColor(current.getForeground());
    g.setXORMode(current.getBackground());
    if (mySelectionStart == null || mySelectionEnd == null) {
      return;
    }

    if (mySelectionStart.y == mySelectionEnd.y) {
                        /* same line */
      if (mySelectionStart.x == mySelectionEnd.x) {
        return;
      }
      top = mySelectionStart.x < mySelectionEnd.x ? mySelectionStart
                                                  : mySelectionEnd;
      bottom = mySelectionStart.x >= mySelectionEnd.x ? mySelectionStart
                                                      : mySelectionEnd;

      g.fillRect(top.x * myCharSize.width, (top.y - myClientScrollOrigin) * myCharSize.height,
                 (bottom.x - top.x) * myCharSize.width, myCharSize.height);
    }
    else {
      top = mySelectionStart.y < mySelectionEnd.y ? mySelectionStart
                                                  : mySelectionEnd;
      bottom = mySelectionStart.y > mySelectionEnd.y ? mySelectionStart
                                                     : mySelectionEnd;
                        /* to end of first line */
      g.fillRect(top.x * myCharSize.width, (top.y - myClientScrollOrigin) * myCharSize.height,
                 (myTermSize.width - top.x) * myCharSize.width, myCharSize.height);

      if (bottom.y - top.y > 1) {
                                /* intermediate lines */
        g.fillRect(0, (top.y + 1 - myClientScrollOrigin) * myCharSize.height,
                   myTermSize.width * myCharSize.width, (bottom.y - top.y - 1)
                                                        * myCharSize.height);
      }

			/* from beginning of last line */

      g.fillRect(0, (bottom.y - myClientScrollOrigin) * myCharSize.height, bottom.x
                                                                           * myCharSize.width, myCharSize.height);
    }
  }

  @Override
  public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer buf, int startRow) {
    if (myGfx != null) {
      myGfx.setColor(myStyleState.getBackground(style.getBackgroundForRun()));
      myGfx
        .fillRect(x * myCharSize.width, (y - myClientScrollOrigin) * myCharSize.height, buf.getLength() * myCharSize.width, myCharSize.height);

      myGfx.setFont(style.hasOption(TextStyle.Option.BOLD) ? myBoldFont : myNormalFont);
      myGfx.setColor(myStyleState.getForeground(style.getForegroundForRun()));

      int baseLine = (y + 1 - myClientScrollOrigin) * myCharSize.height - myDescent;


      myGfx.drawChars(buf.getBuf(), buf.getStart(), buf.getLength(), x * myCharSize.width, baseLine);

      if (style.hasOption(TextStyle.Option.UNDERLINED)) {
        myGfx.drawLine(x * myCharSize.width, baseLine + 1, (x + buf.getLength()) * myCharSize.width, baseLine + 1);
      }
    }
  }

  private void clientScrollOriginChanged(int oldOrigin) {
    int dy = myClientScrollOrigin - oldOrigin;

    int dyPix = dy * myCharSize.height;

    myGfx.copyArea(0, Math.max(0, dyPix),
                   getPixelWidth(), getPixelHeight() - Math.abs(dyPix),
                   0, -dyPix);

    if (dy < 0) {
      // Scrolling up; Copied down
      // New area at the top to be filled in - can only be from scroll buffer
      //


      //clear rect before drawing scroll buffer on it
      myGfx.setColor(getBackground());
      myGfx.fillRect(0, Math.max(0, dyPix), getPixelWidth(), Math.abs(dyPix)); 
      
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

  public void scrollArea(final int y, final int h, int dy) {
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
    mySelectionStart = null;
    mySelectionEnd = null;
    pendingScrolls.add(y, h, dy);
  }

  private void updateScrolling() {
    if (myScrollingEnabled) {
      myBoundedRangeModel.setRangeProperties(0, myTermSize.height, -myBackBuffer.getScrollBuffer().getLineCount(), myTermSize.height, false);
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

  public void setLineSpace(final int foo) {
    myLineSpace = foo;
  }

  public void setAntiAliasing(final boolean antialiasing) {
    if (myGfx == null) {
      return;
    }
    setupAntialiasing(myGfx, antialiasing);
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

  protected JPopupMenu createPopupMenu(final Point selectionStart, final Point selectionEnd, String content) {
    JPopupMenu popup = new JPopupMenu();

    ActionListener popupListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if ("Copy".equals(e.getActionCommand())) {
          copySelection(selectionStart, selectionEnd);
        }
        else if ("Paste".equals(e.getActionCommand())) {
          pasteSelection();
        }
      }
    };

    JMenuItem menuItem = new JMenuItem("Copy");
    menuItem.addActionListener(popupListener);
    menuItem.setEnabled(selectionStart != null);
    popup.add(menuItem);
    menuItem = new JMenuItem("Paste");
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
}
