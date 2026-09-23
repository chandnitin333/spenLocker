package com.spendlocker.ui;

import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;
import com.spendlocker.security.TouchIdService;
import com.spendlocker.ui.dialog.PrivacyPolicyDialog;
import com.spendlocker.ui.dialog.RecoveryKeyPromptDialog;
import com.spendlocker.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/** First-run "create master password" / returning "unlock vault" dialog, styled as a centered
 *  card (icon + wordmark + spacious fields) rather than a bare label/field grid. */
public class LoginDialog {

    public static LoginResult prompt() {
        boolean firstRun = !DatabaseManager.vaultExists();

        // New vaults require reading and accepting the privacy policy before registration
        // continues — returning users already accepted this when their vault was created.
        if (firstRun && !PrivacyPolicyDialog.promptForAcceptance()) {
            return LoginResult.cancelled();
        }

        Dialog<LoginResult> dialog = new Dialog<>();
        com.spendlocker.util.DialogUtil.styleWith(dialog.getDialogPane());
        dialog.setTitle("Wealth Book");

        ButtonType unlockButtonType = new ButtonType(firstRun ? "Create Vault" : "Unlock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlockButtonType, ButtonType.CANCEL);

        MaskedField password = new MaskedField();
        MaskedField confirm = new MaskedField();
        for (MaskedField field : new MaskedField[] {password, confirm}) {
            field.setPrefWidth(320);
            field.setPrefHeight(38);
        }

        CheckBox showPassword = new CheckBox("Show password");
        showPassword.selectedProperty().addListener((obs, old, show) -> {
            password.setRevealed(show);
            confirm.setRevealed(show);
        });

        // Wordmark header: the same lock icon + "Wealth Book" wordmark the sidebar uses, plus a
        // per-mode subtitle — a real branded header instead of a bare dialog headerText line.
        FontIcon lockIcon = new FontIcon(Feather.LOCK);
        lockIcon.setIconSize(30);
        lockIcon.getStyleClass().add("brand-icon");

        Label wealthLabel = new Label("Wealth ");
        wealthLabel.getStyleClass().add("brand-title");
        wealthLabel.setStyle("-fx-font-size: 26px;");
        Label bookLabel = new Label("Book");
        bookLabel.getStyleClass().addAll("brand-title", "brand-title-accent");
        bookLabel.setStyle("-fx-font-size: 26px;");
        HBox wordmark = new HBox(wealthLabel, bookLabel);
        wordmark.setAlignment(Pos.BASELINE_CENTER);

        Label subtitle = new Label(firstRun
                ? "Create a master password for your new encrypted vault"
                : "Enter your master password to unlock the vault");
        subtitle.getStyleClass().add("text-caption");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(320);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox header = new VBox(6, lockIcon, wordmark, subtitle);
        header.setAlignment(Pos.CENTER);

        VBox fieldsBox = new VBox(10, password);
        fieldsBox.setAlignment(Pos.CENTER);
        if (firstRun) {
            fieldsBox.getChildren().add(confirm);
        }
        HBox showPasswordRow = new HBox(showPassword);
        showPasswordRow.setAlignment(Pos.CENTER);
        fieldsBox.getChildren().add(showPasswordRow);

        VBox secondary = new VBox(10);
        secondary.setAlignment(Pos.CENTER);
        if (!firstRun) {
            Hyperlink forgotLink = new Hyperlink("Forgot your master password?");
            forgotLink.setOnAction(e -> onForgotPassword(dialog));
            secondary.getChildren().add(forgotLink);
            addTouchIdButtonIfAvailable(dialog, secondary);
        }

        VBox content = new VBox(20, header, fieldsBox, secondary);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28, 32, 16, 32));
        dialog.getDialogPane().setContent(content);

        Node unlockButton = dialog.getDialogPane().lookupButton(unlockButtonType);
        unlockButton.setDisable(true);
        password.textProperty().addListener((obs, old, val) ->
                unlockButton.setDisable(val == null || val.isBlank()
                        || (firstRun && !val.equals(confirm.getText()))));
        confirm.textProperty().addListener((obs, old, val) ->
                unlockButton.setDisable(val == null
                        || !val.equals(password.getText()) || password.getText().isBlank()));

        dialog.setResultConverter(button ->
                button == unlockButtonType ? LoginResult.password(password.getText()) : LoginResult.cancelled());
        return dialog.showAndWait().orElse(LoginResult.cancelled());
    }

    private static void addTouchIdButtonIfAvailable(Dialog<LoginResult> dialog, VBox secondary) {
        TouchIdService touchId = new TouchIdService();
        if (!touchId.isEnrolled()) return; // avoid the (slow, first-run) compile check when it's never been set up

        Button touchIdButton = new Button("Unlock with Touch ID", new FontIcon(Feather.LOCK));
        touchIdButton.getStyleClass().add("accent");
        touchIdButton.setMaxWidth(Double.MAX_VALUE);
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

        secondary.getChildren().add(touchIdButton);
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
