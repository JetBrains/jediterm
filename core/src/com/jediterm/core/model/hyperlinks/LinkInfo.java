package com.jediterm.core.model.hyperlinks;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class LinkInfo {
  private final String myUrl;

  public LinkInfo(@NotNull String url) {
    myUrl = url;
  }

  public String getUrl() {
    return myUrl;
  }
}
