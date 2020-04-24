package com.jediterm.example;

import com.google.common.base.Ascii;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

public class BasicTerminalExample {
  private static final char ESC = Ascii.ESC;

  private static void writeTerminalCommands(@NotNull PipedWriter writer) throws IOException {
    writer.write(ESC + "%G");
    writer.write(ESC + "[31m");
    writer.write("Hello\r\n");
    writer.write(ESC + "[32;43m");
    writer.write("World\r\n");
  }

  private static @NotNull JediTermWidget createTerminalWidget() {
    JediTermWidget widget = new JediTermWidget(80, 24, new DefaultSettingsProvider());
    PipedWriter terminalWriter = new PipedWriter();
    widget.setTtyConnector(new ExampleTtyConnector(terminalWriter));
    widget.start();
    try {
      writeTerminalCommands(terminalWriter);
      terminalWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return widget;
  }

  private static void createAndShowGUI() {
    JFrame frame = new JFrame("Basic Terminal Example");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setContentPane(createTerminalWidget());
    frame.pack();
    frame.setVisible(true);
  }

  public static void main(String[] args) {
    // Create and show this application's GUI in the event-dispatching thread.
    SwingUtilities.invokeLater(BasicTerminalExample::createAndShowGUI);
  }

  private static class ExampleTtyConnector implements TtyConnector {

    private final PipedReader myReader;

    public ExampleTtyConnector(@NotNull PipedWriter writer) {
      try {
        myReader =  new PipedReader(writer);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean init(Questioner q) {
      return true;
    }

    @Override
    public void close() {
    }

    @Override
    public void resize(Dimension termSize, Dimension pixelSize) {
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
      return myReader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) {
    }

    @Override
    public boolean isConnected() {
      return true;
    }

    @Override
    public void write(String string) {
    }

    @Override
    public int waitFor() {
      return 0;
    }
  }
}
