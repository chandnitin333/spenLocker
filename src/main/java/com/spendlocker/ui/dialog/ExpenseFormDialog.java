package com.spendlocker.ui.dialog;

import com.spendlocker.dao.CategoryDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.MerchantDao;
import com.spendlocker.model.Expense;
import com.spendlocker.model.PaymentMethod;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Add/Edit form for a single expense. Pass {@code null} for a blank "Add Expense" form,
 * an already-saved {@link Expense} (id &gt; 0) to edit it in place, or a not-yet-saved
 * {@link Expense} draft (id == 0, e.g. built from OCR-detected receipt fields) to open
 * a pre-filled "Add Expense" form the user can review before it is inserted.
 */
public class ExpenseFormDialog {

    private static final String[] DEFAULT_CATEGORIES = {
        "Groceries", "Rent", "Utilities", "Dining", "Transportation",
        "Entertainment", "Healthcare", "Shopping", "Travel", "Insurance",
        "Education", "Subscriptions", "Other"
    };
    private static final int TOP_MERCHANTS_SHOWN = 4;

    public static Optional<Expense> show(Expense initial) {
        boolean prefill = initial != null;
        boolean editingExistingRecord = prefill && initial.getId() > 0;
        ExpenseDao expenseDao = new ExpenseDao();
        CategoryDao categoryDao = new CategoryDao();
        MerchantDao merchantDao = new MerchantDao();
        Dialog<Expense> dialog = new Dialog<>();
        dialog.setTitle(editingExistingRecord ? "Edit Expense" : "Add Expense");
        DialogUtil.center(dialog);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        DatePicker dateField = new DatePicker(
                prefill && initial.getTransactionDate() != null ? LocalDate.parse(initial.getTransactionDate()) : LocalDate.now());
        TextField amountField = new TextField(prefill && initial.getAmount() != 0 ? String.valueOf(initial.getAmount()) : "");

        Set<String> categoryOptions = new LinkedHashSet<>();
        categoryOptions.addAll(List.of(DEFAULT_CATEGORIES));
        categoryOptions.addAll(categoryDao.findAll());
        categoryOptions.addAll(expenseDao.distinctCategories());
        ComboBox<String> categoryField = new ComboBox<>(FXCollections.observableArrayList(categoryOptions));
        categoryField.setEditable(true);
        categoryField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(categoryField, Priority.ALWAYS);
        if (prefill) categoryField.setValue(initial.getCategory());

        Button addCategoryButton = new Button(null, new FontIcon(Feather.PLUS_CIRCLE));
        addCategoryButton.getStyleClass().add("icon-button");
        addCategoryButton.setTooltip(new Tooltip("Add a new category"));
        addCategoryButton.setOnAction(e -> QuickAddDialog.prompt("Add Category", "Category name:")
                .ifPresent(name -> {
                    boolean alreadyKnown = categoryField.getItems().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
                    if (!alreadyKnown) {
                        categoryDao.addIfAbsent(name);
                        categoryField.getItems().add(name);
                    }
                    categoryField.setValue(name);
                }));
        HBox categoryRow = new HBox(8, categoryField, addCategoryButton);
        categoryRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Frequency-ranked, so the merchants/payment methods actually used most often surface first.
        ComboBox<String> merchantField = new ComboBox<>(FXCollections.observableArrayList(expenseDao.topMerchants(TOP_MERCHANTS_SHOWN)));
        merchantField.setEditable(true);
        merchantField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(merchantField, Priority.ALWAYS);
        if (prefill) merchantField.setValue(initial.getMerchantOrVendor());

        Button addMerchantButton = new Button(null, new FontIcon(Feather.PLUS_CIRCLE));
        addMerchantButton.getStyleClass().add("icon-button");
        addMerchantButton.setTooltip(new Tooltip("Add a new merchant/vendor"));
        addMerchantButton.setOnAction(e -> QuickAddDialog.prompt("Add Merchant/Vendor", "Merchant or vendor name:")
                .ifPresent(name -> {
                    boolean alreadyKnown = merchantField.getItems().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
                    if (!alreadyKnown) {
                        merchantDao.addIfAbsent(name);
                        merchantField.getItems().add(name);
                    }
                    merchantField.setValue(name);
                }));
        HBox merchantRow = new HBox(8, merchantField, addMerchantButton);
        merchantRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        List<PaymentMethod> rankedMethods = expenseDao.paymentMethodsByUsage();
        ComboBox<PaymentMethod> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList(rankedMethods));
        paymentMethodBox.setValue(prefill && initial.getPaymentMethod() != null
                ? initial.getPaymentMethod()
                : rankedMethods.get(0));

        TextArea notesField = new TextArea(prefill ? initial.getNotes() : "");
        notesField.setPrefRowCount(3);
        notesField.setWrapText(true);

        for (Control field : new Control[] {dateField, amountField, paymentMethodBox, notesField}) {
            field.setMaxWidth(Double.MAX_VALUE);
            field.setPrefWidth(280);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(130);
        labelColumn.setHalignment(HPos.RIGHT);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setMinWidth(280);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.addRow(row++, new Label("Date:"), dateField);
        grid.addRow(row++, new Label("Amount:"), amountField);
        grid.addRow(row++, new Label("Category:"), categoryRow);
        grid.addRow(row++, new Label("Merchant/Vendor:"), merchantRow);
        grid.addRow(row++, new Label("Payment Method:"), paymentMethodBox);
        grid.addRow(row, new Label("Notes:"), notesField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(460);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDisable(categoryField.getEditor().getText().isBlank());
        categoryField.getEditor().textProperty().addListener((obs, o, v) -> saveButton.setDisable(v.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            String category = categoryField.getEditor().getText();
            categoryDao.addIfAbsent(category);
            String merchant = merchantField.getEditor().getText();
            if (merchant != null && !merchant.isBlank()) {
                merchantDao.addIfAbsent(merchant);
            }
            Expense expense = prefill ? initial : new Expense();
            expense.setTransactionDate(dateField.getValue().toString());
            expense.setAmount(parseAmount(amountField.getText()));
            expense.setCategory(category);
            expense.setMerchantOrVendor(merchant);
            expense.setPaymentMethod(paymentMethodBox.getValue());
            expense.setNotes(notesField.getText());
            return expense;
        });

        return dialog.showAndWait();
    }

    private static double parseAmount(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
