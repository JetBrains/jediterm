package com.jediterm.terminal.debug;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;

/**
 * @author traff
 */
public class ControlSequenceVisualizer {

  private File myTempFile;

  public ControlSequenceVisualizer() {
    myTempFile = null;
    try {
      myTempFile = File.createTempFile("jeditermData", ".txt");
      myTempFile.deleteOnExit();
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getVisualizedString(List<char[]> chunks) {
    try {
      writeChunksToFile(chunks);

      return readOutput("teseq " + myTempFile.getAbsolutePath());
    }
    catch (IOException e) {
      return
        "Control sequence visualizer teseq is not installed.\nSee http://www.gnu.org/software/teseq/\nNow printing characters as is:\n\n" +
        joinChunks(chunks);
    }
  }

  private static String joinChunks(List<char[]> chunks) {
    StringBuilder sb = new StringBuilder();

    for (char[] ch : chunks) {
      sb.append(ch);
    }

    return sb.toString();
  }

  private void writeChunksToFile(@NotNull List<char[]> chunks) throws IOException {
    try (OutputStreamWriter stream = new OutputStreamWriter(new FileOutputStream(myTempFile, false))) {
      for (char[] data : chunks) {
        stream.write(data, 0, data.length);
      }
    }
  }

  public String readOutput(String command) throws IOException {
    String line;
    Process process = Runtime.getRuntime().exec(command);

    Reader inStreamReader = new InputStreamReader(process.getInputStream());
    BufferedReader in = new BufferedReader(inStreamReader);

    StringBuilder sb = new StringBuilder();
    int i = 0;
    String lastNum = null;
    while ((line = in.readLine()) != null) {
      if (!line.startsWith("&") && !line.startsWith("\"")) {
        lastNum = String.format("%3d ", i++);
        sb.append(lastNum);
      } else {
        if (lastNum != null) {
          sb.append(" ".repeat(lastNum.length()));
        }
      }
      sb.append(line);
      sb.append("\n");
    }
    in.close();
    return sb.toString();
  }
}
