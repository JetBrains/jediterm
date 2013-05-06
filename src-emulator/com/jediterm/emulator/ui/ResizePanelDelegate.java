package com.jediterm.emulator.ui;

import com.jediterm.emulator.RequestOrigin;

import java.awt.*;


public interface ResizePanelDelegate {

	void resizedPanel(Dimension pixelDimension, RequestOrigin origin);

}
