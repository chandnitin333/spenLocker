package com.spendlocker;

import com.spendlocker.config.AppConfig;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;
import com.spendlocker.service.RecurringExpenseService;
import com.spendlocker.ui.LoginDialog;
import com.spendlocker.ui.LoginResult;
import com.spendlocker.ui.SessionGuard;
import com.spendlocker.ui.ShellView;
import com.spendlocker.ui.ThemeManager;
import com.spendlocker.ui.dialog.RecoveryKeyDisplayDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> DatabaseManager.getInstance().close()));
        ThemeManager.LIGHT.apply();
        promptForLogin(primaryStage);
    }

    /** Shows the login dialog; on cancel, quits — nothing about this step is slow enough to need a loader. */
    private void promptForLogin(Stage primaryStage) {
        LoginResult result = LoginDialog.prompt();
        switch (result.type()) {
            case CANCELLED:
                Platform.exit();
                return;
            case ALREADY_UNLOCKED:
                launchMainWindow(primaryStage);
                return;
            case PASSWORD:
                unlockVaultWithLoadingScreen(primaryStage, result.password());
                return;
        }
    }

    /**
     * Decrypting/opening the vault (and running any pending schema migration) can take a
     * noticeable moment, and was previously done directly on the FX thread with zero feedback —
     * the window just sat there looking frozen. Runs it in the background instead, with a
     * visible "Unlocking vault…" screen so a slow open never looks like a hang.
     */
    private void unlockVaultWithLoadingScreen(Stage primaryStage, String password) {
        showLoadingScreen(primaryStage, "Unlocking vault…");
        new Thread(() -> {
            try {
                DatabaseManager.getInstance().open(password);
                Platform.runLater(() -> {
                    showRecoveryKeyIfJustGenerated();
                    launchMainWindow(primaryStage);
                });
            } catch (VaultLockedException e) {
                Platform.runLater(() -> {
                    AlertUtil.error("Unable to unlock vault", e.getMessage());
                    promptForLogin(primaryStage);
                });
            }
        }, "vault-unlock").start();
    }

    private void showLoadingScreen(Stage primaryStage, String message) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(56, 56);
        spinner.setMaxSize(56, 56);
        Label label = new Label(message);
        label.getStyleClass().add("title-3");
        VBox box = new VBox(16, spinner, label);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(420, 280);
        box.getStyleClass().add("shell");

        Scene loadingScene = new Scene(box, 420, 280);
        loadingScene.getStylesheets().add(getClass().getResource("/com/spendlocker/app.css").toExternalForm());
        primaryStage.setTitle("SpendLocker");
        primaryStage.setScene(loadingScene);
        primaryStage.show();
    }

    private void launchMainWindow(Stage primaryStage) {
        ThemeManager.loadSaved(new AppConfig()).apply();
        runDueRecurringExpenses();

        ShellView shell = new ShellView();
        Scene scene = new Scene(shell, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/com/spendlocker/app.css").toExternalForm());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), shell::focusSearch);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), shell::triggerAdd);

        primaryStage.setTitle("SpendLocker — Personal Finance & Document Vault");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> DatabaseManager.getInstance().close());
        primaryStage.show();
        DialogUtil.setPrimaryStage(primaryStage);

        new SessionGuard(scene);
    }

    private void runDueRecurringExpenses() {
        List<String> created = new RecurringExpenseService().processDue();
        if (!created.isEmpty()) {
            AlertUtil.info("Recurring expenses added",
                    created.size() + " recurring expense(s) were added: " + String.join(", ", created));
        }
    }

    private void showRecoveryKeyIfJustGenerated() {
        String recoveryKey = DatabaseManager.getInstance().consumePendingRecoveryKey();
        if (recoveryKey != null) {
            RecoveryKeyDisplayDialog.show(recoveryKey);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
