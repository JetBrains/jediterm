package com.jediterm.terminal.emulator;

import com.google.common.base.Ascii;
import com.jediterm.terminal.*;
import com.jediterm.terminal.display.StyleState;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * The main terminal emulator class.
 * <p/>
 * Obtains data from the  {@link com.jediterm.terminal.TerminalDataStream}, interprets terminal ANSI escape sequences as commands and directs them
 * as well as plain data characters to the  {@link com.jediterm.terminal.Terminal}
 *
 * @author traff
 */

public class JediEmulator extends DataStreamIteratingEmulator {
  private static final Logger LOG = Logger.getLogger(JediEmulator.class);

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
      case Ascii.VT: //Vertical Tab (Ctrl-K). This is treated the same as LF.
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
      case Ascii.ESC: // ESC
        processEscapeSequence(myDataStream.getChar(), myTerminal);
        break;
      default:
        if (ch <= Ascii.US) {
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
        final ControlSequence args = new ControlSequence(myDataStream);

        if (LOG.isDebugEnabled()) {
          LOG.debug(args.appendTo("Control sequence\nparsed                        :"));
        }
        if (!args.pushBackReordered(myDataStream)) {
          boolean result = processControlSequence(args);

          if (!result) {
            StringBuilder sb = new StringBuilder();
            sb.append("Unhandled Control sequence\n");
            sb.append("parsed                        :");
            args.appendToBuffer(sb);
            sb.append('\n');
            sb.append("bytes read                    :ESC[");
            LOG.error(sb.toString());
          }
        }
        break;
      case 'D': //Index (IND)
        terminal.index();
        break;
      case 'E': //Next Line (NEL)
        terminal.nextLine();
        break;
      case 'H': //Horizontal Tab Set (HTS)
        terminal.setTabStopAtCursor();
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
        final SystemCommandSequence command = new SystemCommandSequence(myDataStream);

        if (!operatingSystemCommand(command)) {
          LOG.error("Error processing OSC " + command.getSequenceString());
        }
        break;
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
      case '=': //Application Keypad (DECKPAM)
        setModeEnabled(TerminalMode.Keypad, true);
        break;
      case '>': //Normal Keypad (DECKPNM)
        setModeEnabled(TerminalMode.Keypad, false);
        break;
      case 'F': //Cursor to lower left corner of the screen
        terminal.cursorPosition(1, terminal.getTerminalHeight());
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
          case 'N': //Set ANSI conformance level 3
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

  private boolean processControlSequence(ControlSequence args) {
    switch (args.getFinalChar()) {
      case '@':
        return insertBlankCharacters(args); //ICH
      case 'A':
        return cursorUp(args); //CUU
      case 'B':
        return cursorDown(args); //CUD
      case 'C':
        return cursorForward(args); //CUF
      case 'D':
        return cursorBackward(args); //CUB
      case 'E':
        return cursorNextLine(args); //CNL
      case 'F':
        return cursorPrecedingLine(args); //CPL
      case 'G':
      case '`':
        return cursorHorizontalAbsolute(args); //CHA
      case 'f':
      case 'H': //CUP
        return cursorPosition(args);
      case 'J': //DECSED
        return eraseInDisplay(args);
      case 'K': //EL
        return eraseInLine(args);
      case 'L': //IL
        return insertLines(args);
      case 'M': //DL
        return deleteLines(args);
      case 'X': //ECH
        return eraseCharacters(args);
      case 'P': //DCH
        return deleteCharacters(args);
      case 'c': //Send Device Attributes (Primary DA)
        if (args.startsWithMoreMark()) { //Send Device Attributes (Secondary DA)
          if (args.getArg(0, 0) == 0) { //apply on to VT220 but xterm extends this to VT100
            sendDeviceAttributes();
            return true;
          }
          return false;
        }
        return sendDeviceAttributes();
      case 'd': //VPA
        return linePositionAbsolute(args);
      case 'g': // Tab Clear (TBC)
        return tabClear(args.getArg(0, 0));
      case 'h': //Set Mode (SM) or DEC Private Mode Set (DECSET)
        return setModeOrPrivateMode(args, true);
      case 'l': //Reset Mode (RM) or DEC Private Mode Reset (DECRST)
        return setModeOrPrivateMode(args, false);
      case 'm':
        if (args.startsWithMoreMark()) { //Set or reset resource-values used by xterm 
          // to decide whether to construct escape sequences holding information about 
          // the modifiers pressed with a given key
          return false;
        }
        return characterAttributes(args); //Character Attributes (SGR)
      case 'n':
        return deviceStatusReport(args); //DSR
      case 'r':
        if (args.startsWithQuestionMark()) {
          return restoreDecPrivateModeValues(args); //
        }
        else {
          return setScrollingRegion(args);
        }
      default:
        return false;
    }
  }

  private boolean tabClear(int mode) {
    if (mode == 0) { //Clear Current Column (default)
      myTerminal.clearTabStopAtCursor();
      return true;
    } else 
    if (mode == 3) {
      myTerminal.clearAllTabStops();
      return true;
    } else {
      return false;
    }
  }

  private boolean eraseCharacters(ControlSequence args) {
    myTerminal.eraseCharacters(args.getArg(0, 1));
    return true;
  }

  private boolean setModeOrPrivateMode(ControlSequence args, boolean enabled) {
    if (args.startsWithQuestionMark()) { // DEC Private Mode
      switch (args.getArg(0, -1)) {
        case 1: //Cursor Keys Mode (DECCKM)
          setModeEnabled(TerminalMode.CursorKey, enabled);
          return true;
        case 3: //132 Column Mode (DECCOLM)
          setModeEnabled(TerminalMode.WideColumn, enabled);
          return true;
        case 4: //Smooth (Slow) Scroll (DECSCLM)
          setModeEnabled(TerminalMode.SmoothScroll, enabled);
          return true;
        case 5: //Reverse Video (DECSCNM)
          setModeEnabled(TerminalMode.ReverseVideo, enabled);
          return true;
        case 6: //Origin Mode (DECOM)
          setModeEnabled(TerminalMode.OriginMode, enabled);
          return true;
        case 7: //Wraparound Mode (DECAWM)
          setModeEnabled(TerminalMode.WrapAround, enabled);
          return true;
        case 8: //Auto-repeat Keys (DECARM)
          setModeEnabled(TerminalMode.AutoRepeatKeys, enabled);
          return true;
        case 12: //Start Blinking Cursor (att610)
          //setModeEnabled(TerminalMode.CursorBlinking, enabled);
          //We want to show blinking cursor always
          return true; 
        case 25:
          setModeEnabled(TerminalMode.CursorVisible, enabled);
          return true;
        case 40: //Allow 80->132 Mode
          setModeEnabled(TerminalMode.AllowWideColumn, enabled);
          return true;
        case 45: //Reverse-wraparound Mode
          setModeEnabled(TerminalMode.ReverseWrapAround, enabled);
          return true;
        case 47:
        case 1047:
          setModeEnabled(TerminalMode.AlternateBuffer, enabled);
          return true;
        case 1048:
          setModeEnabled(TerminalMode.StoreCursor, enabled);
          return true;
        case 1049: //Save cursor and use Alternate Screen Buffer
          setModeEnabled(TerminalMode.StoreCursor, enabled);
          setModeEnabled(TerminalMode.AlternateBuffer, enabled);
          return true;
        default:
          return false;
      }
    }
    else {
      switch (args.getArg(0, -1)) {
        case 2: //Keyboard Action Mode (AM)
          setModeEnabled(TerminalMode.KeyboardAction, enabled);
          return true;
        case 4: //Insert Mode (IRM)
          setModeEnabled(TerminalMode.InsertMode, enabled);
          return true;
        case 12: //Send/receive (SRM)
          setModeEnabled(TerminalMode.SendReceive, enabled);
          return true;
        case 20:
          setModeEnabled(TerminalMode.AutoNewLine, enabled);
          return true;
        default:
          return false;
      }
    }
  }

  private boolean linePositionAbsolute(ControlSequence args) {
    int y = args.getArg(0, 1);
    myTerminal.linePositionAbsolute(y);

    return true;
  }

  private boolean restoreDecPrivateModeValues(ControlSequence args) {
    LOG.error("Unsupported: " + args.toString());

    return false;
  }

  private boolean deviceStatusReport(ControlSequence args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending Device Report Status");
    }

    if (args.startsWithQuestionMark()) {
      LOG.error("Don't support DEC-specific Device Report Status");

      return false;
    }
    int c = args.getArg(0, 0);
    if (c == 5) {
      myOutputStream.sendString(Ascii.ESC + "[0n");
      return true;
    }
    else if (c == 6) {
      int row = myTerminal.getCursorY();
      int column = myTerminal.getCursorX();
      myOutputStream.sendString(Ascii.ESC + "[" + row + ";" + column + "R");
      return true;
    }
    else {
      LOG.error("Unsupported parameter: " + args.toString());
      return false;
    }
  }

