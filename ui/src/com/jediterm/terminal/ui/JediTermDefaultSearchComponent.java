package com.jediterm.terminal.ui;

import com.jediterm.terminal.SubstringFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.KeyListener;

public final class JediTermDefaultSearchComponent extends JPanel implements JediTermWidget.SearchComponent {

  private final JediTermWidget jediTermWidget;
  private final JTextField myTextField = new JTextField();
  private final JLabel label = new JLabel();
  private final JCheckBox ignoreCaseCheckBox = new JCheckBox("Ignore Case", true);

  public JediTermDefaultSearchComponent(JediTermWidget jediTermWidget) {
    this.jediTermWidget = jediTermWidget;
    JButton next = createNextButton();
    next.addActionListener(e -> nextFindResultItem(jediTermWidget.myTerminalPanel.selectNextFindResultItem()));

    JButton prev = createPrevButton();
    prev.addActionListener(e -> prevFindResultItem(jediTermWidget.myTerminalPanel.selectPrevFindResultItem()));

    myTextField.setPreferredSize(new Dimension(
      jediTermWidget.myTerminalPanel.myCharSize.width * 30,
      jediTermWidget.myTerminalPanel.myCharSize.height + 3));
    myTextField.setEditable(true);

    updateLabel(null);

    add(myTextField);
    add(ignoreCaseCheckBox);
    add(label);
    add(next);
    add(prev);

    setOpaque(true);
  }

  private JButton createNextButton() {
    return new BasicArrowButton(SwingConstants.NORTH);
  }

  private JButton createPrevButton() {
    return new BasicArrowButton(SwingConstants.SOUTH);
  }

  @Override
  public void nextFindResultItem(SubstringFinder.FindResult.@Nullable FindItem selectedItem) {
    updateLabel(selectedItem);
  }

  @Override
  public void prevFindResultItem(SubstringFinder.FindResult.@Nullable FindItem selectedItem) {
    updateLabel(selectedItem);
  }

  private void updateLabel(SubstringFinder.FindResult.FindItem selectedItem) {
    SubstringFinder.FindResult result = jediTermWidget.myTerminalPanel.getFindResult();
    label.setText(((selectedItem != null) ? selectedItem.getIndex() : 0)
      + " of " + ((result != null) ? result.getItems().size() : 0));
  }

  @Override
  public void onResultUpdated(SubstringFinder.@Nullable FindResult results) {
    updateLabel(null);
  }

  @Override
  public @NotNull String getText() {
    return myTextField.getText();
  }

  @Override
  public boolean ignoreCase() {
    return ignoreCaseCheckBox.isSelected();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void addSettingsChangedListener(@NotNull Runnable onChangeListener) {
    myTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChangeListener.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChangeListener.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChangeListener.run();
      }
    });
    ignoreCaseCheckBox.addItemListener(e -> {
      onChangeListener.run();
    });
  }

  public void requestFocus() {
    myTextField.requestFocus();
  }

  @Override
  public void addKeyListener(@NotNull KeyListener listener) {
    myTextField.addKeyListener(listener);
  }
}
