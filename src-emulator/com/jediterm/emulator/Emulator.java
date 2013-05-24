/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002 ymnk, JCraft,Inc.
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

package com.jediterm.emulator;

import com.jediterm.emulator.display.StoredCursor;
import com.jediterm.emulator.display.StyleState;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.io.InterruptedIOException;


public class Emulator {
  private static final Logger LOG = Logger.getLogger(Emulator.class);

  private final TerminalWriter myTerminalWriter;
  final protected TtyChannel myTtyChannel;

  /*
   * Character Attributes
   *
   * ESC [ Ps;Ps;Ps;...;Ps m
   *
   * Ps refers to a selective parameter. Multiple parameters are separated by
   * the semicolon character (0738). The parameters are executed in order and
   * have the following meanings: 0 or None All Attributes Off 1 Bold on 4
   * Underscore on 5 Blink on 7 Reverse video on
   *
   * Any other parameter values are ignored.
   */

  private final static Color[] COLORS = {Color.BLACK, Color.RED, Color.GREEN,
    Color.YELLOW, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE};

  public Emulator(final TerminalWriter terminalWriter, final TtyChannel channel) {
    myTtyChannel = channel;
    myTerminalWriter = terminalWriter;
  }

  public void sendBytes(final byte[] bytes) throws IOException {
    myTtyChannel.sendBytes(bytes);
  }

