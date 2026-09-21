package com.spendlocker.config;

import com.spendlocker.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Key/value settings persisted in the encrypted vault (app_config table). */
public class AppConfig {

    public static final String KEY_GOOGLE_ACCOUNT_EMAIL = "google.account.email";
    public static final String KEY_IMAP_HOST = "imap.host";
    public static final String KEY_IMAP_USER = "imap.user";
    public static final String KEY_IMAP_FOLDER = "imap.folder";
    public static final String KEY_IMAP_SUBJECT_FILTER = "imap.filter.subject";
    public static final String KEY_IMAP_FROM_FILTER = "imap.filter.from";
    public static final String KEY_THEME = "ui.theme";
    public static final String KEY_SESSION_TIMEOUT_MINUTES = "session.timeout.minutes";

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public String get(String key) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT config_value FROM app_config WHERE config_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read config " + key, e);
        }
    }

    public void set(String key, String value) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO app_config (config_key, config_value) VALUES (?, ?) " +
                "ON CONFLICT(config_key) DO UPDATE SET config_value = excluded.config_value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write config " + key, e);
        }
    }
}
