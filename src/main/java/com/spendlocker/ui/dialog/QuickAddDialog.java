package com.spendlocker.ui.dialog;

import com.spendlocker.util.DialogUtil;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

/** A small "add new value" popup shared by the Category / Merchant / Investment Type "+" buttons. */
public class QuickAddDialog {

    public static Optional<String> prompt(String title, String promptText) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(promptText);
        DialogUtil.center(dialog);
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty());
    }
}
