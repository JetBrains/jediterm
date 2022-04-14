package com.jediterm.core.model.hyperlinks;

import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface HyperlinkFilter {

  @Nullable
  LinkResult apply(String line);
}
