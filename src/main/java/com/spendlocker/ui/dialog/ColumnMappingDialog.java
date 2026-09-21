package com.spendlocker.ui.dialog;

import com.spendlocker.excel.ColumnMapping;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/** Lets the user match spreadsheet columns to expense fields before an Excel import. */
public class ColumnMappingDialog {

    private static final String NONE = "— None —";

    public static Optional<ColumnMapping> show(List<String> headers) {
        Dialog<ColumnMapping> dialog = new Dialog<>();
        dialog.setTitle("Map Spreadsheet Columns");
        dialog.setHeaderText("Match each expense field to a column from your file.");
        DialogUtil.center(dialog);

        ButtonType importType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importType, ButtonType.CANCEL);

        ObservableList<String> options = FXCollections.observableArrayList();
        options.add(NONE);
        options.addAll(headers);

        ComboBox<String> dateBox = fieldBox(options, headers, "date");
        ComboBox<String> amountBox = fieldBox(options, headers, "amount", "total");
        ComboBox<String> categoryBox = fieldBox(options, headers, "category");
        ComboBox<String> merchantBox = fieldBox(options, headers, "merchant", "vendor", "payee");
        ComboBox<String> paymentMethodBox = fieldBox(options, headers, "payment", "method", "mode");
        ComboBox<String> notesBox = fieldBox(options, headers, "note", "memo", "description");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.addRow(row++, new Label("Date:"), dateBox);
        grid.addRow(row++, new Label("Amount:"), amountBox);
        grid.addRow(row++, new Label("Category:"), categoryBox);
        grid.addRow(row++, new Label("Merchant/Vendor:"), merchantBox);
        grid.addRow(row++, new Label("Payment Method:"), paymentMethodBox);
        grid.addRow(row, new Label("Notes:"), notesBox);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != importType) return null;
            return new ColumnMapping(
                    headers.indexOf(dateBox.getValue()),
                    headers.indexOf(amountBox.getValue()),
                    headers.indexOf(categoryBox.getValue()),
                    headers.indexOf(merchantBox.getValue()),
                    headers.indexOf(paymentMethodBox.getValue()),
                    headers.indexOf(notesBox.getValue()));
        });

        return dialog.showAndWait();
    }

    private static ComboBox<String> fieldBox(ObservableList<String> options, List<String> headers, String... hints) {
        ComboBox<String> box = new ComboBox<>(options);
        box.setValue(guess(headers, hints).orElse(NONE));
        return box;
    }

    private static Optional<String> guess(List<String> headers, String... hints) {
        for (String header : headers) {
            String lower = header.toLowerCase();
            for (String hint : hints) {
                if (lower.contains(hint)) return Optional.of(header);
            }
        }
        return Optional.empty();
    }
}
