/**
 *
 */
package com.jediterm.terminal;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.WeakHashMap;

public class TextStyle implements Cloneable {
  public static final EnumSet<Option> NO_OPTIONS = EnumSet.noneOf(Option.class);
  private static int COUNT = 1;

  public Color getDefaultForeground() {
    return FOREGROUND;
  }

  public Color getDefaultBackground() {
    return BACKGROUND;
  }

  public TextStyle setBackground(Color background) {
    return new TextStyle(myForeground, background, myOptions);
  }

  public TextStyle setForeground(Color foreground) {
    return new TextStyle(foreground, myBackground, myOptions);
  }

  public TextStyle setOptions(EnumSet<Option> options) {
    return new TextStyle(myForeground, myBackground, options);
  }

  static class ChosenColor extends Color {
    private static final long serialVersionUID = 7492667732033832704L;

    public ChosenColor(final Color def) {
      super(def.getRGB());
    }
  }

  public static final ChosenColor FOREGROUND = new ChosenColor(Color.BLACK);
  public static final ChosenColor BACKGROUND = new ChosenColor(Color.WHITE);

  public enum Option {
    BOLD,
    BLINK,
    DIM,
    REVERSE,
    UNDERLINED,
    HIDDEN;

    public EnumSet<Option> set(EnumSet<Option> options, boolean val) {
      if (val) {
        options.add(this);
      }
      else {
        options.remove(this);
      }
      return options;
    }
  }

  public static final TextStyle EMPTY = new TextStyle();
  private static final WeakHashMap<TextStyle, WeakReference<TextStyle>> styles = new WeakHashMap<TextStyle, WeakReference<TextStyle>>();


  public static TextStyle getCanonicalStyle(TextStyle currentStyle) {
    final WeakReference<TextStyle> canonRef = styles.get(currentStyle);
    if (canonRef != null) {
      final TextStyle canonStyle = canonRef.get();
      if (canonStyle != null) {
        return canonStyle;
      }
    }
    styles.put(currentStyle, new WeakReference<TextStyle>(currentStyle));
    return currentStyle;
  }


  private final Color myForeground;
  private final Color myBackground;
  private EnumSet<Option> myOptions;
  protected int myNumber;

  public TextStyle() {
    this(null, null, NO_OPTIONS);
  }

  public TextStyle(final Color foreground, final Color background) {
    this(foreground, background, NO_OPTIONS);
  }

  public TextStyle(final Color foreground, final Color background, final EnumSet<Option> options) {
    myNumber = COUNT++;
    myForeground = foreground;
    myBackground = background;
    myOptions = options.clone();
  }


  public Color getForeground() {
    return myForeground;
  }

  public Color getBackground() {
    return myBackground;
  }

  public TextStyle setOption(final Option opt, final boolean val) {
    return setOptions(opt.set(EnumSet.copyOf(myOptions), val));
  }

  @Override
  public TextStyle clone() {
    return new TextStyle(myForeground, myBackground, myOptions);
  }

  public int getNumber() {
    return myNumber;
  }

  public boolean hasOption(final Option bold) {
    return myOptions.contains(bold);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = PRIME * result + (myBackground == null ? 0 : myBackground.hashCode());
    result = PRIME * result + (myForeground == null ? 0 : myForeground.hashCode());
    result = PRIME * result + (myOptions == null ? 0 : myOptions.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final TextStyle other = (TextStyle)obj;
    if (myBackground == null) {
      if (other.myBackground != null) {
        return false;
      }
    }
    else if (!myBackground.equals(other.myBackground)) {
      return false;
    }
    if (myForeground == null) {
      if (other.myForeground != null) {
        return false;
      }
    }
    else if (!myForeground.equals(other.myForeground)) {
      return false;
    }
    if (myOptions == null) {
      if (other.myOptions != null) {
        return false;
      }
    }
    else if (!myOptions.equals(other.myOptions)) {
      return false;
    }
    return true;
  }

  public Color getBackgroundForRun() {
    return myOptions.contains(Option.REVERSE) ? myForeground : myBackground;
  }

  public Color getForegroundForRun() {
    return myOptions.contains(Option.REVERSE) ? myBackground : myForeground;
  }

  public void clearOptions() {
    myOptions.clear();
  }
}