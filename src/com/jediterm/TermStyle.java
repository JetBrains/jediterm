/**
 *
 */
package com.jediterm;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.WeakHashMap;

public class TermStyle implements Cloneable {
  public static final EnumSet<Option> NO_OPTIONS = EnumSet.noneOf(Option.class);
  private static int COUNT = 1;

  public Color getDefaultForeground() {
    return FOREGROUND;
  }

  public Color getDefaultBackground() {
    return BACKGROUND;
  }

  public TermStyle setBackground(Color background) {
    return new TermStyle(myForeground, background, myOptions);
  }

  public TermStyle setForeground(Color foreground) {
    return new TermStyle(foreground, myBackground, myOptions);
  }

  public TermStyle setOptions(EnumSet<Option> options) {
    return new TermStyle(myForeground, myBackground, options);
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
    UNDERSCORE,
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

  public static final TermStyle EMPTY = new TermStyle();
  private static final WeakHashMap<TermStyle, WeakReference<TermStyle>> styles = new WeakHashMap<TermStyle, WeakReference<TermStyle>>();


  public static TermStyle getCanonicalStyle(TermStyle currentStyle) {
    final WeakReference<TermStyle> canonRef = styles.get(currentStyle);
    if (canonRef != null) {
      final TermStyle canonStyle = canonRef.get();
      if (canonStyle != null) {
        return canonStyle;
      }
    }
    styles.put(currentStyle, new WeakReference<TermStyle>(currentStyle));
    return currentStyle;
  }


  private final Color myForeground;
  private final Color myBackground;
  private EnumSet<Option> myOptions;
  private int number;

  public TermStyle() {
    this(null, null, NO_OPTIONS);
  }

  public TermStyle(final Color foreground, final Color background) {
    this(foreground, background, NO_OPTIONS);
  }

  public TermStyle(final Color foreground, final Color background, final EnumSet<Option> options) {
    number = COUNT++;
    this.myForeground = foreground;
    this.myBackground = background;
    this.myOptions = options.clone();
  }


  public Color getForeground() {
    return myForeground;
  }

  public Color getBackground() {
    return myBackground;
  }

  public TermStyle setOption(final Option opt, final boolean val) {
    return setOptions(opt.set(EnumSet.copyOf(myOptions), val));
  }

  @Override
  public TermStyle clone() {
    return new TermStyle(myForeground, myBackground, myOptions);
  }

  public int getNumber() {
    return number;
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
    final TermStyle other = (TermStyle)obj;
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