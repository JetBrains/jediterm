/**
 *
 */
package com.jediterm.ssh.jsch;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import javax.swing.*;
import java.awt.*;

public class SwingUserInfo implements UserInfo, UIKeyboardInteractive {

  private String myPassword = null;

  private String myPassphrase = null;

  private JTextField myPasswordField = new JPasswordField(20);

  public String getPassword() {
    return myPassword;
  }

  public String getPassphrase() {
    return myPassphrase;
  }

  public boolean promptYesNo(final String str) {
    final Object[] options = {"yes", "no"};
    final int foo = JOptionPane.showOptionDialog(null, str, "Warning",
                                                 JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                                                 options, options[0]);
    return foo == 0;
  }

  public boolean promptPassword(final String message) {
    final Object[] ob = {myPasswordField};
    final int result = JOptionPane.showConfirmDialog(null, ob, message,
                                                     JOptionPane.OK_CANCEL_OPTION);
    if (result == JOptionPane.OK_OPTION) {
      myPassword = myPasswordField.getText();
      return true;
    }
    else {
      return false;
    }
  }

  public boolean promptPassphrase(final String message) {
    return true;
  }

  public void showMessage(final String message) {
    JOptionPane.showMessageDialog(null, message);
  }

  final GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 1,
                                                        GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(
    0, 0, 0, 0), 0, 0);

  private Container panel;

  public String[] promptKeyboardInteractive(final String destination, final String name,
                                            final String instruction, final String[] prompt, final boolean[] echo) {
    panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.gridx = 0;
    panel.add(new JLabel(instruction), gbc);
    gbc.gridy++;

    gbc.gridwidth = GridBagConstraints.RELATIVE;

    final JTextField[] texts = new JTextField[prompt.length];
    for (int i = 0; i < prompt.length; i++) {
      gbc.fill = GridBagConstraints.NONE;
      gbc.gridx = 0;
      gbc.weightx = 1;
      panel.add(new JLabel(prompt[i]), gbc);

      gbc.gridx = 1;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weighty = 1;
      if (echo[i]) {
        texts[i] = new JTextField(20);
      }
      else {
        texts[i] = new JPasswordField(20);
      }
      panel.add(texts[i], gbc);
      gbc.gridy++;
    }

    if (JOptionPane.showConfirmDialog(null, panel, destination + ": "
                                                   + name, JOptionPane.OK_CANCEL_OPTION,
                                      JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
      final String[] response = new String[prompt.length];
      for (int i = 0; i < prompt.length; i++) {
        response[i] = texts[i].getText();
      }
      return response;
    }
    else {
      return null; // cancel
    }
  }
}