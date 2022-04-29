package com.jediterm.core.model.hyperlinks;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class UrlLinkInfo implements LinkInfo {
  private final String myUrl;

  public UrlLinkInfo(@NotNull String url) {
    myUrl = url;
  }

  public String getUrl() {
    return myUrl;
  }
}
