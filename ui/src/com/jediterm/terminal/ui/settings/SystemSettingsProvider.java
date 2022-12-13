package com.jediterm.terminal.ui.settings;

import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NotNull;

public interface SystemSettingsProvider {
  @NotNull TerminalActionPresentation getNewSessionActionPresentation();

  @NotNull TerminalActionPresentation getOpenUrlActionPresentation();

  @NotNull TerminalActionPresentation getCopyActionPresentation();

  @NotNull TerminalActionPresentation getPasteActionPresentation();

  @NotNull TerminalActionPresentation getClearBufferActionPresentation();

  @NotNull TerminalActionPresentation getPageUpActionPresentation();

  @NotNull TerminalActionPresentation getPageDownActionPresentation();

  @NotNull TerminalActionPresentation getLineUpActionPresentation();

  @NotNull TerminalActionPresentation getLineDownActionPresentation();

  @NotNull TerminalActionPresentation getCloseSessionActionPresentation();

  @NotNull TerminalActionPresentation getFindActionPresentation();

  @NotNull TerminalActionPresentation getSelectAllActionPresentation();
}
