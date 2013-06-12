/**
 * 
 */
package com.jediterm.emulator.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


class BufferPanel extends JPanel {
	public BufferPanel(final SwingJediTerminal terminal){
		super(new BorderLayout());
		final JTextArea area = new JTextArea();
		add(area, BorderLayout.NORTH);
		
		final DebugBufferType[] choices = DebugBufferType.values();
		
		final JComboBox chooser = new JComboBox(choices);
		add(chooser, BorderLayout.NORTH);
		
		area.setFont(Font.decode("Monospaced-14"));
		add(new JScrollPane(area), BorderLayout.CENTER);
		
		class Updater implements ActionListener, ItemListener{
			void update(){
				final DebugBufferType type = (DebugBufferType) chooser.getSelectedItem();
				final String text = terminal.getBufferText(type);
				area.setText(text);
			}
			
			public void actionPerformed(final ActionEvent e) {
				update();
			}

			public void itemStateChanged(final ItemEvent e) {
				update();
			}
		}
		final Updater up = new Updater();
		chooser.addItemListener(up);
		final Timer timer = new Timer(1000, up);
		timer.setRepeats(true);
		timer.start();
		
	}
}