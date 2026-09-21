package com.spendlocker.ui.dialog;

import com.spendlocker.dao.CategoryDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class BudgetFormDialog {

    private static final String[] DEFAULT_CATEGORIES = {
        "Groceries", "Rent", "Utilities", "Dining", "Transportation",
        "Entertainment", "Healthcare", "Shopping", "Travel", "Insurance",
        "Education", "Subscriptions", "Other"
    };

    public static Optional<Pair<String, Double>> show() {
        ExpenseDao expenseDao = new ExpenseDao();
        CategoryDao categoryDao = new CategoryDao();

        Dialog<Pair<String, Double>> dialog = new Dialog<>();
        dialog.setTitle("Set Monthly Budget");
        DialogUtil.center(dialog);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Set<String> categoryOptions = new LinkedHashSet<>(java.util.List.of(DEFAULT_CATEGORIES));
        categoryOptions.addAll(categoryDao.findAll());
        categoryOptions.addAll(expenseDao.distinctCategories());
        ComboBox<String> categoryField = new ComboBox<>(FXCollections.observableArrayList(categoryOptions));
        categoryField.setEditable(true);

        TextField limitField = new TextField();
        limitField.setPromptText("Monthly limit, e.g. 500");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Category:"), categoryField);
        grid.addRow(1, new Label("Monthly Limit:"), limitField);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDisable(true);
        Runnable validate = () -> saveButton.setDisable(
                categoryField.getEditor().getText().isBlank() || parseDouble(limitField.getText()) <= 0);
        categoryField.getEditor().textProperty().addListener((o, a, b) -> validate.run());
        limitField.textProperty().addListener((o, a, b) -> validate.run());

        dialog.setResultConverter(button -> button == saveType
                ? new Pair<>(categoryField.getEditor().getText(), parseDouble(limitField.getText()))
                : null);

        return dialog.showAndWait();
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
