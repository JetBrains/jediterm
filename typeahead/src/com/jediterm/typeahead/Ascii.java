package com.jediterm.typeahead;

public final class Ascii {

  /**
   * Escape: A control character intended to provide code extension (supplementary characters) in
   * general information interchange. The Escape character itself is a prefix affecting the
   * interpretation of a limited number of contiguously following characters.
   */
  public static final byte ESC = 27;

  /**
   * Delete: This character is used primarily to "erase" or "obliterate" erroneous or unwanted
   * characters in perforated tape.
   */
  public static final byte DEL = 127;

  private Ascii() {}
}
