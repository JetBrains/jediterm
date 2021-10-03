/**
 *
 */
package com.jediterm.terminal.debug;

import com.jediterm.terminal.LoggingTtyConnector;
import com.jediterm.terminal.ui.TerminalSession;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


public class BufferPanel extends JPanel {
  public BufferPanel(final TerminalSession terminal) {
    super(new BorderLayout());
    final JTextArea area = new JTextArea();
    area.setEditable(false);

    add(area, BorderLayout.NORTH);

    final DebugBufferType[] choices = DebugBufferType.values();

    final JComboBox chooser = new JComboBox(choices);
    add(chooser, BorderLayout.NORTH);

    area.setFont(Font.decode("Monospaced-14"));
    add(new JScrollPane(area), BorderLayout.CENTER);

    JPanel stateIndexPanel = new JPanel(new GridBagLayout());

    LoggingTtyConnector ttyConnector =
      (LoggingTtyConnector) terminal.getTtyConnector();
    int sliderMax = ttyConnector.getChunks().size();

    final JSlider slider = new JSlider(0, sliderMax, sliderMax);
    slider.setMajorTickSpacing(10);
    slider.setMinorTickSpacing(1);
    slider.setPaintTicks(true);
    slider.setPaintLabels(true);
    GridBagConstraints sliderConstraints = new GridBagConstraints();
    sliderConstraints.fill = GridBagConstraints.HORIZONTAL;
    sliderConstraints.gridx = 0;
    sliderConstraints.gridy = 0;
    sliderConstraints.weightx = 0.9;
    stateIndexPanel.add(slider, sliderConstraints);

    final JSpinner spinner = new JSpinner(new SpinnerNumberModel(sliderMax, 0, sliderMax, 1));
    GridBagConstraints spinnerConstraints = new GridBagConstraints();
    spinnerConstraints.fill = GridBagConstraints.HORIZONTAL;
    spinnerConstraints.gridx = 1;
    spinnerConstraints.gridy = 0;
    spinnerConstraints.weightx = 0.1;
    stateIndexPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    stateIndexPanel.add(spinner, spinnerConstraints);

    add(stateIndexPanel, BorderLayout.PAGE_END);

    class Updater implements ActionListener, ItemListener {
      private String myLastUpdate = "";

      public void update() {
        final DebugBufferType type = (DebugBufferType) chooser.getSelectedItem();
        int newMinStateIndex = ttyConnector.getLogStart();
        int newMaxStateIndex = newMinStateIndex + ttyConnector.getChunks().size();
        slider.setMinimum(newMinStateIndex);
        slider.setMaximum(newMaxStateIndex);
        SpinnerModel spinnerModel = new SpinnerNumberModel(Math.max((int) spinner.getValue(), newMinStateIndex), newMinStateIndex, newMaxStateIndex, 1);
        spinner.setModel(spinnerModel);
        final String text = terminal.getBufferText(type, slider.getValue() - slider.getMinimum());
        if (!text.equals(myLastUpdate)) {
          area.setText(text);
          myLastUpdate = text;
        }
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

    slider.addChangeListener((ChangeEvent event) -> {
      int value = slider.getValue();
      spinner.setValue(value);
      up.update();
    });

    spinner.addChangeListener((ChangeEvent event) -> {
      int value = (int) spinner.getValue();
      slider.setValue(value);
      up.update();
    });

    final Timer timer = new Timer(1000, up);
    timer.setRepeats(true);
    timer.start();
  }
}