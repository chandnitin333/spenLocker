package com.spendlocker.ui;

import com.spendlocker.dao.DocumentDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.google.GoogleDriveService;
import com.spendlocker.model.Document;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;
import com.spendlocker.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/** Everything soft-deleted from Expenses, Investments, and Documents — restore, or delete forever. */
public class TrashView extends VBox {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final GoogleDriveService driveService = GoogleDriveService.getInstance();

    private final VBox expenseList = new VBox(8);
    private final VBox investmentList = new VBox(8);
    private final VBox documentList = new VBox(8);

    public TrashView() {
        setSpacing(20);
        setPadding(new Insets(24));

        Label title = new Label("Trash", new FontIcon(Feather.TRASH_2));
        title.getStyleClass().add("title-1");
        HBox header = new HBox(title);
        header.getStyleClass().add("page-header");
        header.setPadding(new Insets(0, 0, 12, 0));
        Label description = new Label("Deleted items stay here until you restore them or remove them permanently.");
        description.getStyleClass().add("text-caption");

        getChildren().addAll(header, description,
                section("Expenses", Feather.CREDIT_CARD, expenseList),
                section("Investments", Feather.TRENDING_UP, investmentList),
                section("Documents", Feather.FOLDER, documentList));
        refresh();
    }

    public void refresh() {
        refreshExpenses();
        refreshInvestments();
        refreshDocuments();
    }

    private VBox section(String heading, org.kordamp.ikonli.Ikon icon, VBox list) {
        Label headingLabel = new Label(heading, new FontIcon(icon));
        headingLabel.getStyleClass().add("title-3");
        VBox card = new VBox(10, headingLabel, list);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private void refreshExpenses() {
        expenseList.getChildren().clear();
        List<Expense> deleted = expenseDao.findDeleted();
        if (deleted.isEmpty()) {
            expenseList.getChildren().add(emptyLabel());
            return;
        }
        for (Expense e : deleted) {
            String summary = String.format("%s — %s — %s%s", e.getTransactionDate(), currency(e.getAmount()), e.getCategory(),
                    e.getMerchantOrVendor() != null && !e.getMerchantOrVendor().isBlank() ? " — " + e.getMerchantOrVendor() : "");
            expenseList.getChildren().add(row(summary, e.getDeletedAt(),
                    () -> { expenseDao.restore(e.getId()); refresh(); },
                    () -> { expenseDao.hardDelete(e.getId()); refresh(); }));
        }
    }

    private void refreshInvestments() {
        investmentList.getChildren().clear();
        List<Investment> deleted = investmentDao.findDeleted();
        if (deleted.isEmpty()) {
            investmentList.getChildren().add(emptyLabel());
            return;
        }
        for (Investment inv : deleted) {
            String summary = String.format("%s — %s — %s", inv.getAssetName(), inv.getInvestmentType(), inv.getPurchaseDate());
            investmentList.getChildren().add(row(summary, inv.getDeletedAt(),
                    () -> { investmentDao.restore(inv.getId()); refresh(); },
                    () -> { investmentDao.hardDelete(inv.getId()); refresh(); }));
        }
    }

    private void refreshDocuments() {
        documentList.getChildren().clear();
        List<Document> deleted = documentDao.findDeleted();
        if (deleted.isEmpty()) {
            documentList.getChildren().add(emptyLabel());
            return;
        }
        for (Document d : deleted) {
            documentList.getChildren().add(row(d.getFileName(), d.getDeletedAt(),
                    () -> { documentDao.restore(d.getId()); refresh(); },
                    () -> {
                        if (d.getDriveFileId() != null && !d.getDriveFileId().isBlank()) {
                            try {
                                driveService.deleteFile(d.getDriveFileId());
                            } catch (Exception ex) {
                                com.spendlocker.util.AlertUtil.error("Google Drive",
                                        "Couldn't remove the Drive copy: " + ex.getMessage() + "\nThe local record was still deleted permanently.");
                            }
                        }
                        documentDao.hardDelete(d.getId());
                        refresh();
                    }));
        }
    }

    private Label emptyLabel() {
        Label label = new Label("Nothing here.");
        label.getStyleClass().add("text-caption");
        return label;
    }

    private HBox row(String summary, String deletedAt, Runnable onRestore, Runnable onDeleteForever) {
        Label text = new Label(summary);
        Label deletedLabel = new Label("Deleted " + deletedAt);
        deletedLabel.getStyleClass().add("text-caption");
        VBox textBox = new VBox(2, text, deletedLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button restoreButton = new Button("Restore", new FontIcon(Feather.ROTATE_CCW));
        restoreButton.setOnAction(e -> onRestore.run());

        Button deleteForeverButton = new Button("Delete Forever", new FontIcon(Feather.X_CIRCLE));
        deleteForeverButton.getStyleClass().add("danger");
        deleteForeverButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This permanently removes \"" + summary + "\". This cannot be undone.",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Delete Forever");
            confirm.setHeaderText(null);
            DialogUtil.center(confirm);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                onDeleteForever.run();
            }
        });

        HBox row = new HBox(10, textBox, spacer, restoreButton, deleteForeverButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String currency(double amount) {
        return com.spendlocker.util.MoneyFormat.currency(amount);
    }
}
