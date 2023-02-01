package com.jediterm.terminal;

/**
 * @deprecated Collect extra information when creating {@link TtyConnector}.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public interface Questioner {
  String questionVisible(String question, String defValue);

  String questionHidden(String string);

  void showMessage(String message);
}
