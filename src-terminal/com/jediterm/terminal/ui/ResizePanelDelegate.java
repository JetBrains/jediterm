package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;

import java.awt.*;


public interface ResizePanelDelegate {

  void onPanelResize(Dimension pixelDimension, RequestOrigin origin);

}
