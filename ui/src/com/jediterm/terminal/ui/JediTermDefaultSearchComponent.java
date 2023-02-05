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
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class JediTermDefaultSearchComponent extends JPanel implements JediTermSearchComponent {

  private final JTextField myTextField = new JTextField();
  private final JLabel label = new JLabel();
  private final JCheckBox ignoreCaseCheckBox = new JCheckBox("Ignore Case", true);
  private final List<JediTermSearchComponentListener> myListeners = new CopyOnWriteArrayList<>();
  private final JediTermSearchComponentListener myMulticaster = createMulticaster();

  public JediTermDefaultSearchComponent(JediTermWidget jediTermWidget) {
    JButton next = createNextButton();
    next.addActionListener(e -> myMulticaster.selectNextFindResult());

    JButton prev = createPrevButton();
    prev.addActionListener(e -> myMulticaster.selectPrevFindResult());

    myTextField.setPreferredSize(new Dimension(
      jediTermWidget.myTerminalPanel.myCharSize.width * 30,
      jediTermWidget.myTerminalPanel.myCharSize.height + 3));
    myTextField.setEditable(true);

    updateLabel(null);

    add(myTextField);
    listenForChanges();
    add(ignoreCaseCheckBox);
    add(label);
    add(next);
    add(prev);

    setOpaque(true);
  }

  private void listenForChanges() {
    Runnable settingsChanged = () -> {
      myMulticaster.searchSettingsChanged(myTextField.getText(), ignoreCaseCheckBox.isSelected());
    };
    myTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        settingsChanged.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        settingsChanged.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        settingsChanged.run();
      }
    });
    ignoreCaseCheckBox.addItemListener(e -> settingsChanged.run());
  }

  private JButton createNextButton() {
    return new BasicArrowButton(SwingConstants.SOUTH);
  }

  private JButton createPrevButton() {
    return new BasicArrowButton(SwingConstants.NORTH);
  }

  private void updateLabel(@Nullable SubstringFinder.FindResult result) {
    if (result == null) {
      label.setText("");
    }
    else if (!result.getItems().isEmpty()) {
      SubstringFinder.FindResult.FindItem selectedItem = result.selectedItem();
      label.setText(selectedItem.getIndex() + " of " + result.getItems().size());
    }
  }

  @Override
  public void onResultUpdated(SubstringFinder.@Nullable FindResult results) {
    updateLabel(results);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public void addListener(@NotNull JediTermSearchComponentListener listener) {
    myListeners.add(listener);
  }

  public void requestFocus() {
    myTextField.requestFocus();
  }

  @Override
  public void addKeyListener(@NotNull KeyListener listener) {
    myTextField.addKeyListener(listener);
  }

  private @NotNull JediTermSearchComponentListener createMulticaster() {
    final Class<JediTermSearchComponentListener> listenerClass = JediTermSearchComponentListener.class;
    return (JediTermSearchComponentListener) Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, (object, method, params) -> {
      for (JediTermSearchComponentListener listener : myListeners) {
        method.invoke(listener, params);
      }
      //noinspection SuspiciousInvocationHandlerImplementation
      return null;
    });
  }
}
