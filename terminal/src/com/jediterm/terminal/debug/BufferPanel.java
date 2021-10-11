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
    super(new GridBagLayout());


    final JTextArea area = constructTextArea();
    GridBagConstraints areaConstraints = new GridBagConstraints();
    areaConstraints.fill = GridBagConstraints.BOTH;
    areaConstraints.gridx = 0;
    areaConstraints.gridy = 1;
    areaConstraints.weightx = 0.5;
    areaConstraints.weighty = 1;
    JScrollPane areaScrollPane = new JScrollPane(area);
    add(areaScrollPane, areaConstraints);

    final JTextArea controlSequencesArea = constructTextArea();
    GridBagConstraints controlSequenceConstraints = new GridBagConstraints();
    controlSequenceConstraints.fill = GridBagConstraints.BOTH;
    controlSequenceConstraints.gridx = 1;
    controlSequenceConstraints.gridy = 1;
    controlSequenceConstraints.weightx = 0.5;
    controlSequenceConstraints.weighty = 1;
    add(new JScrollPane(controlSequencesArea), controlSequenceConstraints);

    final DebugBufferType[] choices = DebugBufferType.values();
    final JComboBox chooser = new JComboBox(choices);
    GridBagConstraints chooserConstraints = new GridBagConstraints();
    chooserConstraints.fill = GridBagConstraints.HORIZONTAL;
    chooserConstraints.gridx = 0;
    chooserConstraints.gridy = 0;
    chooserConstraints.weightx = 1;
    chooserConstraints.gridwidth = 2;
    add(chooser, chooserConstraints);

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
    stateIndexPanel.add(spinner, spinnerConstraints);

    GridBagConstraints stateIndexPanelConstraints = new GridBagConstraints();
    stateIndexPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
    stateIndexPanelConstraints.gridx = 0;
    stateIndexPanelConstraints.gridy = 2;
    stateIndexPanelConstraints.weightx = 1;
    stateIndexPanelConstraints.gridwidth = 2;
    add(stateIndexPanel, stateIndexPanelConstraints);

    class Updater implements ActionListener, ItemListener {
      private String myLastUpdateText = "";
      private String myLastUpdateControlSequenceVisualization = "";

      public void update() {
        final DebugBufferType type = (DebugBufferType) chooser.getSelectedItem();
        int newMinStateIndex = ttyConnector.getLogStart();
        int newMaxStateIndex = newMinStateIndex + ttyConnector.getChunks().size();
        slider.setMinimum(newMinStateIndex);
        slider.setMaximum(newMaxStateIndex);
        SpinnerModel spinnerModel = new SpinnerNumberModel(Math.max((int) spinner.getValue(), newMinStateIndex), newMinStateIndex, newMaxStateIndex, 1);
        spinner.setModel(spinnerModel);
        int stateIndex = slider.getValue() - slider.getMinimum();
        final String text = terminal.getBufferText(type, stateIndex);
        final String controlSequencesVisualization = getControlSequencesVisualization(terminal, stateIndex);
        if (!text.equals(myLastUpdateText)) {
          area.setText(text);
          myLastUpdateText = text;
        }
        if (!controlSequencesVisualization.equals(myLastUpdateControlSequenceVisualization)) {
          controlSequencesArea.setText(controlSequencesVisualization);
          myLastUpdateControlSequenceVisualization = controlSequencesVisualization;
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

  private static JTextArea constructTextArea() {
    JTextArea area = new JTextArea();
    area.setEditable(false);
    area.setFont(Font.decode("Monospaced-14"));
    return area;
  }

  private final ControlSequenceVisualizer myVisualizer = new ControlSequenceVisualizer();

  private String getControlSequencesVisualization(TerminalSession session, int stateIndex) {
    if (session.getTtyConnector() instanceof LoggingTtyConnector) {
      java.util.List<char[]> chunks = ((LoggingTtyConnector) session.getTtyConnector()).getChunks();
      return myVisualizer.getVisualizedString(chunks.subList(0, stateIndex));
    } else {
      return "Control sequences aren't logged";
    }
  }

}