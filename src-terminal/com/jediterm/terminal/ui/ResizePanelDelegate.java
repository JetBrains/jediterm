package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;

import java.awt.*;


public interface ResizePanelDelegate {

	void resizedPanel(Dimension pixelDimension, RequestOrigin origin);

}
