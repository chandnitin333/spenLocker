package com.spendlocker.ui.dialog;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;
import com.spendlocker.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

/** Requires the current master password before re-encrypting the vault with a new one. */
public class ChangePasswordDialog {

    public static void show() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Change Master Password");
        dialog.setHeaderText("Enter your current password and choose a new one.");
        DialogUtil.center(dialog);

        ButtonType changeType = new ButtonType("Change Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeType, ButtonType.CANCEL);

        PasswordField currentField = new PasswordField();
        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("badge-negative");
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Current Password:"), currentField);
        grid.addRow(1, new Label("New Password:"), newField);
        grid.addRow(2, new Label("Confirm New Password:"), confirmField);
        grid.add(errorLabel, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Node changeButton = dialog.getDialogPane().lookupButton(changeType);
        changeButton.setDisable(true);
        Runnable validate = () -> changeButton.setDisable(
                currentField.getText().isBlank()
                        || newField.getText().isBlank()
                        || !newField.getText().equals(confirmField.getText()));
        currentField.textProperty().addListener((o, a, b) -> validate.run());
        newField.textProperty().addListener((o, a, b) -> validate.run());
        confirmField.textProperty().addListener((o, a, b) -> validate.run());

        // Consuming the ACTION event keeps the dialog open when verification/rekey fails.
        changeButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            DatabaseManager manager = DatabaseManager.getInstance();
            if (!manager.verifyPassphrase(currentField.getText())) {
                errorLabel.setText("Current password is incorrect.");
                event.consume();
                return;
            }
            try {
                manager.changePassphrase(newField.getText());
            } catch (VaultLockedException e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == changeType);

        if (Boolean.TRUE.equals(dialog.showAndWait().orElse(false))) {
            Alert success = new Alert(Alert.AlertType.INFORMATION, "Master password changed.");
            success.setTitle("Success");
            success.setHeaderText(null);
            DialogUtil.center(success);
            success.showAndWait();
        }
    }
}
