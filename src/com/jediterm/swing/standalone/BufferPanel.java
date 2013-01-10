/**
 * 
 */
package com.jediterm.swing.standalone;

import com.jediterm.swing.SwingJediTerminal;
import com.jediterm.swing.GrittyTerminal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


public class BufferPanel extends JPanel {
	public BufferPanel(final SwingJediTerminal terminal){
		super(new BorderLayout());
		final JTextArea area = new JTextArea();
		add(area, BorderLayout.NORTH);
		
		final SwingJediTerminal.BufferType[] choices = SwingJediTerminal.BufferType.values();
		
		final JComboBox chooser = new JComboBox(choices);
		add(chooser, BorderLayout.NORTH);
		
		area.setFont(Font.decode("Monospaced-14"));
		add(new JScrollPane(area), BorderLayout.CENTER);
		
		class Updater implements ActionListener, ItemListener{
			void update(){
				final SwingJediTerminal.BufferType type = (SwingJediTerminal.BufferType) chooser.getSelectedItem();
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