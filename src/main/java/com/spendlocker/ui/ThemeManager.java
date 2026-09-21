package com.spendlocker.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.spendlocker.config.AppConfig;
import javafx.application.Application;

public enum ThemeManager {
    LIGHT("light", new PrimerLight().getUserAgentStylesheet()),
    DARK("dark", new PrimerDark().getUserAgentStylesheet());

    private final String key;
    private final String stylesheet;

    ThemeManager(String key, String stylesheet) {
        this.key = key;
        this.stylesheet = stylesheet;
    }

    public String key() {
        return key;
    }

    public void apply() {
        Application.setUserAgentStylesheet(stylesheet);
    }

    public static ThemeManager fromKey(String key) {
        for (ThemeManager theme : values()) {
            if (theme.key.equals(key)) return theme;
        }
        return LIGHT;
    }

    public static ThemeManager loadSaved(AppConfig config) {
        return fromKey(config.get(AppConfig.KEY_THEME));
    }
}
