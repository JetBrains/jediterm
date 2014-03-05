package com.jediterm.terminal;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.WeakHashMap;

public class TextStyle implements Cloneable {
  public static final EnumSet<Option> NO_OPTIONS = EnumSet.noneOf(Option.class);

  public static final TextStyle EMPTY = new TextStyle();

  private static final WeakHashMap<TextStyle, WeakReference<TextStyle>> styles = new WeakHashMap<TextStyle, WeakReference<TextStyle>>();

  private TerminalColor myForeground;
  private TerminalColor myBackground;
  private EnumSet<Option> myOptions;

  public TextStyle() {
    this(null, null, NO_OPTIONS);
  }

  public TextStyle(final TerminalColor foreground, final TerminalColor background) {
    this(foreground, background, NO_OPTIONS);
  }

  public TextStyle(final TerminalColor foreground, final TerminalColor background, final EnumSet<Option> options) {
    myForeground = foreground;
    myBackground = background;
    myOptions = options.clone();
  }

  public void setBackground(TerminalColor background) {
    myBackground = background;
  }

  public void setForeground(TerminalColor foreground) {
    myForeground = foreground;
  }

  public void setOptions(EnumSet<Option> options) {
    myOptions = options;
  }

  public void setOption(final Option opt, final boolean val) {
    setOptions(opt.set(EnumSet.copyOf(myOptions), val));
  }

  public TextStyle readonlyCopy() {
    return new TextStyle(myForeground, myBackground, myOptions) {
      private TextStyle readonly() {
        throw new IllegalStateException("Text Style is readonly");
      }

      @Override
      public void setBackground(TerminalColor background) {
        readonly();
      }

      @Override
      public void setForeground(TerminalColor foreground) {
        readonly();
      }

      @Override
      public void setOptions(EnumSet<Option> options) {
        readonly();
      }
    };
  }


  @NotNull
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


  public TerminalColor getForeground() {
    return myForeground;
  }

  public TerminalColor getBackground() {
    return myBackground;
  }

  @Override
  public TextStyle clone() {
    return new TextStyle(myForeground, myBackground, myOptions);
  }
  
  public TextStyle createEmptyWithColors() {
    return new TextStyle(myForeground, myBackground);
  }

  public int getId() {
    return hashCode();
  }

  public boolean hasOption(final Option option) {
    return myOptions.contains(option);
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

  public TerminalColor getBackgroundForRun() {
    return myOptions.contains(Option.INVERSE) ? myForeground : myBackground;
  }

  public TerminalColor getForegroundForRun() {
    return myOptions.contains(Option.INVERSE) ? myBackground : myForeground;
  }

  public void clearOptions() {
    myOptions.clear();
  }

  public enum Option {
    BOLD,
    ITALIC,
    BLINK,
    DIM,
    INVERSE,
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
}