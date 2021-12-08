package com.jediterm.typeahead;

public final class Ascii {

  /**
   * Null ('\0'): The all-zeros character which may serve to accomplish time fill and media fill.
   * Normally used as a C string terminator.
   */
  public static final byte NUL = 0;

  /**
   * Enquiry: A communication control character used in data communication systems as a request for
   * a response from a remote station. It may be used as a "Who Are You" (WRU) to obtain
   * identification, or may be used to obtain station status, or both.
   */
  public static final byte ENQ = 5;

  /**
   * Bell ('\a'): A character for use when there is a need to call for human attention. It may
   * control alarm or attention devices.
   */
  public static final byte BEL = 7;

  /**
   * Backspace ('\b'): A format effector which controls the movement of the printing position one
   * printing space backward on the same printing line. (Applicable also to display devices.)
   */
  public static final byte BS = 8;

  /**
   * Horizontal Tabulation ('\t'): A format effector which controls the movement of the printing
   * position to the next in a series of predetermined positions along the printing line.
   * (Applicable also to display devices and the skip function on punched cards.)
   */
  public static final byte HT = 9;

  /**
   * Line Feed ('\n'): A format effector which controls the movement of the printing position to the
   * next printing line. (Applicable also to display devices.) Where appropriate, this character may
   * have the meaning "New Line" (NL), a format effector which controls the movement of the printing
   * point to the first printing position on the next printing line. Use of this convention requires
   * agreement between sender and recipient of data.
   */
  public static final byte LF = 10;

  /**
   * Vertical Tabulation ('\v'): A format effector which controls the movement of the printing
   * position to the next in a series of predetermined printing lines. (Applicable also to display
   * devices.)
   */
  public static final byte VT = 11;

  /**
   * Form Feed ('\f'): A format effector which controls the movement of the printing position to the
   * first pre-determined printing line on the next form or page. (Applicable also to display
   * devices.)
   */
  public static final byte FF = 12;

  /**
   * Carriage Return ('\r'): A format effector which controls the movement of the printing position
   * to the first printing position on the same printing line. (Applicable also to display devices.)
   */
  public static final byte CR = 13;

  /**
   * Shift Out: A control character indicating that the code combinations which follow shall be
   * interpreted as outside of the character set of the standard code table until a Shift In
   * character is reached.
   */
  public static final byte SO = 14;

  /**
   * Shift In: A control character indicating that the code combinations which follow shall be
   * interpreted according to the standard code table.
   */
  public static final byte SI = 15;

  /**
   * Escape: A control character intended to provide code extension (supplementary characters) in
   * general information interchange. The Escape character itself is a prefix affecting the
   * interpretation of a limited number of contiguously following characters.
   */
  public static final byte ESC = 27;

  /**
   * Unit Separator: These four information separators may be used within data in optional fashion,
   * except that their hierarchical relationship shall be: FS is the most inclusive, then GS, then
   * RS, and US is least inclusive. (The content and length of a File, Group, Record, or Unit are
   * not specified.)
   */
  public static final byte US = 31;

  /**
   * Delete: This character is used primarily to "erase" or "obliterate" erroneous or unwanted
   * characters in perforated tape.
   */
  public static final byte DEL = 127;

  private Ascii() {}
}
