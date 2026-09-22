package com.spendlocker.ui;

import com.spendlocker.backup.BackupService;
import com.spendlocker.config.AppConfig;
import com.spendlocker.dao.BudgetDao;
import com.spendlocker.dao.RecurringExpenseDao;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.email.EmailSyncService;
import com.spendlocker.google.GoogleDriveService;
import com.spendlocker.model.RecurringExpense;
import com.spendlocker.security.TouchIdService;
import com.spendlocker.ui.dialog.BudgetFormDialog;
import com.spendlocker.ui.dialog.ChangePasswordDialog;
import com.spendlocker.ui.dialog.RecurringExpenseFormDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public class SettingsView extends VBox {

    private final AppConfig appConfig = new AppConfig();
    private final EmailSyncService emailSyncService = new EmailSyncService();
    private final GoogleDriveService googleDriveService = GoogleDriveService.getInstance();
    private final BackupService backupService = new BackupService();
    private final BudgetDao budgetDao = new BudgetDao();
    private final RecurringExpenseDao recurringExpenseDao = new RecurringExpenseDao();
    private final TouchIdService touchIdService = new TouchIdService();

    private final Label googleStatusLabel = new Label("Not connected");
    private final Button googleSignInButton = new Button("Sign in with Google");
    private final Button googleSignOutButton = new Button("Disconnect");
    private final ToggleButton lightButton = new ToggleButton("Light");
    private final ToggleButton darkButton = new ToggleButton("Dark");
    private final VBox budgetList = new VBox(8);
    private final VBox recurringList = new VBox(8);
    private Runnable refreshEmailAuthUi = () -> {};

    public SettingsView() {
        setSpacing(20);
        setPadding(new Insets(24));

        Label title = new Label("Settings");
        title.getStyleClass().add("title-1");

        getChildren().addAll(title, buildAppearanceSection(), buildSecuritySection(), buildBudgetsSection(),
                buildRecurringSection(), buildBackupSection(), buildSampleDataSection(),
                buildGoogleDriveSection(), buildEmailSyncSection());
        refreshGoogleStatus();
        refreshBudgetList();
        refreshRecurringList();
    }

    private VBox buildRecurringSection() {
        Label heading = sectionHeading("Recurring Transactions", Feather.REPEAT);
        Label description = new Label("Rent, subscriptions, EMIs — added automatically as an expense each time " +
                "they come due, checked once whenever you open the app.");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        Button addButton = new Button("Add Recurring Expense", new FontIcon(Feather.PLUS));
        addButton.setOnAction(e -> RecurringExpenseFormDialog.show(null).ifPresent(rule -> {
            recurringExpenseDao.insert(rule);
            refreshRecurringList();
        }));

        VBox section = new VBox(10, heading, description, recurringList, addButton);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private void refreshRecurringList() {
        recurringList.getChildren().clear();
        java.util.List<RecurringExpense> rules = recurringExpenseDao.findAll();
        if (rules.isEmpty()) {
            Label empty = new Label("No recurring expenses set yet.");
            empty.getStyleClass().add("text-caption");
            recurringList.getChildren().add(empty);
            return;
        }
        for (RecurringExpense rule : rules) {
            Label label = new Label(String.format("%s: %s every %s (next: %s)",
                    rule.getCategory(), currency(rule.getAmount()), rule.getFrequency(), rule.getNextDueDate()));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeButton = new Button(null, new FontIcon(Feather.X));
            removeButton.getStyleClass().add("icon-button");
            removeButton.setOnAction(e -> {
                recurringExpenseDao.delete(rule.getId());
                refreshRecurringList();
            });
            HBox row = new HBox(8, label, spacer, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            recurringList.getChildren().add(row);
        }
    }

    private VBox buildSecuritySection() {
        Label heading = sectionHeading("Security", Feather.LOCK);
        Label description = new Label("Your vault is encrypted end-to-end with your master password. " +
                "Changing it re-encrypts the vault immediately — there is no recovery if it's forgotten.");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        Button changePasswordButton = new Button("Change Master Password", new FontIcon(Feather.KEY));
        changePasswordButton.setOnAction(e -> ChangePasswordDialog.show());

        Label timeoutLabel = new Label("Auto-lock after:");
        ComboBox<Integer> timeoutBox = new ComboBox<>(FXCollections.observableArrayList(1, 5, 10, 15, 30, 60));
        String savedTimeout = appConfig.get(AppConfig.KEY_SESSION_TIMEOUT_MINUTES);
        timeoutBox.setValue(savedTimeout != null ? Integer.parseInt(savedTimeout) : SessionGuard.timeoutMinutes());
        timeoutBox.setOnAction(e -> {
            appConfig.set(AppConfig.KEY_SESSION_TIMEOUT_MINUTES, String.valueOf(timeoutBox.getValue()));
            SessionGuard.applyNewTimeout();
        });
        HBox timeoutRow = new HBox(8, timeoutLabel, timeoutBox, new Label("minutes of inactivity"));
        timeoutRow.setAlignment(Pos.CENTER_LEFT);

        VBox touchIdRow = buildTouchIdRow();

        VBox section = new VBox(10, heading, description, changePasswordButton, timeoutRow, touchIdRow);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private VBox buildTouchIdRow() {
        if (!touchIdService.isSupported()) {
            return new VBox();
        }

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("text-caption");
        Button toggleButton = new Button();

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            boolean enrolled = touchIdService.isEnrolled();
            statusLabel.setText(enrolled ? "Touch ID unlock is on." : "Touch ID unlock is off.");
            toggleButton.setText(enrolled ? "Disable Touch ID" : "Enable Touch ID");
            toggleButton.setGraphic(new FontIcon(enrolled ? Feather.LOCK : Feather.UNLOCK));
        };
        refresh[0].run();

        toggleButton.setOnAction(e -> {
            if (touchIdService.isEnrolled()) {
                touchIdService.disable();
                refresh[0].run();
                return;
            }
            TextInputDialog prompt = new TextInputDialog();
            prompt.setTitle("Enable Touch ID");
            prompt.setHeaderText("Confirm your current master password to enable Touch ID unlock.");
            DialogUtil.center(prompt);
            PasswordField pf = new PasswordField();
            prompt.getDialogPane().setContent(pf);
            prompt.setResultConverter(button -> button == ButtonType.OK ? pf.getText() : null);
            prompt.showAndWait().ifPresent(pw -> {
                if (!DatabaseManager.getInstance().verifyPassphrase(pw)) {
                    AlertUtil.error("Incorrect password", "That's not your current master password.");
                    return;
                }
                if (touchIdService.enable(pw)) {
                    AlertUtil.info("Touch ID enabled", "You can now unlock SpendLocker with Touch ID.");
                } else {
                    AlertUtil.error("Touch ID", "Couldn't set up Touch ID on this Mac. " +
                            "Make sure it has Touch ID hardware and Xcode Command Line Tools installed.");
                }
                refresh[0].run();
            });
        });

        HBox row = new HBox(10, toggleButton, statusLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(row);
    }

    private VBox buildBudgetsSection() {
        Label heading = sectionHeading("Monthly Budgets", Feather.TARGET);
        Label description = new Label("Set a monthly spending limit per category. " +
                "The Dashboard flags any category that's over or nearing its limit.");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        Button addBudgetButton = new Button("Set Budget", new FontIcon(Feather.PLUS));
        addBudgetButton.setOnAction(e -> BudgetFormDialog.show().ifPresent(pair -> {
            budgetDao.upsert(pair.getKey(), pair.getValue());
            refreshBudgetList();
        }));

        VBox section = new VBox(10, heading, description, budgetList, addBudgetButton);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private void refreshBudgetList() {
        budgetList.getChildren().clear();
        Map<String, Double> budgets = budgetDao.findAll();
        if (budgets.isEmpty()) {
            Label empty = new Label("No budgets set yet.");
            empty.getStyleClass().add("text-caption");
            budgetList.getChildren().add(empty);
            return;
        }
        budgets.forEach((category, limit) -> {
            Label label = new Label(category + ": " + currency(limit) + " / month");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeButton = new Button(null, new FontIcon(Feather.X));
            removeButton.getStyleClass().add("icon-button");
            removeButton.setOnAction(e -> {
                budgetDao.delete(category);
                refreshBudgetList();
            });
            HBox row = new HBox(8, label, spacer, removeButton);
            row.setAlignment(Pos.CENTER_LEFT);
            budgetList.getChildren().add(row);
        });
    }

    private VBox buildBackupSection() {
        Label heading = sectionHeading("Backup & Restore", Feather.ARCHIVE);
        Label description = new Label("Export a full backup (encrypted database + documents, so all your expenses " +
                "and investments are included, not just files) to a .zip — either to a local file, or straight to " +
                "your Google Drive. Restoring replaces everything currently in the vault.");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        Button exportButton = new Button("Export Backup", new FontIcon(Feather.DOWNLOAD));
        exportButton.setOnAction(e -> onExportBackup());
        Button backupToDriveButton = new Button("Backup to Google Drive", new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
        backupToDriveButton.setOnAction(e -> onBackupToDrive(backupToDriveButton));
        Button restoreButton = new Button("Restore from Backup", new FontIcon(Feather.UPLOAD));
        restoreButton.getStyleClass().add("danger");
        restoreButton.setOnAction(e -> onRestoreBackup());

        Label driveHint = new Label("To restore a backup from Drive: use \"Browse Drive\" on the Documents screen to " +
                "download it locally, then Restore from Backup below.");
        driveHint.getStyleClass().add("text-caption");
        driveHint.setWrapText(true);

        HBox actions = new HBox(10, exportButton, backupToDriveButton, restoreButton);
        VBox section = new VBox(10, heading, description, actions, driveHint);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private void onBackupToDrive(Button trigger) {
        java.io.File tempZip;
        try {
            tempZip = java.io.File.createTempFile("spendlocker-backup-", ".zip");
            backupService.exportBackup(tempZip);
        } catch (IOException ex) {
            AlertUtil.error("Backup failed", ex.getMessage());
            return;
        }

        String driveFileName = "spendlocker-backup-" +
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".zip";
        trigger.setDisable(true);
        SessionGuard.suspendAutoLock();
        new Thread(() -> {
            try {
                // A previous sign-in's cached token restores silently here (no browser popup)
                // if still valid — only truly signing in for the first time opens a browser.
                if (!googleDriveService.isSignedIn()) {
                    googleDriveService.signIn();
                }
                googleDriveService.uploadFile(tempZip, "application/zip", driveFileName);
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    trigger.setDisable(false);
                    AlertUtil.info("Backup uploaded", "Uploaded to Google Drive as " + driveFileName);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    trigger.setDisable(false);
                    AlertUtil.error("Backup failed", ex.getMessage());
                });
            } finally {
                tempZip.delete();
            }
        }, "drive-backup").start();
    }

    private void onExportBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export SpendLocker backup");
        chooser.setInitialFileName("spendlocker-backup.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Archive", "*.zip"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File destination = chooser.showSaveDialog(window);
        if (destination == null) return;

        try {
            backupService.exportBackup(destination);
            AlertUtil.info("Backup complete", "Vault backed up to " + destination.getName());
        } catch (IOException ex) {
            AlertUtil.error("Backup failed", ex.getMessage());
        }
    }

    private void onRestoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Restore SpendLocker backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Archive", "*.zip"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File source = chooser.showOpenDialog(window);
        if (source == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This replaces everything currently in the vault with the backup's contents. This cannot be undone. Continue?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText(null);
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        try {
            DatabaseManager.getInstance().close();
            backupService.restoreBackup(source);
            Alert done = new Alert(Alert.AlertType.INFORMATION,
                    "Restore complete. SpendLocker will now close — reopen it and unlock with the restored vault's password.");
            done.setTitle("Restore Complete");
            done.setHeaderText(null);
            DialogUtil.center(done);
            done.showAndWait();
            javafx.application.Platform.exit();
        } catch (IOException ex) {
            AlertUtil.error("Restore failed", ex.getMessage());
        }
    }

    private VBox buildSampleDataSection() {
        Label heading = sectionHeading("Sample Data", Feather.PACKAGE);
        Label description = new Label("Add a set of realistic sample expenses and investments across every " +
                "category and type, for trying the app out.");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        Button loadButton = new Button("Load Sample Data", new FontIcon(Feather.PLUS_CIRCLE));
        loadButton.setOnAction(e -> {
            int count = new com.spendlocker.service.SampleDataService().loadSampleData();
            AlertUtil.info("Sample data loaded", count + " sample rows added across Expenses and Investments.");
        });

        VBox section = new VBox(10, heading, description, loadButton);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private String currency(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount);
    }

    private VBox buildAppearanceSection() {
        Label heading = sectionHeading("Appearance", Feather.SUN);

        ToggleGroup group = new ToggleGroup();
        lightButton.setToggleGroup(group);
        darkButton.setToggleGroup(group);
        ThemeManager current = ThemeManager.loadSaved(appConfig);
        (current == ThemeManager.DARK ? darkButton : lightButton).setSelected(true);

        lightButton.setOnAction(e -> applyTheme(ThemeManager.LIGHT));
        darkButton.setOnAction(e -> applyTheme(ThemeManager.DARK));

        HBox toggle = new HBox(8, lightButton, darkButton);
        toggle.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, heading, toggle);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private void applyTheme(ThemeManager theme) {
        theme.apply();
        appConfig.set(AppConfig.KEY_THEME, theme.key());
    }

    private VBox buildGoogleDriveSection() {
        Label heading = sectionHeading("Google Drive", Feather.CLOUD);
        Label description = new Label(
                "Sign in with your Google account to back up and sync vault documents to Drive.\n" +
                "One-time setup: place your Google Cloud OAuth Desktop-app client secret at\n" +
                GoogleDriveService.clientSecretPath());
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        googleSignInButton.setGraphic(new FontIcon(Feather.LOG_IN));
        googleSignOutButton.setGraphic(new FontIcon(Feather.LOG_OUT));
        googleSignInButton.setOnAction(e -> onGoogleSignIn());
        googleSignOutButton.setOnAction(e -> onGoogleSignOut());

        HBox actions = new HBox(10, googleSignInButton, googleSignOutButton, googleStatusLabel);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, heading, description, actions);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private VBox buildEmailSyncSection() {
        Label heading = sectionHeading("Email Attachment Sync (IMAP)", Feather.MAIL);
        Label description = new Label("Pull receipts and invoices straight from your inbox into the vault. " +
                "Narrow the sync with a subject or sender filter, e.g. subject contains \"Invoice\" or sender contains \"billing@\".");
        description.setWrapText(true);
        description.getStyleClass().add("text-caption");

        String savedHost = appConfig.get(AppConfig.KEY_IMAP_HOST);
        TextField hostField = new TextField(savedHost != null && !savedHost.isBlank() ? savedHost : "imap.gmail.com");
        TextField portField = new TextField("993");
        TextField userField = new TextField(orEmpty(appConfig.get(AppConfig.KEY_IMAP_USER)));
        userField.setPromptText("you@example.com");
        PasswordField passwordField = new PasswordField();
        String savedFolder = appConfig.get(AppConfig.KEY_IMAP_FOLDER);
        TextField folderField = new TextField(savedFolder != null && !savedFolder.isBlank() ? savedFolder : "INBOX");
        TextField subjectFilterField = new TextField(orEmpty(appConfig.get(AppConfig.KEY_IMAP_SUBJECT_FILTER)));
        subjectFilterField.setPromptText("e.g. Invoice, Receipt");
        TextField fromFilterField = new TextField(orEmpty(appConfig.get(AppConfig.KEY_IMAP_FROM_FILTER)));
        fromFilterField.setPromptText("e.g. billing@vendor.com");

        // For Gmail, reuse the "Sign in with Google" session above via IMAP OAuth2 instead of
        // making the user generate a separate app password for a second login.
        CheckBox useGoogleAuthCheck = new CheckBox("Use my connected Google account (no password needed)");
        Label googleAuthStatus = new Label();
        googleAuthStatus.getStyleClass().add("text-caption");
        googleAuthStatus.setWrapText(true);

        refreshEmailAuthUi = () -> {
            boolean gmailHost = isGmailHost(hostField.getText());
            useGoogleAuthCheck.setVisible(gmailHost);
            useGoogleAuthCheck.setManaged(gmailHost);
            googleAuthStatus.setVisible(gmailHost);
            googleAuthStatus.setManaged(gmailHost);
            boolean useGoogleAuth = gmailHost && useGoogleAuthCheck.isSelected();
            userField.setDisable(useGoogleAuth);
            passwordField.setDisable(useGoogleAuth);
            if (!gmailHost) return;
            if (!useGoogleAuth) {
                googleAuthStatus.setText("");
                return;
            }
            String email = googleDriveService.signedInEmail();
            if (email != null) {
                userField.setText(email);
                googleAuthStatus.setText("Signed in as " + email + " — no password needed.");
            } else {
                googleAuthStatus.setText("Not connected yet — click \"Sign in with Google\" above first.");
            }
        };
        useGoogleAuthCheck.setSelected(isGmailHost(hostField.getText()) && googleDriveService.isSignedIn());
        hostField.textProperty().addListener((obs, old, val) -> refreshEmailAuthUi.run());
        useGoogleAuthCheck.setOnAction(e -> refreshEmailAuthUi.run());
        refreshEmailAuthUi.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int row = 0;
        grid.addRow(row++, new Label("Host:"), hostField, new Label("Port:"), portField);
        grid.add(useGoogleAuthCheck, 0, row, 4, 1);
        row++;
        grid.add(googleAuthStatus, 0, row, 4, 1);
        row++;
        grid.addRow(row++, new Label("Username:"), userField);
        grid.addRow(row++, new Label("Password:"), passwordField);
        grid.addRow(row++, new Label("Folder:"), folderField);
        grid.addRow(row++, new Label("Subject contains:"), subjectFilterField);
        grid.addRow(row, new Label("From contains:"), fromFilterField);

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setGraphic(new FontIcon(Feather.LOG_OUT));
        disconnectButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This clears the saved email host, username, and filters from Settings. Continue?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Disconnect Email");
            confirm.setHeaderText(null);
            DialogUtil.center(confirm);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            appConfig.set(AppConfig.KEY_IMAP_HOST, "");
            appConfig.set(AppConfig.KEY_IMAP_USER, "");
            appConfig.set(AppConfig.KEY_IMAP_FOLDER, "");
            appConfig.set(AppConfig.KEY_IMAP_SUBJECT_FILTER, "");
            appConfig.set(AppConfig.KEY_IMAP_FROM_FILTER, "");

            hostField.setText("imap.gmail.com");
            userField.clear();
            passwordField.clear();
            folderField.setText("INBOX");
            subjectFilterField.clear();
            fromFilterField.clear();
            AlertUtil.info("Disconnected", "Email account settings cleared.");
        });

        Button syncButton = new Button("Sync Attachments Now");
        syncButton.setGraphic(new FontIcon(Feather.DOWNLOAD));
        syncButton.setOnAction(e -> {
            appConfig.set(AppConfig.KEY_IMAP_HOST, hostField.getText());
            appConfig.set(AppConfig.KEY_IMAP_USER, userField.getText());
            appConfig.set(AppConfig.KEY_IMAP_FOLDER, folderField.getText());
            appConfig.set(AppConfig.KEY_IMAP_SUBJECT_FILTER, subjectFilterField.getText());
            appConfig.set(AppConfig.KEY_IMAP_FROM_FILTER, fromFilterField.getText());

            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException nfe) {
                AlertUtil.error("Sync failed", "Port must be a number.");
                return;
            }
            String host = hostField.getText();
            String user = userField.getText();
            String password = passwordField.getText();
            String folder = folderField.getText();
            String subjectFilter = subjectFilterField.getText();
            String fromFilter = fromFilterField.getText();
            boolean useGoogleAuth = isGmailHost(host) && useGoogleAuthCheck.isSelected();

            // IMAP connect + fetch can take several seconds (or hang on a bad host/firewall);
            // running it on the FX thread froze the whole app with no feedback, which looked
            // like the feature was simply "not working". Run it in the background instead.
            syncButton.setDisable(true);
            SessionGuard.suspendAutoLock();
            new Thread(() -> {
                try {
                    String effectiveUser = user;
                    String effectivePassword = password;
                    if (useGoogleAuth) {
                        // A previous sign-in's cached token restores silently here (no browser
                        // popup) if still valid — only truly signing in for the first time opens one.
                        if (!googleDriveService.isSignedIn()) {
                            googleDriveService.signIn();
                        }
                        effectiveUser = googleDriveService.signedInEmail();
                        effectivePassword = googleDriveService.getAccessTokenForImap();
                    }
                    var imported = emailSyncService.syncAttachments(
                            host, port, effectiveUser, effectivePassword, folder, true,
                            subjectFilter, fromFilter, useGoogleAuth);
                    javafx.application.Platform.runLater(() -> {
                        SessionGuard.resumeAutoLock();
                        syncButton.setDisable(false);
                        AlertUtil.info("Sync complete", imported.size() + " attachments imported.");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        SessionGuard.resumeAutoLock();
                        syncButton.setDisable(false);
                        AlertUtil.error("Sync failed", ex.getMessage());
                    });
                }
            }, "imap-sync").start();
        });

        HBox buttonRow = new HBox(10, syncButton, disconnectButton);
        VBox section = new VBox(10, heading, description, grid, buttonRow);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(16));
        return section;
    }

    private Label sectionHeading(String text, org.kordamp.ikonli.Ikon icon) {
        Label heading = new Label(text, new FontIcon(icon));
        heading.getStyleClass().add("title-3");
        return heading;
    }

    private void onGoogleSignIn() {
        googleSignInButton.setDisable(true);
        // The browser-based consent step can take a while (and generates no in-app activity),
        // so pause auto-lock for its duration — otherwise the vault can lock itself mid-flow.
        SessionGuard.suspendAutoLock();
        new Thread(() -> {
            try {
                String email = googleDriveService.signIn();
                javafx.application.Platform.runLater(() -> {
                    // The DB connection isn't safe for concurrent cross-thread access, so
                    // the write happens here on the FX thread rather than on this background
                    // OAuth thread, where it could race a DB read the UI is doing at the same
                    // moment (that race is what caused "Failed to write config" reports).
                    appConfig.set(AppConfig.KEY_GOOGLE_ACCOUNT_EMAIL, email);
                    SessionGuard.resumeAutoLock();
                    refreshGoogleStatus();
                    AlertUtil.info("Connected", "Signed in to Google Drive as " + email);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    AlertUtil.error("Google sign-in failed", ex.getMessage());
                    googleSignInButton.setDisable(false);
                });
            }
        }, "google-oauth").start();
    }

    private void onGoogleSignOut() {
        try {
            googleDriveService.signOut();
            appConfig.set(AppConfig.KEY_GOOGLE_ACCOUNT_EMAIL, null);
            refreshGoogleStatus();
        } catch (Exception ex) {
            AlertUtil.error("Sign-out failed", ex.getMessage());
        }
    }

    private void refreshGoogleStatus() {
        String storedEmail = appConfig.get(AppConfig.KEY_GOOGLE_ACCOUNT_EMAIL);
        boolean connected = storedEmail != null && !storedEmail.isBlank();
        googleStatusLabel.setText(connected ? "Connected as " + storedEmail : "Not connected");
        googleSignInButton.setDisable(connected);
        googleSignOutButton.setDisable(!connected);
        refreshEmailAuthUi.run();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isGmailHost(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(Locale.ROOT);
        return h.equals("imap.gmail.com") || h.equals("imap.googlemail.com");
    }
}
