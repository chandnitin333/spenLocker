package com.spendlocker.util;

import javafx.scene.control.Alert;

public class AlertUtil {

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        DialogUtil.center(alert);
        alert.showAndWait();
    }
}
