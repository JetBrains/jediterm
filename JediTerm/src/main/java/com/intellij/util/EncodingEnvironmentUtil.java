/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;

public class EncodingEnvironmentUtil {
  private static final Logger LOG = Logger.getInstance(EncodingEnvironmentUtil.class);

  private static final String LC_ALL = "LC_ALL";
  private static final String LC_CTYPE = "LC_CTYPE";
  private static final String LANG = "LANG";


  public static void setLocaleEnvironmentIfMac(@NotNull Map<String, String> env, @NotNull Charset charset) {
    if (SystemInfo.isMac && !isLocaleDefined(env)) {
      setLocaleEnvironment(env, charset);
    }
  }

  private static void setLocaleEnvironment(@NotNull Map<String, String> env, @NotNull Charset charset) {
    env.put(LC_CTYPE, formatLocaleValue(charset));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fixed mac locale: " + charset.name());
    }
  }

  @NotNull
  private static String formatLocaleValue(@NotNull Charset charset) {
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    String country = locale.getCountry();
    return (language.isEmpty() || country.isEmpty() ? "en_US" : language + "_" + country) + "." + charset.name();
  }


  private static boolean isLocaleDefined(@NotNull Map<String, String> env) {
    return !env.isEmpty() && (env.containsKey(LC_CTYPE) || env.containsKey(LC_ALL) || env.containsKey(LANG));
  }

}
