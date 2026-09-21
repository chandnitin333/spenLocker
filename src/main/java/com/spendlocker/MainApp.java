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
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import java.util.List;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> DatabaseManager.getInstance().close()));
        ThemeManager.LIGHT.apply();

        if (!unlockVault()) {
            javafx.application.Platform.exit();
            return;
        }
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

    private boolean unlockVault() {
        while (true) {
            LoginResult result = LoginDialog.prompt();
            switch (result.type()) {
                case CANCELLED:
                    return false;
                case ALREADY_UNLOCKED:
                    return true;
                case PASSWORD:
                    try {
                        DatabaseManager.getInstance().open(result.password());
                        showRecoveryKeyIfJustGenerated();
                        return true;
                    } catch (VaultLockedException e) {
                        AlertUtil.error("Unable to unlock vault", e.getMessage());
                    }
                    break;
            }
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
