package com.jediterm.example;

import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.jediterm.app.PlatformUtilKt.isWindows;

public class BasicTerminalShellExample {

  private static @NotNull JediTermWidget createTerminalWidget() {
    JediTermWidget widget = new JediTermWidget(80, 24, new DefaultSettingsProvider());
    widget.setTtyConnector(createTtyConnector());
    widget.start();
    return widget;
  }

  private static @NotNull TtyConnector createTtyConnector() {
    try {
      Map<String, String> envs = System.getenv();
      String[] command;
      if (isWindows()) {
        command = new String[]{"cmd.exe"};
      } else {
        command = new String[]{"/bin/bash", "--login"};
        envs = new HashMap<>(System.getenv());
        envs.put("TERM", "xterm-256color");
      }

      PtyProcess process = new PtyProcessBuilder().setCommand(command).setEnvironment(envs).start();
      return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void createAndShowGUI() {
    JFrame frame = new JFrame("Basic Terminal Shell Example");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    JediTermWidget widget = createTerminalWidget();
    widget.addListener(terminalWidget -> {
      widget.close(); // terminate the current process and dispose all allocated resources
      SwingUtilities.invokeLater(() -> {
        if (frame.isVisible()) {
          frame.dispose();
        }
      });
    });
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        frame.setVisible(false);
        widget.getTtyConnector().close(); // terminate the current process
      }
    });
    frame.setContentPane(widget);
    frame.pack();
    frame.setVisible(true);
  }

  public static void main(String[] args) {
    // Create and show this application's GUI in the event-dispatching thread.
    SwingUtilities.invokeLater(BasicTerminalShellExample::createAndShowGUI);
  }
}
