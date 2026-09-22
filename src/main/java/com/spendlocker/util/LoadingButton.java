package com.spendlocker.util;

import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;

/** Swaps a button's icon for a small spinner while a background action runs, then restores it. */
public final class LoadingButton {

    private static final String ORIGINAL_GRAPHIC_KEY = "loadingButton.originalGraphic";

    private LoadingButton() {
    }

    public static void startLoading(Button button) {
        button.getProperties().put(ORIGINAL_GRAPHIC_KEY, button.getGraphic());
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.setMaxSize(16, 16);
        button.setGraphic(spinner);
        button.setDisable(true);
    }

    public static void stopLoading(Button button) {
        button.setGraphic((javafx.scene.Node) button.getProperties().remove(ORIGINAL_GRAPHIC_KEY));
        button.setDisable(false);
    }
}
