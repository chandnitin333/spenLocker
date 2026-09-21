package com.spendlocker.ui.dialog;

import com.spendlocker.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** Prompts for a recovery key when the master password has been forgotten. */
public class RecoveryKeyPromptDialog {

    public static Optional<String> prompt() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Recover Vault Access");
        dialog.setHeaderText("Enter the recovery key you saved when this vault was created.");
        DialogUtil.center(dialog);

        ButtonType recoverType = new ButtonType("Recover", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(recoverType, ButtonType.CANCEL);

        TextField keyField = new TextField();
        keyField.setPromptText("XXXX-XXXX-XXXX-XXXX-XXXX");
        keyField.setPrefWidth(280);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Recovery key:"), keyField);
        dialog.getDialogPane().setContent(grid);

        var recoverButton = dialog.getDialogPane().lookupButton(recoverType);
        recoverButton.setDisable(true);
        keyField.textProperty().addListener((o, a, b) -> recoverButton.setDisable(b.isBlank()));

        dialog.setResultConverter(button -> button == recoverType ? keyField.getText().trim() : null);
        return dialog.showAndWait();
    }
}
