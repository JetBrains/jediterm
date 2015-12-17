package testData;

import java.io.File;
import java.net.URL;

/**
 * @author traff
 */
public class TestPathsManager {
  public static String getHomePathFor(Class aClass) {
    String rootPath = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    if (rootPath != null) {
      File root = new File(rootPath).getAbsoluteFile();

      return root.getAbsolutePath();
    }
    return null;
  }

  public static String getResourceRoot(Class context, String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    if (url == null) {
      return null;
    }
    return url.getFile();
  }

  public static String getTestDataPath() {
    String file = getHomePathFor(TestPathsManager.class);
    if (file == null) {
      return null;
    }
    return file.substring(0, file.lastIndexOf(File.separator)) + File.separator;
  }
}
