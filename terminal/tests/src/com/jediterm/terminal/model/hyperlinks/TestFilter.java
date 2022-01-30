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
package com.jediterm.terminal.model.hyperlinks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestFilter implements HyperlinkFilter {

  public static final String PREFIX = "my_link:";

  @Nullable
  @Override
  public LinkResult apply(@NotNull String line) {
    int startInd = line.indexOf(PREFIX);
    if (startInd >= 0) {
      int endInd = startInd;
      while (endInd < line.length()) {
        char ch = line.charAt(endInd);
        if (!Character.isLetterOrDigit(ch) && "_:".indexOf(ch) < 0) {
          break;
        }
        endInd++;
      }
      if (endInd > 0) {
        return new LinkResult(new LinkResultItem(startInd, endInd, new LinkInfo(() -> {
        })));
      }
    }
    return null;
  }

  @NotNull
  public static String formatLink(@NotNull String text) {
    return PREFIX + text;
  }
}
