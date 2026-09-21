package com.spendlocker.ui.dialog;

import com.spendlocker.excel.InvestmentColumnMapping;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/** Lets the user match spreadsheet columns to investment fields before an Excel import. */
public class InvestmentColumnMappingDialog {

    private static final String NONE = "— None —";

    public static Optional<InvestmentColumnMapping> show(List<String> headers) {
        Dialog<InvestmentColumnMapping> dialog = new Dialog<>();
        dialog.setTitle("Map Spreadsheet Columns");
        dialog.setHeaderText("Match each investment field to a column from your file.");
        DialogUtil.center(dialog);

        ButtonType importType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importType, ButtonType.CANCEL);

        ObservableList<String> options = FXCollections.observableArrayList();
        options.add(NONE);
        options.addAll(headers);

        ComboBox<String> nameBox = fieldBox(options, headers, "asset", "name", "instrument");
        ComboBox<String> tickerBox = fieldBox(options, headers, "ticker", "symbol");
        ComboBox<String> typeBox = fieldBox(options, headers, "type", "category");
        ComboBox<String> dateBox = fieldBox(options, headers, "purchase", "date");
        ComboBox<String> principalBox = fieldBox(options, headers, "principal", "invested", "cost");
        ComboBox<String> unitPriceBox = fieldBox(options, headers, "unit price", "price", "nav");
        ComboBox<String> unitsBox = fieldBox(options, headers, "units", "quantity", "qty", "shares");
        ComboBox<String> notesBox = fieldBox(options, headers, "note", "memo", "description");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.addRow(row++, new Label("Asset Name:"), nameBox);
        grid.addRow(row++, new Label("Ticker:"), tickerBox);
        grid.addRow(row++, new Label("Type:"), typeBox);
        grid.addRow(row++, new Label("Purchase Date:"), dateBox);
        grid.addRow(row++, new Label("Principal Amount:"), principalBox);
        grid.addRow(row++, new Label("Current Unit Price:"), unitPriceBox);
        grid.addRow(row++, new Label("Total Units:"), unitsBox);
        grid.addRow(row, new Label("Notes:"), notesBox);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != importType) return null;
            return new InvestmentColumnMapping(
                    headers.indexOf(nameBox.getValue()),
                    headers.indexOf(tickerBox.getValue()),
                    headers.indexOf(typeBox.getValue()),
                    headers.indexOf(dateBox.getValue()),
                    headers.indexOf(principalBox.getValue()),
                    headers.indexOf(unitPriceBox.getValue()),
                    headers.indexOf(unitsBox.getValue()),
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
