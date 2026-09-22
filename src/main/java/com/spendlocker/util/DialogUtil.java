package com.spendlocker.util;

import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Keeps every popup (Dialog/Alert/TextInputDialog/Stage) centered over the main application
 * window instead of wherever JavaFX's screen-relative default positioning happens to land it,
 * and — since this is the one helper nearly every dialog in the app already calls — also makes
 * sure every popup carries the app's own ledger-theme stylesheet. AtlantaFX's base theme is
 * applied JVM-wide via Application.setUserAgentStylesheet, but app.css (colors, fonts, button
 * shapes) is only added to whichever Scene's stylesheets list it's added to; dialogs get their
 * own internal Scene that app.css was never added to, so without this they render in AtlantaFX's
 * un-themed defaults instead of matching the main window.
 */
public final class DialogUtil {

    private static final String APP_STYLESHEET =
            DialogUtil.class.getResource("/com/spendlocker/app.css").toExternalForm();

    private static Stage primaryStage;

    private DialogUtil() {
    }

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /** Adds the app's stylesheet to any Parent that has its own Scene (Dialog panes, popup roots). */
    public static void styleWith(javafx.scene.Parent root) {
        if (!root.getStylesheets().contains(APP_STYLESHEET)) {
            root.getStylesheets().add(APP_STYLESHEET);
        }
    }

    /** Centers any Dialog (including Alert and TextInputDialog, which are subclasses) over the main window. */
    public static void center(Dialog<?> dialog) {
        styleWith(dialog.getDialogPane());
        if (primaryStage == null || !primaryStage.isShowing()) return;
        if (dialog.getOwner() == null) {
            dialog.initOwner(primaryStage);
        }
        dialog.setOnShowing(e -> {
            Window w = dialog.getDialogPane().getScene().getWindow();
            centerOverPrimary(w, w.getWidth(), w.getHeight());
        });
    }

    /** Centers a plain Stage-based popup over the main window. */
    public static void center(Stage stage) {
        // The scene may not exist yet at call time (some dialogs call this before building
        // their content) — style whenever it does, rather than assuming call-site ordering.
        if (stage.getScene() != null) {
            styleWith(stage.getScene().getRoot());
        } else {
            stage.sceneProperty().addListener((obs, old, scene) -> {
                if (scene != null) styleWith(scene.getRoot());
            });
        }
        if (primaryStage == null || !primaryStage.isShowing()) return;
        if (stage.getOwner() == null) {
            stage.initOwner(primaryStage);
        }
        stage.setOnShowing(e -> {
            double width = stage.getWidth();
            double height = stage.getHeight();
            if (Double.isNaN(width) || width <= 0) {
                width = stage.getScene() != null ? stage.getScene().getWidth() : 0;
            }
            if (Double.isNaN(height) || height <= 0) {
                height = stage.getScene() != null ? stage.getScene().getHeight() : 0;
            }
            centerOverPrimary(stage, width, height);
        });
    }

    private static void centerOverPrimary(Window w, double width, double height) {
        if (width <= 0 || height <= 0) return;
        w.setX(primaryStage.getX() + (primaryStage.getWidth() - width) / 2);
        w.setY(primaryStage.getY() + (primaryStage.getHeight() - height) / 2);
    }
}
