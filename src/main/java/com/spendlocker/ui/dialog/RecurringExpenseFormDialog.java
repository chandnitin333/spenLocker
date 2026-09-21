package com.spendlocker.ui.dialog;

import com.spendlocker.dao.CategoryDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.model.PaymentMethod;
import com.spendlocker.model.RecurrenceFrequency;
import com.spendlocker.model.RecurringExpense;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class RecurringExpenseFormDialog {

    public static Optional<RecurringExpense> show(RecurringExpense existing) {
        boolean editing = existing != null;
        ExpenseDao expenseDao = new ExpenseDao();
        CategoryDao categoryDao = new CategoryDao();

        Dialog<RecurringExpense> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit Recurring Expense" : "Add Recurring Expense");
        DialogUtil.center(dialog);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Set<String> categoryOptions = new LinkedHashSet<>(categoryDao.findAll());
        categoryOptions.addAll(expenseDao.distinctCategories());
        ComboBox<String> categoryField = new ComboBox<>(FXCollections.observableArrayList(categoryOptions));
        categoryField.setEditable(true);
        if (editing) categoryField.setValue(existing.getCategory());

        TextField amountField = new TextField(editing ? String.valueOf(existing.getAmount()) : "");
        TextField merchantField = new TextField(editing ? existing.getMerchantOrVendor() : "");
        ComboBox<PaymentMethod> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentMethodBox.setValue(editing && existing.getPaymentMethod() != null ? existing.getPaymentMethod() : PaymentMethod.CASH);
        ComboBox<RecurrenceFrequency> frequencyBox = new ComboBox<>(FXCollections.observableArrayList(RecurrenceFrequency.values()));
        frequencyBox.setValue(editing ? existing.getFrequency() : RecurrenceFrequency.MONTHLY);
        DatePicker nextDueField = new DatePicker(editing ? LocalDate.parse(existing.getNextDueDate()) : LocalDate.now());
        TextArea notesField = new TextArea(editing ? existing.getNotes() : "");
        notesField.setPrefRowCount(2);
        notesField.setWrapText(true);

        for (Control field : new Control[] {categoryField, amountField, merchantField, paymentMethodBox,
                frequencyBox, nextDueField, notesField}) {
            field.setMaxWidth(Double.MAX_VALUE);
            field.setPrefWidth(260);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(140);
        labelColumn.setHalignment(HPos.RIGHT);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.addRow(row++, new Label("Category:"), categoryField);
        grid.addRow(row++, new Label("Amount:"), amountField);
        grid.addRow(row++, new Label("Merchant/Vendor:"), merchantField);
        grid.addRow(row++, new Label("Payment Method:"), paymentMethodBox);
        grid.addRow(row++, new Label("Frequency:"), frequencyBox);
        grid.addRow(row++, new Label("Next Due Date:"), nextDueField);
        grid.addRow(row, new Label("Notes:"), notesField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(460);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDisable(categoryField.getEditor().getText().isBlank());
        categoryField.getEditor().textProperty().addListener((o, a, b) -> saveButton.setDisable(b.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            String category = categoryField.getEditor().getText();
            categoryDao.addIfAbsent(category);
            RecurringExpense r = editing ? existing : new RecurringExpense();
            r.setCategory(category);
            r.setAmount(parseAmount(amountField.getText()));
            r.setMerchantOrVendor(merchantField.getText());
            r.setPaymentMethod(paymentMethodBox.getValue());
            r.setFrequency(frequencyBox.getValue());
            r.setNextDueDate(nextDueField.getValue().toString());
            r.setNotes(notesField.getText());
            return r;
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