  public void start() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        singleIteration();
      }
    }
    catch (final InterruptedIOException e) {
      LOG.info("Terminal exiting");
    }
    catch (final Exception e) {
      if (!myTtyChannel.isConnected()) {
        myTerminalWriter.disconnected();
        return;
      }
      LOG.error("Caught exception in terminal thread", e);
    }
  }

  public byte[] getCode(final int key) {
    return CharacterUtils.getCode(key);
  }

  public void postResize(final Dimension dimension, final RequestOrigin origin) {
    Dimension pixelSize;
    synchronized (myTerminalWriter) {
      pixelSize = myTerminalWriter.resize(dimension, origin);
    }
    myTtyChannel.postResize(dimension, pixelSize);
  }

  void singleIteration() throws IOException {
    char b = myTtyChannel.getChar();

    switch (b) {
      case 0:
        break;
      case CharacterUtils.ESC: // ESC
        b = myTtyChannel.getChar();
        processEscSequence(b);
        break;
      case CharacterUtils.BEL:
        myTerminalWriter.beep();
        break;
      case CharacterUtils.BS:
        myTerminalWriter.backspace();
        break;
      case CharacterUtils.TAB: // ht(^I) TAB
        myTerminalWriter.horizontalTab();
        break;
      case CharacterUtils.CR:
        myTerminalWriter.carriageReturn();
        break;
      case CharacterUtils.FF:
      case CharacterUtils.VT:
      case CharacterUtils.LF:
        // '\n'
        myTerminalWriter.newLine();
        break;
      default:
        if (b <= CharacterUtils.US) {
          StringBuffer sb = new StringBuffer("Unhandled control character:");
          CharacterUtils.appendChar(sb, CharacterUtils.CharacterType.NONE, b);
          LOG.error(sb.toString());
        }
        else {
          myTtyChannel.pushChar(b);
          final int availableChars = myTtyChannel.advanceThroughASCII(myTerminalWriter.distanceToLineEnd());

          myTerminalWriter.writeASCII(myTtyChannel.myBuf, myTtyChannel.myOffset - availableChars, availableChars);
        }
        break;
    }
  }

  private void processEscSequence(char initByte) throws IOException {
    char b = initByte;
    if (b == '[') {
      processControlSequence();
    }
    else {
      final char[] intermediate = new char[10];
      int intCount = 0;
      while (b >= 0x20 && b <= 0x2F) {
        intCount++;
        intermediate[intCount - 1] = b;
        b = myTtyChannel.getChar();
      }
      if (b >= 0x30 && b <= 0x7E) {
        synchronized (myTerminalWriter) {
          switch (b) {
            case 'M':
              // Reverse index ESC M
              myTerminalWriter.reverseIndex();
              break;
            case 'D':
              // Index ESC D
              myTerminalWriter.index();
              break;
            case 'E':
              myTerminalWriter.nextLine();
              break;
            case '7':
              saveCursor();
              break;
            case '8':
              if (intCount > 0 && intermediate[0] == '#') {
                myTerminalWriter.fillScreen('E');
              }
              else {
                restoreCursor();
              }
              break;
            default:
              LOG.error("Unhandled escape sequence : " + escapeSequenceToString(intermediate, intCount, b));
          }
        }
      }
      else {
        LOG.error("Malformed escape sequence, pushing back to buffer: " + escapeSequenceToString(intermediate, intCount, b));

        // Push backwards
        for (int i = intCount - 1; i >= 0; i--) {
          final char ib = intermediate[i];
          myTtyChannel.pushChar(ib);
        }
        myTtyChannel.pushChar(b);
      }
    }
  }

  StoredCursor storedCursor = null;

  private void saveCursor() {

    if (storedCursor == null) {
      storedCursor = new StoredCursor();
    }
    myTerminalWriter.storeCursor(storedCursor);
  }

  private void restoreCursor() {
    myTerminalWriter.restoreCursor(storedCursor);
  }

  private static String escapeSequenceToString(final char[] intermediate,
                                               final int intCount, final char b) {

    StringBuilder sb = new StringBuilder("ESC ");

    for (int i = 0; i < intCount; i++) {
      final char ib = intermediate[i];
      sb.append(' ');
      sb.append(ib);
    }
    sb.append(' ');
    sb.append(b);
    return sb.toString();
  }

  private void processControlSequence() throws IOException {
    final ControlSequence args = new ControlSequence(myTtyChannel);

    if (LOG.isDebugEnabled()) {
      StringBuffer sb = new StringBuffer();
      sb.append("Control sequence\n");
      sb.append("parsed                        :");
      args.appendToBuffer(sb);
      sb.append('\n');
      sb.append("bytes read                    :ESC[");
      args.appendActualBytesRead(sb, myTtyChannel);
      LOG.debug(sb.toString());
    }
    if (args.pushBackReordered(myTtyChannel)) return;

    synchronized (myTerminalWriter) {

      switch (args.getFinalChar()) {
        case 'm':
          setCharacterAttributes(args);
          break;
        case 'r':
          setScrollingRegion(args);
          break;
        case 'A':
          cursorUp(args);
          break;
        case 'B':
          cursorDown(args);
          break;
        case 'C':
          cursorForward(args);
          break;
        case 'D':
          cursorBackward(args);
          break;
        case 'f':
        case 'H':
          cursorPosition(args);
          break;
        case 'K':
          eraseInLine(args);
          break;
        case 'J':
          eraseInDisplay(args);
          break;
        case 'h':
          setModes(args, true);
          break;
        case 'l':
          setModes(args, false);
          break;
        case 'c':
          // What are you
          // ESC [ c or ESC [ 0 c
          // Response is ESC [ ? 6 c
          if (LOG.isDebugEnabled()) {
            LOG.debug("Identifying to remote system as VT102");
          }
          myTtyChannel.sendBytes(CharacterUtils.DEVICE_ATTRIBUTES_RESPONSE);
          break;
        default:
          StringBuffer sb = new StringBuffer();
          sb.append("Unhandled Control sequence\n");
          sb.append("parsed                        :");
          args.appendToBuffer(sb);
          sb.append('\n');
          sb.append("bytes read                    :ESC[");
          args.appendActualBytesRead(sb, myTtyChannel);
          LOG.error(sb.toString());
          break;
      }
    }
  }

  private void eraseInDisplay(ControlSequence args) {
    // ESC [ Ps J
    final int arg = args.getArg(0, 0);
    myTerminalWriter.eraseInDisplay(arg);
  }

  private void eraseInLine(ControlSequence args) {
    // ESC [ Ps K
    final int arg = args.getArg(0, 0);

    myTerminalWriter.eraseInLine(arg);
  }

  private void cursorBackward(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;

    myTerminalWriter.cursorBackward(dx);
  }

  private void setScrollingRegion(ControlSequence args) {
    final int top = args.getArg(0, 1);
    final int bottom = args.getArg(1, myTerminalWriter.getTerminalHeight());

    myTerminalWriter.setScrollingRegion(top, bottom);
  }

  private void cursorForward(ControlSequence args) {
    int countX = args.getArg(0, 1);
    countX = countX == 0 ? 1 : countX;

    myTerminalWriter.cursorForward(countX);
  }

  private void cursorDown(ControlSequence cs) {
    int countY = cs.getArg(0, 0);
    countY = countY == 0 ? 1 : countY;
    myTerminalWriter.cursorDown(countY);
  }

  private void cursorPosition(ControlSequence cs) {
    final int argy = cs.getArg(0, 1);
    final int argx = cs.getArg(1, 1);

    myTerminalWriter.cursorPosition(argx, argy);
  }

  public void setCharacterAttributes(final ControlSequence args) {
    StyleState styleState = createStyleState(args);

    myTerminalWriter.setCharacterAttributes(styleState);
  }

  private static StyleState createStyleState(ControlSequence args) {
    StyleState styleState = new StyleState();

    final int argCount = args.getCount();
    if (argCount == 0) {
      styleState.reset();
    }

    for (int i = 0; i < argCount; i++) {
      final int arg = args.getArg(i, -1);
      if (arg == -1) {
        LOG.error("Error in processing char attributes, arg " + i);
        continue;
      }

      switch (arg) {
        case 0:
          styleState.reset();
          break;
        case 1:// Bright
          styleState.setOption(TextStyle.Option.BOLD, true);
          break;
        case 2:// Dim
          styleState.setOption(TextStyle.Option.DIM, true);
          break;
        case 4:// Underscore on
          styleState.setOption(TextStyle.Option.UNDERSCORE, true);
          break;
        case 5:// Blink on
          styleState.setOption(TextStyle.Option.BLINK, true);
          break;
        case 7:// Reverse video on
          styleState.setOption(TextStyle.Option.REVERSE, true);
          break;
        case 8: // Hidden
          styleState.setOption(TextStyle.Option.HIDDEN, true);
          break;
        default:
          if (arg >= 30 && arg <= 37) {
            styleState.setCurrentForeground(COLORS[arg - 30]);
          }
          else if (arg >= 40 && arg <= 47) {
            styleState.setCurrentBackground(COLORS[arg - 40]);
          }
          else {
            LOG.error("Unknown character attribute:" + arg);
          }
      }
    }
    return styleState;
  }

  private void cursorUp(ControlSequence cs) {
    int arg = cs.getArg(0, 0);
    arg = arg == 0 ? 1 : arg;
    myTerminalWriter.cursorUp(arg);
  }

  private void setModes(final ControlSequence args, final boolean on) throws IOException {
    final int argCount = args.getCount();
    final TerminalMode[] modeTable = args.getModeTable();
    for (int i = 0; i < argCount; i++) {
      final int num = args.getArg(i, -1);
      TerminalMode mode = null;
      if (num >= 0 && num < modeTable.length) {
        mode = modeTable[num];
      }

      if (mode == null) {
        LOG.error("Unknown mode " + num);
      }
      else if (on) {
        if (LOG.isInfoEnabled()) LOG.info("Modes: adding " + mode);
        myTerminalWriter.setMode(mode);
      }
      else {
        if (LOG.isInfoEnabled()) LOG.info("Modes: removing " + mode);
        myTerminalWriter.unsetMode(mode);
      }
    }
  }

  public void sendString(String string) throws IOException {
    myTtyChannel.sendString(string);
  }
}
