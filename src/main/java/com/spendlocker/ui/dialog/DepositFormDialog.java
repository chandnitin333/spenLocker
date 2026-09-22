package com.spendlocker.ui.dialog;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.model.Compounding;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.InterestPayout;
import com.spendlocker.model.TenureUnit;
import com.spendlocker.util.DialogUtil;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
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
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.Optional;

/** "Add to the book" — mirrors the reference's live-preview FD form. */
public class DepositFormDialog {

    public static Optional<FixedDeposit> show(FixedDeposit existing) {
        boolean editing = existing != null;
        FixedDepositDao fixedDepositDao = new FixedDepositDao();
        Dialog<FixedDeposit> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit Deposit" : "Add to the book");
        DialogUtil.center(dialog);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        ComboBox<String> depositorField = new ComboBox<>(FXCollections.observableArrayList(fixedDepositDao.distinctDepositors()));
        depositorField.setEditable(true);
        depositorField.setValue(editing ? existing.getDepositor() : "");

        ComboBox<String> bankField = new ComboBox<>(FXCollections.observableArrayList(fixedDepositDao.distinctBanks()));
        bankField.setEditable(true);
        bankField.setValue(editing ? existing.getBank() : "");

        TextField fdNumberField = new TextField(editing ? existing.getFdNumber() : "");
        TextField principalField = new TextField(editing ? String.valueOf(existing.getPrincipal()) : "");
        TextField rateField = new TextField(editing ? String.valueOf(existing.getRatePercent()) : "");

        DatePicker startDateField = new DatePicker(editing ? LocalDate.parse(existing.getStartDate()) : LocalDate.now());
        TextField tenureValueField = new TextField(editing ? String.valueOf(existing.getTenureValue()) : "12");
        ComboBox<TenureUnit> tenureUnitField = new ComboBox<>(FXCollections.observableArrayList(TenureUnit.values()));
        tenureUnitField.setValue(editing ? existing.getTenureUnit() : TenureUnit.MONTHS);

        ComboBox<Compounding> compoundingField = new ComboBox<>(FXCollections.observableArrayList(Compounding.values()));
        compoundingField.setValue(editing ? existing.getCompounding() : Compounding.QUARTERLY);

        DatePicker maturityDateField = new DatePicker(editing ? LocalDate.parse(existing.getMaturityDate()) : null);
        ComboBox<InterestPayout> payoutField = new ComboBox<>(FXCollections.observableArrayList(InterestPayout.values()));
        payoutField.setValue(editing ? existing.getPayout() : InterestPayout.CUMULATIVE);

        TextField nomineeField = new TextField(editing ? existing.getNominee() : "");
        TextArea notesField = new TextArea(editing ? existing.getNotes() : "");
        notesField.setPrefRowCount(3);
        notesField.setWrapText(true);

        for (Control field : new Control[] {depositorField, bankField, fdNumberField, principalField, rateField,
                startDateField, tenureValueField, tenureUnitField, compoundingField, maturityDateField, payoutField,
                nomineeField, notesField}) {
            field.setMaxWidth(Double.MAX_VALUE);
            field.setPrefWidth(280);
        }

        // Live preview — the amount + explainer recalculate on every keystroke, matching the
        // reference's own preview box exactly.
        Label previewAmount = new Label(MoneyFormat.currency(0));
        previewAmount.getStyleClass().add("title-2");
        Label previewExplainer = new Label("Enter a principal, rate and tenure");
        previewExplainer.getStyleClass().add("text-caption");
        VBox previewBox = new VBox(2, previewAmount, previewExplainer);
        previewBox.getStyleClass().add("card");
        previewBox.setPadding(new Insets(14));

        Runnable[] refreshMaturitySuggestion = new Runnable[1];
        Runnable refreshPreview = () -> {
            double principal = parseDouble(principalField.getText());
            double rate = parseDouble(rateField.getText());
            int tenureValue = (int) parseDouble(tenureValueField.getText());
            TenureUnit unit = tenureUnitField.getValue();
            Compounding compounding = compoundingField.getValue();
            InterestPayout payout = payoutField.getValue();
            if (principal <= 0 || rate <= 0 || tenureValue <= 0 || unit == null || compounding == null || payout == null) {
                previewAmount.setText(MoneyFormat.currency(principal));
                previewExplainer.setText(principal > 0 ? "Enter a rate and tenure" : "Enter a principal, rate and tenure");
                return;
            }
            double years = FixedDepositCalculator.yearsOf(tenureValue, unit);
            double interest = FixedDepositCalculator.interest(principal, rate, years, compounding, payout);
            double maturity = principal + (payout == InterestPayout.PAID_OUT ? 0 : interest);
            previewAmount.setText(MoneyFormat.currency(maturity));
            String tenureText = tenureValue + " " + unit.dbValue().toLowerCase(java.util.Locale.ROOT);
            previewExplainer.setText(payout == InterestPayout.PAID_OUT
                    ? String.format("%s principal · %s paid out periodically · over %s", MoneyFormat.currency(principal), MoneyFormat.currency(interest), tenureText)
                    : String.format("%s + %s interest · over %s · %s compounding",
                            MoneyFormat.currency(principal), MoneyFormat.currency(interest), tenureText, compounding.dbValue().toLowerCase(java.util.Locale.ROOT)));
        };

        Runnable suggestMaturity = () -> {
            if (editing) return; // don't override an explicit edit
            LocalDate start = startDateField.getValue();
            double tenureValue = parseDouble(tenureValueField.getText());
            TenureUnit unit = tenureUnitField.getValue();
            if (start != null && tenureValue > 0 && unit != null) {
                maturityDateField.setValue(FixedDepositCalculator.suggestMaturityDate(start, tenureValue, unit));
            }
        };
        refreshMaturitySuggestion[0] = suggestMaturity;

        for (TextField field : new TextField[] {principalField, rateField, tenureValueField}) {
            field.textProperty().addListener((obs, o, v) -> { refreshPreview.run(); suggestMaturity.run(); });
        }
        startDateField.valueProperty().addListener((obs, o, v) -> suggestMaturity.run());
        tenureUnitField.valueProperty().addListener((obs, o, v) -> { refreshPreview.run(); suggestMaturity.run(); });
        compoundingField.valueProperty().addListener((obs, o, v) -> refreshPreview.run());
        payoutField.valueProperty().addListener((obs, o, v) -> refreshPreview.run());
        if (!editing) suggestMaturity.run();
        refreshPreview.run();

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
        grid.addRow(row++, fieldLabel("Depositor:", "Who this deposit belongs to."), depositorField);
        grid.addRow(row++, fieldLabel("Bank:", "The bank or NBFC holding this deposit."), bankField);
        grid.addRow(row++, fieldLabel("Principal (₹):", "The amount deposited."), principalField);
        grid.addRow(row++, fieldLabel("Rate of interest (% p.a.):", "Annual interest rate."), rateField);
        grid.addRow(row++, fieldLabel("FD / receipt number:", "Tells two identical deposits apart."), fdNumberField);
        grid.addRow(row++, fieldLabel("Deposited on:", "The date this deposit started."), startDateField);
        HBox tenureRow = new HBox(8, tenureValueField, tenureUnitField);
        grid.addRow(row++, fieldLabel("Tenure:", "How long the deposit runs for."), tenureRow);
        grid.addRow(row++, fieldLabel("Compounding:", "Interest compounds the way the bank pays it."), compoundingField);
        grid.addRow(row++, fieldLabel("Maturity date:", "Auto-suggested from the start date + tenure; override if the bank quotes a different date."), maturityDateField);
        grid.addRow(row++, fieldLabel("Interest:", "Cumulative compounds until maturity; paid-out periodically never compounds."), payoutField);
        grid.addRow(row++, fieldLabel("Nominee:", "Who's named on the deposit, if anyone."), nomineeField);
        grid.addRow(row, fieldLabel("Note:", "Branch, account number, anything worth remembering."), notesField);

        VBox content = new VBox(16, previewBox, grid);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(520);

        Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        Runnable updateSaveDisabled = () -> saveButton.setDisable(
                depositorField.getValue() == null || depositorField.getValue().isBlank()
                        || bankField.getValue() == null || bankField.getValue().isBlank()
                        || parseDouble(principalField.getText()) <= 0
                        || maturityDateField.getValue() == null);
        depositorField.valueProperty().addListener((o, a, b) -> updateSaveDisabled.run());
        bankField.valueProperty().addListener((o, a, b) -> updateSaveDisabled.run());
        principalField.textProperty().addListener((o, a, b) -> updateSaveDisabled.run());
        maturityDateField.valueProperty().addListener((o, a, b) -> updateSaveDisabled.run());
        updateSaveDisabled.run();

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            FixedDeposit fd = editing ? existing : new FixedDeposit();
            fd.setDepositor(depositorField.getValue());
            fd.setBank(bankField.getValue());
            fd.setFdNumber(fdNumberField.getText());
            fd.setPrincipal(parseDouble(principalField.getText()));
            fd.setRatePercent(parseDouble(rateField.getText()));
            fd.setTenureValue((int) parseDouble(tenureValueField.getText()));
            fd.setTenureUnit(tenureUnitField.getValue());
            fd.setCompounding(compoundingField.getValue());
            fd.setPayout(payoutField.getValue());
            fd.setStartDate(startDateField.getValue() != null ? startDateField.getValue().toString() : LocalDate.now().toString());
            fd.setMaturityDate(maturityDateField.getValue().toString());
            fd.setNominee(nomineeField.getText());
            fd.setNotes(notesField.getText());
            return fd;
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
        if (text == null) return 0.0;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
