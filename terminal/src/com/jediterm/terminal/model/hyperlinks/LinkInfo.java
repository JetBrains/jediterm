package com.jediterm.terminal.model.hyperlinks;

import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author traff
 */
public class LinkInfo {
  private final Runnable myNavigateCallback;
  private final PopupMenuGroupProvider myPopupMenuGroupProvider;

  public LinkInfo(@NotNull Runnable navigateCallback) {
    this(navigateCallback, null);
  }

  private LinkInfo(@NotNull Runnable navigateCallback, @Nullable PopupMenuGroupProvider popupMenuGroupProvider) {
    myNavigateCallback = navigateCallback;
    myPopupMenuGroupProvider = popupMenuGroupProvider;
  }

  public void navigate() {
    myNavigateCallback.run();
  }

  public @Nullable PopupMenuGroupProvider getPopupMenuGroupProvider() {
    return myPopupMenuGroupProvider;
  }

  public interface PopupMenuGroupProvider {
    @NotNull List<TerminalAction> getPopupMenuGroup(@NotNull MouseEvent event);
  }

  public static final class Builder {
    private Runnable myNavigateCallback;
    private PopupMenuGroupProvider myPopupMenuGroupProvider;

    public @NotNull Builder setNavigateCallback(@NotNull Runnable navigateCallback) {
      myNavigateCallback = navigateCallback;
      return this;
    }

    public @NotNull Builder setPopupMenuGroupProvider(@Nullable PopupMenuGroupProvider popupMenuGroupProvider) {
      myPopupMenuGroupProvider = popupMenuGroupProvider;
      return this;
    }

    public @NotNull LinkInfo build() {
      return new LinkInfo(myNavigateCallback, myPopupMenuGroupProvider);
    }
  }
}
