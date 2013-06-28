/* -*-mode:java; c-basic-offset:2; -*- */
/* JCTerm
 * Copyright (C) 2002 ymnk, JCraft,Inc.
 *  
 * Written by: 2002 ymnk<ymnk@jcaft.com>
 *   
 *   
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.jediterm.terminal;

import com.jediterm.terminal.display.BufferedDisplayTerminal;

import java.awt.*;

public interface TerminalDisplay {
  // Size information
  int getRowCount();

  int getColumnCount();

  void setCursor(int x, int y);

  void beep();

  Dimension requestResize(Dimension pendingResize, RequestOrigin origin, int cursorY, BufferedDisplayTerminal.ResizeHandler resizeHandler);

  void scrollArea(final int y, final int h, int dy);

  void setCursorVisible(boolean shouldDrawCursor);
  
  void setScrollingEnabled(boolean enabled);

  void setBlinkingCursor(boolean enabled);
}
