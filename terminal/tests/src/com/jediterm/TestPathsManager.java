package com.jediterm;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
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
        try {
            URI uri = TestPathsManager.class.getClassLoader().getResource("testData").toURI();
            return uri.getPath() + File.separator;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }
}
