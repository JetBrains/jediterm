package com.jediterm.ui.debug

import com.jediterm.terminal.LoggingTtyConnector
import com.jediterm.terminal.ui.TerminalSession
import java.awt.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*
import kotlin.math.max

class TerminalDebugView(private val terminal: TerminalSession) {
  private val loggingTtyConnector: LoggingTtyConnector = terminal.getTtyConnector() as LoggingTtyConnector
  private val typeComboBox: JComboBox<DebugBufferType> = JComboBox(DebugBufferType.entries.toTypedArray())
  private val slider: JSlider
  private val spinner: JSpinner
  private val resultPanel: JPanel
  private val timer: Timer
  private val listeners: MutableList<StateChangeListener> = CopyOnWriteArrayList()
  private val myVisualizer = ControlSequenceVisualizer()

  init {
    val viewArea = createViewArea()
    val controlSequencesArea = createControlSequenceArea()
    slider = createSlider()
    spinner = JSpinner()
    resultPanel = createResultPanel(typeComboBox, viewArea, controlSequencesArea, slider, spinner)
    typeComboBox.addItemListener {
      update()
    }
    slider.addChangeListener {
      spinner.value = slider.value
      update()
    }
    spinner.addChangeListener {
      slider.setValue(spinner.value as Int)
      update()
    }
    update()
    timer = Timer(1000) {
      update()
    }
    timer.isRepeats = true
    timer.start()
  }

  private fun createViewArea(): JTextArea {
    val viewArea = createTextArea()
    addListener(object : StateChangeListener {
      private var myLastText: String? = null
      override fun stateChanged(type: DebugBufferType, stateIndex: Int) {
        val text = type.getValue(terminal, stateIndex)
        if (text != myLastText) {
          viewArea.text = text
          myLastText = text
        }
      }
    })
    return viewArea
  }

  private fun createControlSequenceArea(): JTextArea {
    val controlSequenceArea = createTextArea()
    addListener(object : StateChangeListener {
      private var myLastText: String? = null
      override fun stateChanged(type: DebugBufferType, stateIndex: Int) {
        val controlSequencesVisualization = getControlSequencesVisualization(stateIndex)
        if (controlSequencesVisualization != myLastText) {
          controlSequenceArea.text = controlSequencesVisualization
          myLastText = controlSequencesVisualization
        }
      }
    })
    return controlSequenceArea
  }

  private fun createSlider(): JSlider {
    val slider = JSlider(0, 0, 0)
    slider.setMajorTickSpacing(10)
    slider.setMinorTickSpacing(1)
    slider.setPaintTicks(true)
    slider.setPaintLabels(true)
    return slider
  }

  private fun update() {
    val type: DebugBufferType = typeComboBox.selectedItem as DebugBufferType
    val newMinStateIndex = loggingTtyConnector.getLogStart()
    val newMaxStateIndex = newMinStateIndex + loggingTtyConnector.getChunks().size
    slider.setMinimum(newMinStateIndex)
    slider.setMaximum(newMaxStateIndex)
    val spinnerModel: SpinnerModel = SpinnerNumberModel(max(spinner.value as Int, newMinStateIndex), newMinStateIndex, newMaxStateIndex, 1)
    spinner.setModel(spinnerModel)
    val stateIndex = slider.value - slider.minimum
    for (listener in listeners) {
      listener.stateChanged(type, stateIndex)
    }
  }

  private fun addListener(listener: StateChangeListener) {
    listeners.add(listener)
  }

  private fun getControlSequencesVisualization(stateIndex: Int): String {
    val chunks = loggingTtyConnector.getChunks()
    return myVisualizer.getVisualizedString(chunks.subList(0, stateIndex))
  }

  val component: JComponent
    get() = resultPanel

  fun stop() {
    timer.stop()
  }

  private interface StateChangeListener {
    fun stateChanged(type: DebugBufferType, stateIndex: Int)
  }

  companion object {
    private fun createResultPanel(typeComboBox: JComboBox<DebugBufferType>,
                                  viewArea: JTextArea,
                                  controlSequencesArea: JTextArea,
                                  slider: JSlider,
                                  spinner: JSpinner): JPanel {
      val resultPanel = JPanel(BorderLayout(0, 0))
      resultPanel.add(typeComboBox, BorderLayout.NORTH)
      val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
      splitPane.leftComponent = JScrollPane(viewArea)
      splitPane.rightComponent = JScrollPane(controlSequencesArea)
      resultPanel.add(splitPane, BorderLayout.CENTER)
      resultPanel.add(createBottomPanel(slider, spinner), BorderLayout.SOUTH)
      return resultPanel
    }

    private fun createBottomPanel(slider: JSlider, spinner: JSpinner): JPanel {
      val stateIndexPanel = JPanel(GridBagLayout())
      stateIndexPanel.add(slider, GridBagConstraints(
        0, 0,
        1, 1,
        0.9, 0.0,
        GridBagConstraints.CENTER,
        GridBagConstraints.HORIZONTAL,
        Insets(0, 0, 0, 0),
        0, 0))
      stateIndexPanel.add(spinner, GridBagConstraints(
        1, 0,
        1, 1,
        0.1, 0.0,
        GridBagConstraints.CENTER,
        GridBagConstraints.HORIZONTAL,
        Insets(0, 0, 0, 0),
        0, 0))
      return stateIndexPanel
    }

    private fun createTextArea(): JTextArea {
      return JTextArea().also {
        it.isEditable = false
        it.setFont(Font("Monospaced", Font.PLAIN, 14))
      }
    }
  }
}