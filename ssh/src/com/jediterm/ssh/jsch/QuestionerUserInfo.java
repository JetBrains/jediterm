/**
 *
 */
package com.jediterm.ssh.jsch;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.jediterm.terminal.Questioner;

class QuestionerUserInfo implements UserInfo, UIKeyboardInteractive {
  private Questioner myQuestioner;
  private String myPassword;
  private String myPassPhrase;

  public QuestionerUserInfo(Questioner questioner) {
    this.myQuestioner = questioner;
  }

  public String getPassphrase() {
    return myPassPhrase;
  }

  public String getPassword() {
    return myPassword;
  }

  public void setPassword(String password) {
    this.myPassword = password;
  }

  public boolean promptPassphrase(String message) {
    myPassPhrase = myQuestioner.questionHidden(message + ":");
    return true;
  }

  public boolean promptPassword(String message) {
    myPassword = myQuestioner.questionHidden(message + ":");
    return true;
  }

  public boolean promptYesNo(String message) {
    String yn = myQuestioner.questionVisible(message + " [Y/N]:", "Y");
    String lyn = yn.toLowerCase();
    if (lyn.equals("y") || lyn.equals("yes")) {
      return true;
    }
    else {
      return false;
    }
  }

  public void showMessage(String message) {
    myQuestioner.showMessage(message);
  }

  public String[] promptKeyboardInteractive(final String destination, final String name,
                                            final String instruction, final String[] prompt, final boolean[] echo) {
    int len = prompt.length;
    String[] results = new String[len];
    if (destination != null && destination.length() > 0) myQuestioner.showMessage(destination);
    if (name != null && name.length() > 0) myQuestioner.showMessage(name);
    myQuestioner.showMessage(instruction);
    for (int i = 0; i < len; i++) {
      String promptStr = prompt[i];
      results[i] = echo[i] ? myQuestioner.questionVisible(promptStr, null) :
                   myQuestioner.questionHidden(promptStr);
    }
    return results;
  }
}