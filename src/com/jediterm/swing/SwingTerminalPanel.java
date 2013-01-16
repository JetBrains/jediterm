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

package com.jediterm.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jediterm.*;
import org.apache.log4j.Logger;

import com.jediterm.TextStyle;

public class SwingTerminalPanel extends JComponent implements TerminalDisplay, ClipboardOwner, StyledTextConsumer {
  private static final Logger logger = Logger.getLogger(SwingTerminalPanel.class);
  private static final long serialVersionUID = -1048763516632093014L;
  private static final double FPS = 20;

  private BufferedImage myImage;

  private Graphics2D myGfx;

  private final Component myTerminalPanel = this;

  private Font myNormalFont;

  private Font myBoldFont;

  private int myDescent = 0;

  private int myLineSpace = 0;

  Dimension myCharSize = new Dimension();

  Dimension myTermSize = new Dimension(80, 24);

  protected Point myCursor = new Point();

  private boolean myAntialiasing = true;

  private Emulator myEmulator = null;

  protected Point mySelectionStart;

  protected Point mySelectionEnd;

  protected boolean mySelectionInProgress;

  private Clipboard myClipboard;

  private ResizePanelDelegate myResizePanelDelegate;

  final private BackBuffer myBackBuffer;
  final private LinesBuffer myScrollBuffer;
  final private StyleState myStyleState;

  private final BoundedRangeModel myBoundedRangeModel = new DefaultBoundedRangeModel(0, 80, 0, 80);

  protected int myClientScrollOrigin;
  protected int newClientScrollOrigin;
  protected boolean myShouldDrawCursor = true;
  private KeyListener myKeyListener;


  public SwingTerminalPanel(BackBuffer backBuffer, LinesBuffer scrollBuffer, StyleState styleState) {
    myScrollBuffer = scrollBuffer;
    myBackBuffer = backBuffer;
    myStyleState = styleState;
    myBoundedRangeModel.setRangeProperties(0, myTermSize.height, -scrollBuffer.getLineCount(), myTermSize.height, false);
  }

