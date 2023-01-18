package com.jediterm.terminal.ui.hyperlinks;

import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public final class LinkInfoEx extends LinkInfo {

  private final PopupMenuGroupProvider myPopupMenuGroupProvider;
  private final HoverConsumer myHoverConsumer;

  public LinkInfoEx(@NotNull Runnable navigateCallback) {
    this(navigateCallback, null, null);
  }

  private LinkInfoEx(@NotNull Runnable navigateCallback,
                     @Nullable PopupMenuGroupProvider popupMenuGroupProvider,
                     @Nullable HoverConsumer hoverConsumer) {
    super(navigateCallback);
    myPopupMenuGroupProvider = popupMenuGroupProvider;
    myHoverConsumer = hoverConsumer;
  }

  public @Nullable PopupMenuGroupProvider getPopupMenuGroupProvider() {
    return myPopupMenuGroupProvider;
  }

  public @Nullable HoverConsumer getHoverConsumer() {
    return myHoverConsumer;
  }

  public interface PopupMenuGroupProvider {
    @NotNull List<TerminalAction> getPopupMenuGroup(@NotNull MouseEvent event);
  }

  public interface HoverConsumer {
    /**
     * Gets called when the mouse cursor enters the link's bounds.
     *
     * @param hostComponent terminal/console component containing the link
     * @param linkBounds    link's bounds relative to {@code hostComponent}
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

    public @NotNull Builder setHoverConsumer(@Nullable HoverConsumer hoverConsumer) {
      myHoverConsumer = hoverConsumer;
      return this;
    }

    public @NotNull LinkInfo build() {
      return new LinkInfoEx(myNavigateCallback, myPopupMenuGroupProvider, myHoverConsumer);
    }
  }

  public static @Nullable PopupMenuGroupProvider getPopupMenuGroupProvider(@Nullable LinkInfo linkInfo) {
    return linkInfo instanceof LinkInfoEx ? ((LinkInfoEx) linkInfo).getPopupMenuGroupProvider() : null;
  }

  @Contract("null -> null")
  public static @Nullable HoverConsumer getHoverConsumer(@Nullable LinkInfo linkInfo) {
    return linkInfo instanceof LinkInfoEx ? ((LinkInfoEx) linkInfo).getHoverConsumer() : null;
  }
}
