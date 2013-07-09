package com.jediterm.terminal.display;

import com.jediterm.terminal.StyledTextConsumer;
import com.jediterm.terminal.TextStyle;

import java.awt.*;

/**
 * @author traff
 */
public class TerminalSelection {
  private final Point myStart;

  private Point myEnd;

  public TerminalSelection(Point start) {
    myStart = start;
  }


  public Point getStart() {
    return myStart;
  }

  public Point getEnd() {
    return myEnd;
  }

  public void updateEnd(Point end, int width) {
    myEnd = end;
    myEnd.x = Math.min(myEnd.x + 1, width);
  }

  public void process(int x, int y, TextStyle style, CharBuffer buf, StyledTextConsumer consumer) {
    Point top;
    Point bottom;
    if (myStart.y == myEnd.y) {
      /* same line */
      if (myStart.x == myEnd.x) {
        return;
      }
      top = myStart.x < myEnd.x ? myStart: myEnd;
      bottom = myStart.x >= myEnd.x ? myStart: myEnd;
    }
    else {
      top = myStart.y < myEnd.y ? myStart: myEnd;
      bottom = myStart.y > myEnd.y ? myStart: myEnd;
    }
    
    if (y>=top.y && y<=bottom.y) {

      int startX;
      if (y == top.y) {
        startX = Math.max(x, top.x);
        if (startX-x>0) {
          consumer.consume(x, y, style, buf.subBuffer(0, startX-x), 0);
        }
      } else {
        startX = x;
      }
      
      int endX;
       if (y == bottom.y) {
         endX = Math.min(x + buf.length(), bottom.x);
         if (x + buf.length() - endX>0) {
           //consumer.consume(endX+1, y, style, buf.subBuffer());
         }
       } else {
         endX = x + buf.length();
       }
      
      style = style.clone();
      style.setForeground(Color.WHITE);
      style.setBackground(Color.BLACK);
      
      consumer.consume(startX, y, style, buf.subBuffer(startX - x, endX - x - (startX-x)), 0);
    }
    
    
  }
}
