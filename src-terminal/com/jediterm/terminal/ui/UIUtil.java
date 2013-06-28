package com.jediterm.terminal.ui;

import com.jediterm.terminal.Util;

import java.awt.*;
import java.lang.reflect.Field;

/**
 * @author traff
 */
public class UIUtil {
  private static final boolean IS_ORACLE_JVM = isOracleJvm();

  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");

  public static boolean isRetina() {
    if (isJavaVersionAtLeast("1.7.0_40") && IS_ORACLE_JVM) {
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      final GraphicsDevice device = env.getDefaultScreenDevice();

      try {
        Field field = device.getClass().getDeclaredField("scale");

        if (field != null) {
          field.setAccessible(true);
          Object scale = field.get(device);

          if (scale instanceof Integer && ((Integer)scale).intValue() == 2) {
            return true;
          }
        }
      }
      catch (Exception ignore) {
      }
    }

    final Float scaleFactor = (Float)Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor");

    if (scaleFactor != null && scaleFactor.intValue() == 2) {
      return true;
    }
    return false;
  }

  private static boolean isOracleJvm() {
    final String vendor = getJavaVmVendor();
    return vendor != null && Util.containsIgnoreCase(vendor, "Oracle");
  }

  public static String getJavaVmVendor() {
    return System.getProperty("java.vm.vendor");
  }

  public static boolean isJavaVersionAtLeast(String v) {
    return Util.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }
}
