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
package com.jediterm.app;

import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlFilter implements HyperlinkFilter {

  private static final String FILE_MINIMAL_PROTOCOL_PREFIX = "file:/";

  private static final Pattern FILE_URL_PATTERN = Pattern.compile(
    "\\bfile:(?:///|/)[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]"
  );

  private static final Pattern URL_PATTERN = Pattern.compile(
    "\\b(?:mailto:|(?:news|(?:ht|f)tps?)://|(?<![\\p{L}0-9_.])www\\.)[-A-Za-z0-9+$&@#/%?=~_|!:,.;]*[-A-Za-z0-9+$&@#/%=~_|]"
  );

  /**
   * @return if false, then the line contains no URL; if true, then more heavy {@link #URL_PATTERN} check should be used.
   */
  public static boolean canContainUrl(@NotNull String line) {
    return line.contains("mailto:") || line.contains("://") || line.contains("www.") || line.contains("file:/");
  }

  @Nullable
  @Override
  public LinkResult apply(String line) {
    if (!canContainUrl(line)) return null;

    List<LinkResultItem> resultList = new ArrayList<>();
    if (line.contains(FILE_MINIMAL_PROTOCOL_PREFIX)) {
      addMatchingItems(line, FILE_URL_PATTERN, resultList);
    }
    if (isPotentialUrl(line)) {
      addMatchingItems(line, URL_PATTERN, resultList);
    }
    return resultList.isEmpty() ? null : new LinkResult(resultList);
  }

  private static boolean isPotentialUrl(@NotNull String input) {
    return input.contains("www") ||
      input.contains("http") ||
      input.contains("mailto") ||
      input.contains("ftp") ||
      input.contains("news");
  }

  private void addMatchingItems(
    @NotNull String line,
    @NotNull Pattern pattern,
    @NotNull List<LinkResultItem> resultList
  ) {
    Matcher matcher = pattern.matcher(line);
    while (matcher.find()) {
      String url = matcher.group();
      resultList.add(new LinkResultItem(matcher.start(), matcher.end(), new LinkInfo(() -> {
        try {
          Desktop.getDesktop().browse(new URI(url));
        }
        catch (Exception e) {
          //pass
        }
      })));
    }
  }

}