  public void init() {
    myNormalFont = createFont();
    myBoldFont = myNormalFont.deriveFont(Font.BOLD);

    establishFontMetrics();

    setUpImages();
    setUpClipboard();
    setAntiAliasing(myAntialiasing);

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
          JPopupMenu popup = createPopupMenu(mySelectionStart, mySelectionEnd, getClipboardContent());
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

    myBoundedRangeModel.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        newClientScrollOrigin = myBoundedRangeModel.getValue();
      }
    });

    Timer redrawTimer = new Timer((int)(1000 / FPS), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        redrawFromDamage();
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

    Point top;
    Point bottom;

    if (selectionStart.y == selectionEnd.y) {                        /* same line */
      top = selectionStart.x < selectionEnd.x ? selectionStart
                                              : selectionEnd;
      bottom = selectionStart.x >= selectionEnd.x ? selectionStart
                                                  : selectionEnd;
    }
    else {
      top = selectionStart.y < selectionEnd.y ? selectionStart
                                              : selectionEnd;
      bottom = selectionStart.y > selectionEnd.y ? selectionStart
                                                 : selectionEnd;
    }

    final StringBuilder selectionText = new StringBuilder();

    if (top.y < 0) {
      final Point scrollEnd = bottom.y >= 0 ? new Point(myTermSize.width, -1) : bottom;
      myScrollBuffer.processLines(top.y, scrollEnd.y - top.y,
                                  new SelectionTextAppender(selectionText, top, scrollEnd));
    }

    if (bottom.y >= 0) {
      final Point backBegin = top.y < 0 ? new Point(0, 0) : top;
      myBackBuffer.processBufferCells(0, backBegin.y, myTermSize.width, bottom.y - backBegin.y + 1,
                                      new SelectionTextAppender(selectionText, backBegin, bottom));
    }

    if (selectionText.length() != 0) {

      try {
        myClipboard.setContents(new StringSelection(selectionText.toString()), this);
      }
      catch (final IllegalStateException e) {
        logger.error("Could not set clipboard:", e);
      }
    }
  }

  void pasteSelection() {
    try {
      final String selection = getClipboardContent();
      myEmulator.sendString(selection);
    }
    catch (final IOException e) {
      logger.info(e);
    }
  }

  private String getClipboardContent() {
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

  private void setUpImages() {
    final BufferedImage oldImage = myImage;
    int width = getPixelWidth();
    int height = getPixelHeight();
    if (width > 0 && height > 0) {
      myImage = createBufferedImage(width, height);
      myGfx = myImage.createGraphics();

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
    if (myEmulator != null) {
      final int newWidth = getWidth() / myCharSize.width;
      final int newHeight = getHeight() / myCharSize.height;

      if (newHeight > 0 && newWidth > 0) {
        final Dimension newSize = new Dimension(newWidth, newHeight);

        myEmulator.postResize(newSize, RequestOrigin.User);
      }
    }
  }

  public void setEmulator(final Emulator emulator) {
    myEmulator = emulator;
    sizeTerminalFromComponent();
  }

  public void setKeyListener(final KeyListener keyListener) {
    this.myKeyListener = keyListener;
  }

  public Dimension requestResize(final Dimension newSize,
                                 final RequestOrigin origin,
                                 int cursorY,
                                 BufferedTerminalWriter.ResizeHandler resizeHandler) {
    if (!newSize.equals(myTermSize)) {
      myBackBuffer.lock();
      try {
        myBackBuffer.resize(newSize, origin, cursorY, resizeHandler);
        myTermSize = (Dimension)newSize.clone();
        // resize images..
        setUpImages();

        final Dimension pixelDimension = new Dimension(getPixelWidth(), getPixelHeight());

        setPreferredSize(pixelDimension);
        if (myResizePanelDelegate != null) myResizePanelDelegate.resizedPanel(pixelDimension, origin);
        myBoundedRangeModel.setRangeProperties(0, myTermSize.height, -myScrollBuffer.getLineCount(), myTermSize.height, false);
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

  @Override
  public void paintComponent(final Graphics g) {
    Graphics2D gfx = (Graphics2D)g;
    if (myImage != null) {
      gfx.drawImage(myImage, 0, 0, myTerminalPanel);
      drawMargin(gfx, myImage.getWidth(), myImage.getHeight());
      drawCursor(gfx);
      drawSelection(gfx);
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

  public void drawCursor(Graphics2D g) {
    if (myShouldDrawCursor) {
      final int y = (myCursor.y - 1 - myClientScrollOrigin);
      if (y >= 0 && y < myTermSize.height) {
        TextStyle current = myStyleState.getCurrent();
        g.setColor(current.getForeground());
        g.setXORMode(current.getBackground());
        g.fillRect(myCursor.x * myCharSize.width, y * myCharSize.height,
                   myCharSize.width, myCharSize.height);
      }
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
  public void consume(int x, int y, TextStyle style, CharBuffer buf) {
    if (myGfx != null) {
      myGfx.setColor(myStyleState.getBackground(style.getBackgroundForRun()));
      myGfx
        .fillRect(x * myCharSize.width, (y - myClientScrollOrigin) * myCharSize.height, buf.getLen() * myCharSize.width, myCharSize.height);

      myGfx.setFont(style.hasOption(TextStyle.Option.BOLD) ? myBoldFont : myNormalFont);
      myGfx.setColor(myStyleState.getForeground(style.getForegroundForRun()));

      int baseLine = (y + 1 - myClientScrollOrigin) * myCharSize.height - myDescent;


      myGfx.drawChars(buf.getBuf(), buf.getStart(), buf.getLen(), x * myCharSize.width, baseLine);

      if (style.hasOption(TextStyle.Option.UNDERSCORE)) {
        myGfx.drawLine(x * myCharSize.width, baseLine + 1, (x + buf.getLen()) * myCharSize.width, baseLine + 1);
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

      myScrollBuffer.processLines(myClientScrollOrigin, -dy, this);
    }
    else {
      //Scrolling down; Copied up
      // New area at the bottom to be filled - can be from both

      int oldEnd = oldOrigin + myTermSize.height;

      // Either its the whole amount above the back buffer + some more
      // Or its the whole amount we moved
      // Or we are already out of the scroll buffer
      int portionInScroll = oldEnd < 0 ? Math.min(-oldEnd, dy) : 0;

      int portionInBackBuffer = dy - portionInScroll;

      if (portionInScroll > 0) {
        myScrollBuffer.processLines(oldEnd, portionInScroll, this);
      }

      if (portionInBackBuffer > 0) {
        myBackBuffer.processBufferRows(oldEnd + portionInScroll, portionInBackBuffer, this);
      }
    }
  }

  int noDamage = 0;
  int framesSkipped = 0;
  private boolean cursorChanged;

  public void redrawFromDamage() {

    final int newOrigin = newClientScrollOrigin;
    if (!myBackBuffer.tryLock()) {
      if (framesSkipped >= 5) {
        myBackBuffer.lock();
      }
      else {
        framesSkipped++;
        return;
      }
    }
    try {
      framesSkipped = 0;

      boolean serverScroll = pendingScrolls.enact(myGfx, getPixelWidth(), myCharSize.height);

      boolean clientScroll = myClientScrollOrigin != newOrigin;
      if (clientScroll) {
        final int oldOrigin = myClientScrollOrigin;
        myClientScrollOrigin = newOrigin;
        clientScrollOriginChanged(oldOrigin);
      }

      boolean hasDamage = myBackBuffer.hasDamage();
      if (hasDamage) {
        noDamage = 0;

        myBackBuffer.processDamagedCells(this);
        myBackBuffer.resetDamage();
      }
      else {
        noDamage++;
      }

      if (serverScroll || clientScroll || hasDamage || cursorChanged) {
        repaint();
        cursorChanged = false;
      }
    }
    finally {
      myBackBuffer.unlock();
    }
  }

  private void drawMargin(Graphics2D gfx, int width, int height) {
    gfx.setColor(getBackground());
    gfx.fillRect(0, height, width, getHeight() - height);
  }

  public void scrollArea(final int y, final int h, int dy) {
    if (dy < 0) {
      //Moving lines off the top of the screen
      //TODO: Something to do with application keypad mode
      //TODO: Something to do with the scroll margins

      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myBoundedRangeModel.setRangeProperties(0, myTermSize.height, -myScrollBuffer.getLineCount(), myTermSize.height, false);
        }
      });
    }
    mySelectionStart = null;
    mySelectionEnd = null;
    pendingScrolls.add(y, h, dy);
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
    myCursor.x = x;
    myCursor.y = y;
    cursorChanged = true;
  }

  public void beep() {
    Toolkit.getDefaultToolkit().beep();
  }

  public void setLineSpace(final int foo) {
    myLineSpace = foo;
  }

  public void setAntiAliasing(final boolean foo) {
    if (myGfx == null) {
      return;
    }
    myAntialiasing = foo;
    final Object mode = foo ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
    final RenderingHints hints = new RenderingHints(
      RenderingHints.KEY_TEXT_ANTIALIASING, mode);
    myGfx.setRenderingHints(hints);
  }

  public BoundedRangeModel getBoundedRangeModel() {
    return myBoundedRangeModel;
  }

  public BackBuffer getBackBuffer() {
    return myBackBuffer;
  }

  public LinesBuffer getScrollBuffer() {
    return myScrollBuffer;
  }

  public void lock() {
    myBackBuffer.lock();
  }

  public void unlock() {
    myBackBuffer.unlock();
  }

  @Override
  public void setShouldDrawCursor(boolean shouldDrawCursor) {
    myShouldDrawCursor = shouldDrawCursor;
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
}
