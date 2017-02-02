/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class URLUtil {
  public static final String SCHEME_SEPARATOR = "://";
  public static final String FILE_PROTOCOL = "file";
  public static final String HTTP_PROTOCOL = "http";
  public static final String JAR_PROTOCOL = "jar";
  public static final String JAR_SEPARATOR = "!/";

  public static final Pattern DATA_URI_PATTERN = Pattern.compile("data:([^,;]+/[^,;]+)(;charset(?:=|:)[^,;]+)?(;base64)?,(.+)");
  public static final Pattern URL_PATTERN = Pattern.compile("\\b(mailto:|(news|(ht|f)tp(s?))://|((?<![\\p{L}0-9_.])(www\\.)))[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]");

  private URLUtil() {
  }

  /**
   * @return if false, then the line contains no URL; if true, then more heavy {@link #URL_PATTERN} check should be used.
   */
  public static boolean canContainUrl(@NotNull String line) {
    return line.contains("mailto:") || line.contains("://") || line.contains("www.");
  }

}
