package com.jediterm.terminal.ui.settings;

public class SettingsUtil {

  public static SettingsProvider getDelegateSettingsProvider(SystemSettingsProvider ssp, UserSettingsProvider usp) {
    return new DelegateSettingsProvider(ssp, usp);
  }

  public static SettingsProvider getDelegateSettingsProvider(SystemSettingsProvider ssp) {
    return new DelegateSettingsProvider(ssp, getDefaultUserSettingsProvider());
  }

  public static SettingsProvider getDelegateSettingsProvider(UserSettingsProvider usp) {
    return new DelegateSettingsProvider(getDefaultSystemSettingsProvider(), usp);
  }

  public static SettingsProvider getDefaultSettingsProvider() {
    return getDelegateSettingsProvider(getDefaultSystemSettingsProvider(), getDefaultUserSettingsProvider());
  }

  public static SystemSettingsProvider getDefaultSystemSettingsProvider() {
    return new DefaultSystemSettingsProvider();
  }

  public static UserSettingsProvider getDefaultUserSettingsProvider() {
    return new DefaultUserSettingsProvider();
  }

}