  private boolean insertLines(ControlSequence args) {
    myTerminal.insertLines(args.getArg(0, 1));
    return true;
  }

  private boolean sendDeviceAttributes() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Identifying to remote system as VT102");
    }
    myOutputStream.sendBytes(CharacterUtils.VT102_RESPONSE);

    return true;
  }

  private boolean cursorHorizontalAbsolute(ControlSequence args) {
    int x = args.getArg(0, 1);

    myTerminal.cursorHorizontalAbsolute(x);

    return true;
  }

  private boolean cursorNextLine(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;
    myTerminal.cursorDown(dx);
    myTerminal.cursorHorizontalAbsolute(1);

    return true;
  }

  private boolean cursorPrecedingLine(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;
    myTerminal.cursorUp(dx);

    myTerminal.cursorHorizontalAbsolute(1);

    return true;
  }

  private boolean insertBlankCharacters(ControlSequence args) {
    final int arg = args.getArg(0, 1);
    char[] chars = new char[arg];
    Arrays.fill(chars, ' ');

    myTerminal.writeCharacters(chars, 0, arg);

    return true;
  }

  private boolean eraseInDisplay(ControlSequence args) {
    // ESC [ Ps J
    final int arg = args.getArg(0, 0);

    if (args.startsWithQuestionMark()) {
      //TODO: support ESC [ ? Ps J - Selective Erase (DECSED)
      return false;
    }

    myTerminal.eraseInDisplay(arg);

    return true;
  }

  private boolean eraseInLine(ControlSequence args) {
    // ESC [ Ps K
    final int arg = args.getArg(0, 0);

    if (args.startsWithQuestionMark()) {
      //TODO: support ESC [ ? Ps K - Selective Erase (DECSEL)
      return false;
    }

    myTerminal.eraseInLine(arg);

    return true;
  }

  private boolean deleteLines(ControlSequence args) {
    // ESC [ Ps M
    myTerminal.deleteLines(args.getArg(0, 1));
    return true;
  }

  private boolean deleteCharacters(ControlSequence args) {
    // ESC [ Ps P
    final int arg = args.getArg(0, 1);

    myTerminal.deleteCharacters(arg);

    return true;
  }

  private boolean cursorBackward(ControlSequence args) {
    int dx = args.getArg(0, 1);
    dx = dx == 0 ? 1 : dx;

    myTerminal.cursorBackward(dx);

    return true;
  }

  private boolean setScrollingRegion(ControlSequence args) {
    final int top = args.getArg(0, 1);
    final int bottom = args.getArg(1, myTerminal.getTerminalHeight());

    myTerminal.setScrollingRegion(top, bottom);

    return true;
  }

  private boolean cursorForward(ControlSequence args) {
    int countX = args.getArg(0, 1);
    countX = countX == 0 ? 1 : countX;

    myTerminal.cursorForward(countX);

    return true;
  }

  private boolean cursorDown(ControlSequence cs) {
    int countY = cs.getArg(0, 0);
    countY = countY == 0 ? 1 : countY;
    myTerminal.cursorDown(countY);
    return true;
  }

  private boolean cursorPosition(ControlSequence cs) {
    final int argy = cs.getArg(0, 1);
    final int argx = cs.getArg(1, 1);

    myTerminal.cursorPosition(argx, argy);

    return true;
  }

  private boolean characterAttributes(final ControlSequence args) {
    StyleState styleState = createStyleState(myTerminal.getStyleState(), args);

    myTerminal.characterAttributes(styleState);

    return true;
  }

  private static StyleState createStyleState(StyleState state, ControlSequence args) {
    StyleState styleState = state.clone();

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
          styleState.setOption(TextStyle.Option.INVERSE, true);
          break;
        case 8: // Invisible (hidden)  
          styleState.setOption(TextStyle.Option.HIDDEN, true);
          break;
        case 22: //Normal (neither bold nor faint)
          styleState.setOption(TextStyle.Option.BOLD, false);
          styleState.setOption(TextStyle.Option.DIM, false);
          break;
        case 24: // Not underlined
          styleState.setOption(TextStyle.Option.UNDERLINED, false);
          break;
        case 25: //Steady (not blinking)
          styleState.setOption(TextStyle.Option.BLINK, false);
          break;
        case 27: //Positive (not inverse)
          styleState.setOption(TextStyle.Option.INVERSE, false);
          break;
        case 28: //Visible, i.e. not hidden
          styleState.setOption(TextStyle.Option.HIDDEN, false);
          break;
        case 30:
        case 31:
        case 32:
        case 33:
        case 34:
        case 35:
        case 36:
        case 37:
          styleState.setCurrentForeground(ColorPalette.getCurrentColorSettings()[arg - 30]);
          break;
        case 38: // Set xterm-256 text color
          Color color256 = getColor256(args);
          if (color256 != null) {
            styleState.setCurrentForeground(color256);
          }
          break;
        case 39: // Default (original) foreground
          styleState.setCurrentForeground(null);
          break;
        case 40:
        case 41:
        case 42:
        case 43:
        case 44:
        case 45:
        case 46:
        case 47:
          styleState.setCurrentBackground(ColorPalette.getCurrentColorSettings()[arg - 40]);
          break;
        case 48: // Set xterm-256 background color
          Color bgColor256 = getColor256(args);
          if (bgColor256 != null) {
            styleState.setCurrentBackground(bgColor256);
          }
          break;
        case 49: //Default (original) foreground
          styleState.setCurrentBackground(null);
          break;
        case 90:
        case 91:
        case 92:
        case 93:
        case 94:
        case 95:
        case 96:
        case 97:
          //Bright versions of the ISO colors for foreground
          styleState.setCurrentForeground(ColorPalette.getIndexedColor(arg - 82));
          break;
        case 100:
        case 101:
        case 102:
        case 103:
        case 104:
        case 105:
        case 106:
        case 107:
          //Bright versions of the ISO colors for background
          styleState.setCurrentBackground(ColorPalette.getIndexedColor(arg - 92));
          break;
        default:
          LOG.error("Unknown character attribute:" + arg);
      }
    }
    return styleState;
  }

  private static Color getColor256(ControlSequence args) {
    int code = args.getArg(1, 0);

    if (code == 2) {
      /* direct color in rgb space */
      int val0 = args.getArg(2, -1);
      int val1 = args.getArg(3, -1);
      int val2 = args.getArg(4, -1);
      if ((val0 >= 0 && val0 < 256) &&
          (val1 >= 0 && val1 < 256) &&
          (val2 >= 0 && val2 < 256)) {
        return new Color(val0, val1, val2);
      }
      else {
        LOG.error("Bogus color setting " + args.toString());
        return null;
      }
    }
    else if (code == 5) {
      /* indexed color */
      return ColorPalette.getIndexedColor(args.getArg(2, 0));
    }
    else {
      LOG.error("Unsupported code for color attribute " + args.toString());
      return null;
    }
  }

  private boolean cursorUp(ControlSequence cs) {
    int arg = cs.getArg(0, 0);
    arg = arg == 0 ? 1 : arg;
    myTerminal.cursorUp(arg);
    return true;
  }

  private void setModeEnabled(final TerminalMode mode, final boolean enabled) {
    if (LOG.isDebugEnabled()) {
      LOG.info("Setting mode " + mode + " enabled = " + enabled);
    }
    myTerminal.setModeEnabled(mode, enabled);
  }
}

