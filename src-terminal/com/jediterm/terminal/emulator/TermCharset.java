package com.jediterm.terminal.emulator;

/**
 * @author traff
 */
public enum TermCharset {
  //TODO: implement symbols decoding
  SpecialCharacters {
    @Override
    public char decode(char ch) {
      return ch;
    }
  },
  UK {
    @Override
    public char decode(char ch) {
      return ch;
    }
  },
  USASCII {
    @Override
    public char decode(char ch) {
      return ch;
    }
  };
  //,Dutch,
  //Finnish,
  //French,
  //FrenchCanadian,
  //German,
  //Italian,
  //Norwegian,
  //Spanish,
  //Swedish,
  //Swiss;

  abstract public char decode(char ch);
}
