package com.spendlocker.ui;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;
import com.spendlocker.security.TouchIdService;
import com.spendlocker.ui.dialog.RecoveryKeyPromptDialog;
import com.spendlocker.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/** First-run "create master password" / returning "unlock vault" dialog. */
public class LoginDialog {

    public static LoginResult prompt() {
        boolean firstRun = !DatabaseManager.vaultExists();

        Dialog<LoginResult> dialog = new Dialog<>();
        dialog.setTitle("SpendLocker");
        dialog.setHeaderText(firstRun
                ? "Create a master password for your new encrypted vault"
                : "Enter your master password to unlock the vault");

        ButtonType unlockButtonType = new ButtonType(firstRun ? "Create Vault" : "Unlock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlockButtonType, ButtonType.CANCEL);

        MaskedField password = new MaskedField();
        MaskedField confirm = new MaskedField();

        CheckBox showPassword = new CheckBox("Show password");
        showPassword.selectedProperty().addListener((obs, old, show) -> {
            password.setRevealed(show);
            confirm.setRevealed(show);
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));
        grid.add(new Label("Password:"), 0, 0);
        grid.add(password, 1, 0);
        int nextRow = 1;
        if (firstRun) {
            grid.add(new Label("Confirm:"), 0, nextRow);
            grid.add(confirm, 1, nextRow++);
        }
        grid.add(showPassword, 1, nextRow++);

        if (!firstRun) {
            Hyperlink forgotLink = new Hyperlink("Forgot your master password?");
            forgotLink.setOnAction(e -> onForgotPassword(dialog));
            grid.add(forgotLink, 1, nextRow++);
        }
        dialog.getDialogPane().setContent(grid);

        Node unlockButton = dialog.getDialogPane().lookupButton(unlockButtonType);
        unlockButton.setDisable(true);
        password.textProperty().addListener((obs, old, val) ->
                unlockButton.setDisable(val == null || val.isBlank()
                        || (firstRun && !val.equals(confirm.getText()))));
        confirm.textProperty().addListener((obs, old, val) ->
                unlockButton.setDisable(val == null
                        || !val.equals(password.getText()) || password.getText().isBlank()));

        if (!firstRun) {
            addTouchIdButtonIfAvailable(dialog, grid, nextRow);
        }

        dialog.setResultConverter(button ->
                button == unlockButtonType ? LoginResult.password(password.getText()) : LoginResult.cancelled());
        return dialog.showAndWait().orElse(LoginResult.cancelled());
    }

    private static void addTouchIdButtonIfAvailable(Dialog<LoginResult> dialog, GridPane grid, int row) {
        TouchIdService touchId = new TouchIdService();
        if (!touchId.isEnrolled()) return; // avoid the (slow, first-run) compile check when it's never been set up

        Button touchIdButton = new Button("Unlock with Touch ID", new FontIcon(Feather.LOCK));
        touchIdButton.getStyleClass().add("accent");
        touchIdButton.setOnAction(e -> {
            touchIdButton.setDisable(true);
            new Thread(() -> {
                Optional<String> password = touchId.unlock();
                javafx.application.Platform.runLater(() -> {
                    if (password.isEmpty()) {
                        touchIdButton.setDisable(false);
                        AlertUtil.error("Touch ID", "Touch ID authentication failed or was canceled.");
                        return;
                    }
                    try {
                        DatabaseManager.getInstance().open(password.get());
                        dialog.setResult(LoginResult.alreadyUnlocked());
                    } catch (VaultLockedException ex) {
                        touchIdButton.setDisable(false);
                        AlertUtil.error("Touch ID", "Stored password no longer unlocks this vault: " + ex.getMessage());
                    }
                });
            }, "touchid-unlock").start();
        });

        HBox row2 = new HBox(touchIdButton);
        grid.add(row2, 1, row);
    }

    private static void onForgotPassword(Dialog<LoginResult> outerDialog) {
        Optional<String> recoveryKey = RecoveryKeyPromptDialog.prompt();
        recoveryKey.ifPresent(key -> {
            try {
                DatabaseManager.getInstance().openWithRecoveryKey(key);
                outerDialog.setResult(LoginResult.alreadyUnlocked());
            } catch (VaultLockedException ex) {
                AlertUtil.error("Recovery failed", ex.getMessage());
            }
        });
    }

    /** A PasswordField and a plain TextField sharing one text value, toggled by a "Show password" box. */
    private static class MaskedField extends StackPane {
        private final PasswordField masked = new PasswordField();
        private final TextField revealed = new TextField();

        MaskedField() {
            masked.setPromptText("Master password");
            revealed.setPromptText("Master password");
            revealed.textProperty().bindBidirectional(masked.textProperty());
            revealed.setVisible(false);
            revealed.setManaged(false);
            getChildren().addAll(masked, revealed);
        }

        void setRevealed(boolean show) {
            masked.setVisible(!show);
            masked.setManaged(!show);
            revealed.setVisible(show);
            revealed.setManaged(show);
        }

        String getText() {
            return masked.getText();
        }

        javafx.beans.property.StringProperty textProperty() {
            return masked.textProperty();
        }
    }
}
