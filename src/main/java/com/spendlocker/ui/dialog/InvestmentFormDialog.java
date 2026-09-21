package com.spendlocker.ui.dialog;

import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.dao.InvestmentTypeDao;
import com.spendlocker.model.Investment;
import com.spendlocker.model.InvestmentType;
import com.spendlocker.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import java.util.Optional;
import java.util.Set;

public class InvestmentFormDialog {

    public static Optional<Investment> show(Investment existing) {
        boolean editing = existing != null;
        InvestmentTypeDao investmentTypeDao = new InvestmentTypeDao();
        InvestmentDao investmentDao = new InvestmentDao();
        Dialog<Investment> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit Investment" : "Add Investment");
        DialogUtil.center(dialog);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(editing ? existing.getAssetName() : "");
        TextField tickerField = new TextField(editing ? existing.getAssetTicker() : "");

        Set<String> typeOptions = new LinkedHashSet<>();
        for (InvestmentType type : InvestmentType.values()) {
            typeOptions.add(type.dbValue());
        }
        typeOptions.addAll(investmentTypeDao.findAll());
        typeOptions.addAll(investmentDao.distinctTypes());
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(typeOptions));
        typeBox.setEditable(true);
        typeBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(typeBox, Priority.ALWAYS);
        typeBox.setValue(editing ? existing.getInvestmentType() : InvestmentType.STOCKS.dbValue());

        Button addTypeButton = new Button(null, new FontIcon(Feather.PLUS_CIRCLE));
        addTypeButton.getStyleClass().add("icon-button");
        addTypeButton.setTooltip(new Tooltip("Add a new investment type"));
        addTypeButton.setOnAction(e -> QuickAddDialog.prompt("Add Investment Type", "Investment type name:")
                .ifPresent(name -> {
                    boolean alreadyKnown = typeBox.getItems().stream().anyMatch(existingType -> existingType.equalsIgnoreCase(name));
                    if (!alreadyKnown) {
                        investmentTypeDao.addIfAbsent(name);
                        typeBox.getItems().add(name);
                    }
                    typeBox.setValue(name);
                }));
        HBox typeRow = new HBox(8, typeBox, addTypeButton);
        typeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        DatePicker purchaseDateField = new DatePicker(editing ? LocalDate.parse(existing.getPurchaseDate()) : LocalDate.now());
        TextField principalField = new TextField(editing ? String.valueOf(existing.getPrincipalAmount()) : "");
        TextField unitPriceField = new TextField(editing ? String.valueOf(existing.getCurrentUnitPrice()) : "0.0");
        TextField unitsField = new TextField(editing ? String.valueOf(existing.getTotalUnits()) : "1.0");
        TextArea notesField = new TextArea(editing ? existing.getNotes() : "");
        notesField.setPrefRowCount(3);
        notesField.setWrapText(true);

        for (Control field : new Control[] {nameField, tickerField, purchaseDateField,
                principalField, unitPriceField, unitsField, notesField}) {
            field.setMaxWidth(Double.MAX_VALUE);
            field.setPrefWidth(280);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        labelColumn.setHalignment(HPos.RIGHT);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setMinWidth(280);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        int row = 0;
        grid.addRow(row++, fieldLabel("Asset Name:",
                "The name of the stock, fund, property, or other asset (e.g. \"Apple Inc.\" or \"Downtown Rental Property\")."), nameField);
        grid.addRow(row++, fieldLabel("Ticker:",
                "The stock/fund trading symbol, if it has one (e.g. AAPL). Leave blank for things like real estate or fixed deposits."), tickerField);
        grid.addRow(row++, fieldLabel("Type:",
                "The category of investment — Stocks, Mutual Funds, Crypto, Real Estate, Fixed Deposit, Bonds, Gold, or your own custom type."), typeRow);
        grid.addRow(row++, fieldLabel("Purchase Date:",
                "The date you originally bought or invested in this asset."), purchaseDateField);
        grid.addRow(row++, fieldLabel("Principal Amount:",
                "The total amount of money you originally put in — your cost basis."), principalField);
        grid.addRow(row++, fieldLabel("Current Unit Price:",
                "What ONE unit/share is worth right now. For a lump-sum asset like real estate or a fixed deposit, put its current total value here and set Total Units to 1."), unitPriceField);
        grid.addRow(row++, fieldLabel("Total Units:",
                "How many units/shares you own. Current Value = Current Unit Price × Total Units. For a single lump-sum asset, use 1."), unitsField);
        grid.addRow(row, fieldLabel("Notes:", "Anything else worth remembering about this investment."), notesField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(480);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.setDisable(nameField.getText().isBlank());
        nameField.textProperty().addListener((obs, o, v) -> saveButton.setDisable(v.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            String type = typeBox.getEditor().getText();
            investmentTypeDao.addIfAbsent(type);
            Investment inv = editing ? existing : new Investment();
            inv.setAssetName(nameField.getText());
            inv.setAssetTicker(tickerField.getText());
            inv.setInvestmentType(type);
            inv.setPurchaseDate(purchaseDateField.getValue().toString());
            inv.setPrincipalAmount(parseDouble(principalField.getText()));
            inv.setCurrentUnitPrice(parseDouble(unitPriceField.getText()));
            inv.setTotalUnits(parseDouble(unitsField.getText()));
            inv.setNotes(notesField.getText());
            return inv;
        });

        return dialog.showAndWait();
    }

    private static Node fieldLabel(String text, String tooltipText) {
        Label label = new Label(text);
        FontIcon info = new FontIcon(Feather.INFO);
        info.getStyleClass().add("info-icon");
        Tooltip.install(info, new Tooltip(tooltipText));
        HBox box = new HBox(4, label, info);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
