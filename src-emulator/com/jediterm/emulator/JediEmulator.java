package com.jediterm.emulator;

import com.google.common.base.Ascii;
import com.jediterm.emulator.display.StyleState;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;

/**
 * Obtains data from the  {@link TerminalDataStream}, interprets terminal ANSI escape sequences as commands and directs them
 * as well as plain data characters to the  {@link Terminal}
 *
 * @author traff
 */

public class JediEmulator extends DataStreamIteratingEmulator {
  private static final Logger LOG = Logger.getLogger(TerminalStarter.class);

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

  @SuppressWarnings("UseJBColor")
  private final static Color[] COLORS = {Color.BLACK, Color.RED, Color.GREEN,
    Color.YELLOW, Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE};

  private final TerminalOutputStream myOutputStream;

  public JediEmulator(TerminalDataStream dataStream, TerminalOutputStream outputStream, Terminal terminal) {
    super(dataStream, terminal);
    myOutputStream = outputStream;
  }

  @Override
  public void processChar(char ch, Terminal terminal) throws IOException {
    switch (ch) {
      case 0:
        break;
      case CharacterUtils.ESC: // ESC
        ch = myDataStream.getChar();
        processEscSequence(ch);
        break;
      case CharacterUtils.BEL:
        terminal.beep();
        break;
      case CharacterUtils.BS:
        terminal.backspace();
        break;
      case Ascii.HT: // ht(^I) TAB
        terminal.horizontalTab();
        break;
      case Ascii.CR:
        terminal.carriageReturn();
        break;
      case Ascii.FF:
      case Ascii.VT:
      case Ascii.LF:
        // '\n'
        terminal.newLine();
        break;
      default:
        if (ch <= CharacterUtils.US) {
          StringBuffer sb = new StringBuffer("Unhandled control character:");
          CharacterUtils.appendChar(sb, CharacterUtils.CharacterType.NONE, ch);
          LOG.error(sb.toString());
        }
        else {
          myDataStream.pushChar(ch);
          String nonControlCharacters = myDataStream.advanceThroughASCII(terminal.distanceToLineEnd());

          terminal.writeASCII(nonControlCharacters);
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
        b = myDataStream.getChar();
      }
      if (b >= 0x30 && b <= 0x7E) {
        synchronized (myTerminal) {
          switch (b) {
            case 'M':
              // Reverse index ESC M
              myTerminal.reverseIndex();
              break;
            case 'D':
              // Index ESC D
              myTerminal.index();
              break;
            case 'E':
              myTerminal.nextLine();
              break;
            case '7':
              saveCursor();
              break;
            case '8':
              if (intCount > 0 && intermediate[0] == '#') {
                myTerminal.fillScreen('E');
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
          myDataStream.pushChar(ib);
        }
        myDataStream.pushChar(b);
      }
    }
  }

  private void saveCursor() {
    myTerminal.storeCursor();
  }

  private void restoreCursor() {
    myTerminal.restoreCursor();
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
    final ControlSequence args = new ControlSequence(myDataStream);

    if (LOG.isDebugEnabled()) {
      StringBuffer sb = new StringBuffer();
      sb.append("Control sequence\n");
      sb.append("parsed                        :");
      args.appendToBuffer(sb);
      LOG.debug(sb.toString());
    }
    if (args.pushBackReordered(myDataStream)) return;

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
        myOutputStream.sendBytes(CharacterUtils.DEVICE_ATTRIBUTES_RESPONSE);
        break;
      default:
        StringBuffer sb = new StringBuffer();
        sb.append("Unhandled Control sequence\n");
        sb.append("parsed                        :");
        args.appendToBuffer(sb);
        sb.append('\n');
        sb.append("bytes read                    :ESC[");
        LOG.error(sb.toString());
        break;
    }
  }

  private void eraseInDisplay(ControlSequence args) {
    // ESC [ Ps J
    final int arg = args.getArg(0, 0);
    myTerminal.eraseInDisplay(arg);
  }

  private void eraseInLine(ControlSequence args) {
    // ESC [ Ps K
    final int arg = args.getArg(0, 0);

    myTerminal.eraseInLine(arg);
  }

  private void cursorBackward(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;

    myTerminal.cursorBackward(dx);
  }

  private void setScrollingRegion(ControlSequence args) {
    final int top = args.getArg(0, 1);
    final int bottom = args.getArg(1, myTerminal.getTerminalHeight());

    myTerminal.setScrollingRegion(top, bottom);
  }

  private void cursorForward(ControlSequence args) {
    int countX = args.getArg(0, 1);
    countX = countX == 0 ? 1 : countX;

    myTerminal.cursorForward(countX);
  }

  private void cursorDown(ControlSequence cs) {
    int countY = cs.getArg(0, 0);
    countY = countY == 0 ? 1 : countY;
    myTerminal.cursorDown(countY);
  }

  private void cursorPosition(ControlSequence cs) {
    final int argy = cs.getArg(0, 1);
    final int argx = cs.getArg(1, 1);

    myTerminal.cursorPosition(argx, argy);
  }

  private void setCharacterAttributes(final ControlSequence args) {
    StyleState styleState = createStyleState(args);

    myTerminal.setCharacterAttributes(styleState);
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
    myTerminal.cursorUp(arg);
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
        myTerminal.setMode(mode);
      }
      else {
        if (LOG.isInfoEnabled()) LOG.info("Modes: removing " + mode);
        myTerminal.unsetMode(mode);
      }
    }
  }
}
