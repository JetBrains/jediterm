package com.jediterm.terminal.model.hyperlinks;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class LinkResult {
  private final LinkResultItem myItem;
  private List<LinkResultItem> myItemList;

  public LinkResult(@NotNull LinkResultItem item) {
    myItem = item;
    myItemList = null;
  }

  public LinkResult(@NotNull List<LinkResultItem> itemList) {
    myItemList = itemList;
    myItem = null;
  }

  public List<LinkResultItem> getItems() {
    if (myItemList == null) {
      myItemList = Lists.newArrayList(myItem);
    }
    return myItemList;
  }
}
