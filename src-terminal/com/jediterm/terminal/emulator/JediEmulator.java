package com.jediterm.terminal.emulator;

import com.google.common.base.Ascii;
import com.jediterm.terminal.*;
import com.jediterm.terminal.display.StyleState;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;

/**
 * Obtains data from the  {@link com.jediterm.terminal.TerminalDataStream}, interprets terminal ANSI escape sequences as commands and directs them
 * as well as plain data characters to the  {@link com.jediterm.terminal.Terminal}
 *
 * @author traff
 */

public class JediEmulator extends DataStreamIteratingEmulator {
  private static final Logger LOG = Logger.getLogger(JediEmulator.class);

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
      case Ascii.BEL: //Bell (Ctrl-G)
        terminal.beep();
        break;
      case Ascii.BS: //Backspace (Ctrl-H)
        terminal.backspace();
        break;
      case Ascii.CR: //Carriage return (Ctrl-M)
        terminal.carriageReturn();
        break;
      case Ascii.ENQ: //Return terminal status (Ctrl-E). Default response is an empty string
        unsupported("Terminal status:" + escapeSequenceToString(ch));
        break;
      case Ascii.FF: //Form Feed or New Page (NP). Ctrl-L treated the same as LF
      case Ascii.LF: //Line Feed or New Line (NL). (LF is Ctrl-J)
      case Ascii.VT: //Vertical Tab (Ctrl-K). This is treated the sane as LF.
        // '\n'
        terminal.newLine();
        break;
      case Ascii.SI: //Shift In (Ctrl-O) -> Switch to Standard Character Set. This invokes the G0 character set (the default)
        terminal.invokeCharacterSet(0);
        break;
      case Ascii.SO: //Shift Out (Ctrl-N) -> Switch to Alternate Character Set. This invokes the G1 character set (the default)
        terminal.invokeCharacterSet(1);
        break;
      case Ascii.HT: // Horizontal Tab (HT) (Ctrl-I)
        terminal.horizontalTab();
        break;
      case CharacterUtils.ESC: // ESC
        ch = myDataStream.getChar();
        processEscapeSequence(ch, myTerminal);
        break;
      default:
        if (ch <= CharacterUtils.US) {
          StringBuilder sb = new StringBuilder("Unhandled control character:");
          CharacterUtils.appendChar(sb, CharacterUtils.CharacterType.NONE, ch);
          LOG.error(sb.toString());
        }
        else { // Plain characters
          myDataStream.pushChar(ch);
          String nonControlCharacters = myDataStream.readNonControlCharacters(terminal.distanceToLineEnd());

          terminal.writeCharacters(nonControlCharacters);
        }
        break;
    }
  }

  private void processEscapeSequence(char ch, Terminal terminal) throws IOException {
    switch (ch) {
      case '[': // Control Sequence Introducer (CSI)
        processControlSequence();
        break;
      case 'D': // Index (IND)
        terminal.index();
        break;
      case 'E': // Next Line (NEL)
        terminal.nextLine();
        break;
      case 'H':
        unsupported("Horizontal Tab Set (HTS)");
        break;
      case 'M': // Reverse Index (RI) 
        terminal.reverseIndex();
        break;
      case 'N':
        terminal.singleShiftSelect(2); //Single Shift Select of G2 Character Set (SS2). This affects next character only.
        break;
      case 'O':
        terminal.singleShiftSelect(3); //Single Shift Select of G3 Character Set (SS3). This affects next character only.
        break;
      case ']': // Operating System Command (OSC)
        // xterm uses it to set parameters like windows title
        final SystemCommandSequence args = new SystemCommandSequence(myDataStream);

        if (!operatingSystemCommand(args)) {
          LOG.error("Error processing OSC " + args.getSequenceString());
        }

      case '6':
        unsupported("Back Index (DECBI), VT420 and up");
        break;
      case '7': //Save Cursor (DECSC)
        terminal.storeCursor();
        break;
      case '8':
        terminal.restoreCursor();
        break;
      case '9':
        unsupported("Forward Index (DECFI), VT420 and up");
        break;
      case '=':
        unsupported("Application Keypad (DECKPAM)");
        break;
      case '>':
        unsupported("Normal Keypad (DECKPNM)");
        break;
      case 'F':
        unsupported("Cursor to lower left corner of the screen");
        break;
      case 'c': //Full Reset (RIS)
        terminal.reset();
        break;
      case 'l':
      case 'm':
      case 'n':
      case 'o':
      case '|':
      case '}':
      case '~':
        unsupported(escapeSequenceToString(ch));
        break;
      case '#':
      case '(':
      case ')':
      case '*':
      case '+':
      case '$':
      case '@':
      case '%':
      case '.':
      case '/':
      case ' ':
        processTwoCharSequence(ch, terminal);
        break;
      default:
        unsupported(ch);
    }
  }

  private boolean operatingSystemCommand(SystemCommandSequence args) {
    Integer i = args.getIntAt(0);

    if (i != null) {
      switch (i) {
        case 0: //Icon name/title
        case 2: //Title
          String name = args.getStringAt(1);
          if (name != null) {
            myTerminal.setWindowTitle(name);
            return true;
          }
      }
    }

    return false;
  }

  private void processTwoCharSequence(char ch, Terminal terminal) throws IOException {
    char secondCh = myDataStream.getChar();
    switch (ch) {
      case ' ':
        switch (secondCh) {
          //About different character sets: http://en.wikipedia.org/wiki/ISO/IEC_2022
          case 'F': //7-bit controls
            unsupported("Switching ot 7-bit");
            break;
          case 'G': //8-bit controls
            unsupported("Switching ot 8-bit");
            break;
          //About ANSI conformance levels: http://www.vt100.net/docs/vt510-rm/ANSI
          case 'L': //Set ANSI conformance level 1
          case 'M': //Set ANSI conformance level 2
          case 'N': //Set ANSI conformance level 2
            unsupported("Settings conformance level: " + escapeSequenceToString(ch, secondCh));
            break;

          default:
            unsupported(ch, secondCh);
        }
        break;
      case '#':
        switch (secondCh) {
          case '8':
            terminal.fillScreen('E');
            break;
          default:
            unsupported(ch, secondCh);
        }
        break;
      case '%':
        switch (secondCh) {
          case '@': // Select default character set. That is ISO 8859-1 
          case 'G': // Select UTF-8 character set.
            unsupported("Selecting charset is unsupported: " + escapeSequenceToString(ch, secondCh));
            break;
          default:
            unsupported(ch, secondCh);
        }
        break;
      case '(':
        terminal.designateCharacterSet(0, parseCharacterSet(secondCh)); //Designate G0 Character set (VT100)
        break;
      case ')':
        terminal.designateCharacterSet(1, parseCharacterSet(secondCh)); //Designate G1 Character set (VT100)
        break;
      case '*':
        terminal.designateCharacterSet(2, parseCharacterSet(secondCh)); //Designate G2 Character set (VT220)
        break;
      case '+':
        terminal.designateCharacterSet(3, parseCharacterSet(secondCh)); //Designate G3 Character set (VT220)
        break;
      case '-':
        terminal.designateCharacterSet(1, parseCharacterSet(secondCh)); //Designate G1 Character set (VT300)
        break;
      case '.':
        terminal.designateCharacterSet(2, parseCharacterSet(secondCh)); //Designate G2 Character set (VT300)
        break;
      case '/':
        terminal.designateCharacterSet(3, parseCharacterSet(secondCh)); //Designate G3 Character set (VT300)
        break;
      case '$':
      case '@':
        unsupported(ch, secondCh);
    }
  }

  private static TermCharset parseCharacterSet(char ch) {
    switch (ch) {
      case '0':
        return TermCharset.SpecialCharacters;
      case 'A':
        return TermCharset.UK;
      case 'B':
        return TermCharset.USASCII;

      // Other character sets apply to VT220 and up
      default:
        return TermCharset.USASCII;
    }
  }

  private static void unsupported(char... b) {
    unsupported(escapeSequenceToString(b));
  }

  private static void unsupported(String msg) {
    LOG.error("Unsupported control characters: " + msg);
  }

  private static String escapeSequenceToString(final char... b) {
    StringBuilder sb = new StringBuilder("ESC ");

    for (char c : b) {
      sb.append(' ');
      sb.append(c);
    }
    return sb.toString();
  }

  private void processControlSequence() throws IOException {
    final ControlSequence args = new ControlSequence(myDataStream);

    if (LOG.isDebugEnabled()) {
      LOG.debug(args.appendTo("Control sequence\nparsed                        :"));
    }
    if (args.pushBackReordered(myDataStream)) {
      return; //when there are unhandled chars in stream
    }

    switch (args.getFinalChar()) {
      case '@':
        insertBlankCharacters(args); // ICH
        break;
      case 'A':
        cursorUp(args); //CUU
        break;
      case 'B':
        cursorDown(args); //CUD
        break;
      case 'C':
        cursorForward(args); //CUF
        break;
      case 'D':
        cursorBackward(args); //CUB
        break;
      case 'E':
        cursorNextLine(args); //CNL
      case 'F':
        cursorPrecedingLine(args); //CPL
      case 'G':
      case '`':
        cursorHorizontalAbsolute(args); //CHA
      case 'f':
      case 'H': // CUP
        cursorPosition(args);
        break;
      case 'J': // DECSED
        eraseInDisplay(args);
        break;
      case 'K': //EL
        eraseInLine(args);
        break;
      case 'L': //IL
        insertLines(args);
        break;


      case 'c': //Send Device Attributes (Primary DA)
        sendDeviceAttributes();
        break;
      case 'd': // VPA
        linePositionAbsolute(args);
        break;
      case 'h':
        //setModeEnabled(args, true);
        break;
      case 'm': //Character Attributes (SGR)
        String mySequence = args.getSequenceString();
        String midSequence = mySequence.substring(0, mySequence.length() - 1);
        
        characterAttributes(args);
        break;
      case 'n':
        deviceStatusReport(args); // DSR
        break;
      case 'r':
        if (args.startsWithQuestionMark()) {
          restoreDecPrivateModeValues(args); //
        }
        else {
          setScrollingRegion(args);
        }
        break;
      case 'l':
        //setModeEnabled(args, false);
        break;

      default:
        StringBuilder sb = new StringBuilder();
        sb.append("Unhandled Control sequence\n");
        sb.append("parsed                        :");
        args.appendToBuffer(sb);
        sb.append('\n');
        sb.append("bytes read                    :ESC[");
        LOG.error(sb.toString());
        break;
    }
  }

  private void linePositionAbsolute(ControlSequence args) {
    int y = args.getArg(0, 1);
    myTerminal.linePositionAbsolute(y);
  }

  private void restoreDecPrivateModeValues(ControlSequence args) {
    LOG.error("Unsupported: " + args.toString());
  }

  private void deviceStatusReport(ControlSequence args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending Device Report Status");
    }
    if (args.startsWithQuestionMark()) {
      LOG.error("Don't support DEC-specific Device Report Status");
    }
    int c = args.getArg(0, 0);
    if (c == 5) {
      myOutputStream.sendString(Ascii.ESC + "[0n");
    }
    else if (c == 6) {
      int row = myTerminal.getCursorY();
      int column = myTerminal.getCursorX();
      myOutputStream.sendString(Ascii.ESC + "[" + row + ";" + column + "R");
    }
    else {
      LOG.error("Unsupported parameter: " + args.toString());
    }
  }

  private void insertLines(ControlSequence args) {
    //TODO: implement
  }

  private void sendDeviceAttributes() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Identifying to remote system as VT102");
    }
    myOutputStream.sendBytes(CharacterUtils.VT102_RESPONSE);
  }

  private void cursorHorizontalAbsolute(ControlSequence args) {
    int x = args.getArg(0, 1);

    myTerminal.cursorHorizontalAbsolute(x);
  }

  private void cursorNextLine(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;
    myTerminal.cursorDown(dx);
    myTerminal.cursorHorizontalAbsolute(1);
  }

  private void cursorPrecedingLine(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;
    myTerminal.cursorUp(dx);

    myTerminal.cursorHorizontalAbsolute(1);
  }

  private void insertBlankCharacters(ControlSequence args) {
    final int arg = args.getArg(0, 1);
    char[] chars = new char[arg];
    Arrays.fill(chars, ' ');

    myTerminal.writeCharacters(chars, 0, arg);
  }

  private void eraseInDisplay(ControlSequence args) {
    // ESC [ Ps J
    final int arg = args.getArg(0, 0);

    if (args.startsWithQuestionMark()) {
      //TODO: support ESC [ ? Ps J - Selective Erase (DECSED)
    }

    myTerminal.eraseInDisplay(arg);
  }

  private void eraseInLine(ControlSequence args) {
    // ESC [ Ps K
    final int arg = args.getArg(0, 0);

    if (args.startsWithQuestionMark()) {
      //TODO: support ESC [ ? Ps K - Selective Erase (DECSEL)
    }

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

  private void characterAttributes(final ControlSequence args) {
    StyleState styleState = createStyleState(args);

    myTerminal.characterAttributes(styleState);
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
        case 0: //Normal (default)
          styleState.reset();
          break;
        case 1:// Bold
          styleState.setOption(TextStyle.Option.BOLD, true);
          break;
        case 2:// Dim
          styleState.setOption(TextStyle.Option.DIM, true);
          break;
        case 4:// Underlined
          styleState.setOption(TextStyle.Option.UNDERLINED, true);
          break;
        case 5:// Blink (appears as Bold) 
          styleState.setOption(TextStyle.Option.BLINK, true);
          break;
        case 7:// Inverse 
          styleState.setOption(TextStyle.Option.REVERSE, true);
          break;
        case 8: // Invisible (hidden)  
          styleState.setOption(TextStyle.Option.HIDDEN, true);
          break;
        default:
          if (arg >= 30 && arg <= 37) {
            styleState.setCurrentForeground(ColorPalette.getCurrentColorSettings()[arg - 30]);
          }
          else if (arg >= 40 && arg <= 47) {
            styleState.setCurrentBackground(ColorPalette.getCurrentColorSettings()[arg - 40]);
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

  private void setModeEnabled(final TerminalMode mode, final boolean on) throws IOException {
    if (on) {
      if (LOG.isInfoEnabled()) LOG.info("Modes: adding " + mode);
      myTerminal.setMode(mode);
    }
    else {
      if (LOG.isInfoEnabled()) LOG.info("Modes: removing " + mode);
      myTerminal.unsetMode(mode);
    }
  }
}
