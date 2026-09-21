package com.spendlocker.ui.dialog;

import com.spendlocker.dao.DocumentCategoryDao;
import com.spendlocker.dao.DocumentDao;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Prompts for a category when importing a document, with the same "+" add-new flow as elsewhere. */
public class DocumentCategoryDialog {

    private static final String[] DEFAULT_CATEGORIES = {
        "Education", "Company", "Home", "Personal", "Finance", "Legal", "Medical", "Other"
    };

    public static Optional<String> show(String fileName) {
        DocumentCategoryDao documentCategoryDao = new DocumentCategoryDao();
        DocumentDao documentDao = new DocumentDao();

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Document Category");
        dialog.setHeaderText("Categorize \"" + fileName + "\" (optional)");
        DialogUtil.center(dialog);

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Set<String> options = new LinkedHashSet<>(List.of(DEFAULT_CATEGORIES));
        options.addAll(documentCategoryDao.findAll());
        options.addAll(documentDao.distinctCategories());
        ComboBox<String> categoryField = new ComboBox<>(FXCollections.observableArrayList(options));
        categoryField.setEditable(true);
        categoryField.setPromptText("Select or type a category");
        categoryField.setMaxWidth(Double.MAX_VALUE);

        Button addButton = new Button(null, new FontIcon(Feather.PLUS_CIRCLE));
        addButton.getStyleClass().add("icon-button");
        addButton.setTooltip(new Tooltip("Add a new category"));
        addButton.setOnAction(e -> QuickAddDialog.prompt("Add Document Category", "Category name:")
                .ifPresent(name -> {
                    boolean alreadyKnown = categoryField.getItems().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
                    if (!alreadyKnown) {
                        documentCategoryDao.addIfAbsent(name);
                        categoryField.getItems().add(name);
                    }
                    categoryField.setValue(name);
                }));

        HBox row = new HBox(8, categoryField, addButton);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, row);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(400);

        dialog.setResultConverter(button -> {
            if (button != okType) return null;
            String category = categoryField.getEditor().getText();
            if (category != null && !category.isBlank()) {
                documentCategoryDao.addIfAbsent(category);
            }
            return category;
        });

        return dialog.showAndWait();
    }
}
