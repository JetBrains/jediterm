package com.jediterm.terminal.ui.hyperlinks;

import com.jediterm.core.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author traff
 */
public class UILinkInfo implements LinkInfo {
  private final Runnable myNavigateCallback;
  private final PopupMenuGroupProvider myPopupMenuGroupProvider;
  private final HoverConsumer myHoverConsumer;

  public UILinkInfo(@NotNull Runnable navigateCallback) {
    this(navigateCallback, null, null);
  }

  private UILinkInfo(@NotNull Runnable navigateCallback,
                     @Nullable PopupMenuGroupProvider popupMenuGroupProvider,
                     @Nullable UILinkInfo.HoverConsumer hoverConsumer) {
    myNavigateCallback = navigateCallback;
    myPopupMenuGroupProvider = popupMenuGroupProvider;
    myHoverConsumer = hoverConsumer;
  }

  public void navigate() {
    myNavigateCallback.run();
  }

  public @Nullable PopupMenuGroupProvider getPopupMenuGroupProvider() {
    return myPopupMenuGroupProvider;
  }

  public @Nullable UILinkInfo.HoverConsumer getHoverConsumer() {
    return myHoverConsumer;
  }

  public interface PopupMenuGroupProvider {
    @NotNull List<TerminalAction> getPopupMenuGroup(@NotNull MouseEvent event);
  }

  public interface HoverConsumer {
    /**
     * Gets called when the mouse cursor enters the link's bounds.
     * @param hostComponent terminal/console component containing the link
     * @param linkBounds link's bounds relative to {@code hostComponent}
     */
    void onMouseEntered(@NotNull JComponent hostComponent, @NotNull Rectangle linkBounds);
    /**
     * Gets called when the mouse cursor exits the link's bounds.
     */
    void onMouseExited();
  }

  public static final class Builder {
    private Runnable myNavigateCallback;
    private PopupMenuGroupProvider myPopupMenuGroupProvider;
    private HoverConsumer myHoverConsumer;

    public @NotNull Builder setNavigateCallback(@NotNull Runnable navigateCallback) {
      myNavigateCallback = navigateCallback;
      return this;
    }

    public @NotNull Builder setPopupMenuGroupProvider(@Nullable PopupMenuGroupProvider popupMenuGroupProvider) {
      myPopupMenuGroupProvider = popupMenuGroupProvider;
      return this;
    }

    public @NotNull Builder setHoverConsumer(@Nullable UILinkInfo.HoverConsumer hoverConsumer) {
      myHoverConsumer = hoverConsumer;
      return this;
    }

    public @NotNull UILinkInfo build() {
      return new UILinkInfo(myNavigateCallback, myPopupMenuGroupProvider, myHoverConsumer);
    }
  }
}
