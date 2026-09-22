package com.spendlocker.ui;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.dao.RecurringExpenseDao;
import com.spendlocker.excel.ExcelExportService;
import com.spendlocker.model.Expense;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.Investment;
import com.spendlocker.model.RecurringExpense;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The reference design's "Send & export" tab: a workbook download, mail compose, and a
 *  copyable plain-text summary. */
public class SendExportView extends BorderPane {

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final RecurringExpenseDao recurringExpenseDao = new RecurringExpenseDao();
    private final ExcelExportService excelExportService = new ExcelExportService();

    private final CheckBox depositsChip = new CheckBox("Fixed deposit");
    private final CheckBox investmentsChip = new CheckBox("Investments");
    private final CheckBox expensesChip = new CheckBox("Expense");
    private final Label downloadStatus = new Label();
    private final Label mailHint = new Label();
    private final TextArea summaryArea = new TextArea();
    private final VBox kpiStripBox = new VBox();
    private boolean downloaded = false;

    public SendExportView() {
        setPadding(new Insets(24));

        Label title = new Label("Send & export", new FontIcon(Feather.SEND));
        title.getStyleClass().add("title-1");
        HBox toolbar = new HBox(title);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 16, 0));

        VBox left = buildLeftColumn();
        VBox right = buildRightColumn();
        HBox columns = new HBox(24, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(420);
        right.setPrefWidth(420);

        VBox body = new VBox(20, kpiStripBox, columns);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        setTop(toolbar);
        setCenter(scroll);
        refresh();
    }

    private VBox buildLeftColumn() {
        for (CheckBox chip : List.of(depositsChip, investmentsChip, expensesChip)) {
            chip.setSelected(true);
            chip.getStyleClass().add("tag");
        }
        Label chipsHeading = new Label("Choose what to include");
        chipsHeading.getStyleClass().add("title-3");
        Label chipsNote = new Label("One sheet per category. Untick anything you don't want to send.");
        chipsNote.getStyleClass().add("text-caption");
        HBox chipsRow = new HBox(12, depositsChip, investmentsChip, expensesChip);
        VBox chipsSection = new VBox(8, chipsHeading, chipsNote, chipsRow);
        chipsSection.getStyleClass().add("dash-section");

        Label step1Heading = new Label("Step 1 · Download the workbook");
        step1Heading.getStyleClass().add("title-3");
        Label step1Note = new Label("One workbook with a summary plus a sheet for each category you ticked.");
        step1Note.getStyleClass().add("text-caption");
        step1Note.setWrapText(true);
        Button downloadButton = new Button("Download Excel workbook", new FontIcon(Feather.DOWNLOAD));
        downloadButton.getStyleClass().add("accent");
        downloadButton.setOnAction(e -> onDownload());
        downloadStatus.getStyleClass().add("text-caption");
        VBox step1Section = new VBox(8, step1Heading, step1Note, downloadButton, downloadStatus);
        step1Section.getStyleClass().add("dash-section");

        Label step2Heading = new Label("Step 2 · Start the email");
        step2Heading.getStyleClass().add("title-3");
        Label step2Note = new Label("Opens a draft with the summary already written. Attach the workbook yourself.");
        step2Note.getStyleClass().add("text-caption");
        step2Note.setWrapText(true);
        Button gmailBtn = new Button("Compose in Gmail");
        gmailBtn.setOnAction(e -> onComposeGmail());
        Button outlookBtn = new Button("Compose in Outlook");
        outlookBtn.setOnAction(e -> onComposeOutlook());
        Button defaultMailBtn = new Button("Default mail app");
        defaultMailBtn.setOnAction(e -> onComposeDefault());
        HBox mailButtons = new HBox(8, gmailBtn, outlookBtn, defaultMailBtn);
        mailHint.getStyleClass().add("text-caption");
        mailHint.setWrapText(true);
        updateMailHint();
        VBox step2Section = new VBox(8, step2Heading, step2Note, mailButtons, mailHint);
        step2Section.getStyleClass().add("dash-section");

        return new VBox(4, chipsSection, step1Section, step2Section);
    }

    private VBox buildRightColumn() {
        Label heading = new Label("Copy the summary");
        heading.getStyleClass().add("title-3");
        Label note = new Label("Plain text that pastes tidily into any mail or message.");
        note.getStyleClass().add("text-caption");

        summaryArea.setEditable(false);
        summaryArea.setWrapText(false);
        summaryArea.setPrefRowCount(20);
        summaryArea.setStyle("-fx-font-family: monospace;");

        Label copyStatus = new Label();
        copyStatus.getStyleClass().add("text-caption");
        Button copyButton = new Button("Copy to clipboard", new FontIcon(Feather.CLIPBOARD));
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(summaryArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyStatus.setText("Copied.");
        });

        return new VBox(8, heading, note, summaryArea, copyButton, copyStatus);
    }

    public void refresh() {
        List<FixedDeposit> deposits = fixedDepositDao.findAll();
        List<Investment> investments = investmentDao.findAll();
        List<Expense> expenses = expenseDao.findAll();

        List<FixedDeposit> activeDeposits = deposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate())).toList();
        double fdPrincipal = activeDeposits.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        double investmentsValue = investments.stream().mapToDouble(Investment::getCurrentTotalValue).sum();
        double totalInFile = fdPrincipal + investmentsValue;

        kpiStripBox.getChildren().setAll(KpiStrip.strip(
                KpiStrip.cell("Deposits", String.valueOf(deposits.size()), null),
                KpiStrip.cell("Investments", String.valueOf(investments.size()), null),
                KpiStrip.cell("Expenses", String.valueOf(expenses.size()), null),
                KpiStrip.cell("Total in the file", MoneyFormat.currency(totalInFile), null),
                KpiStrip.cell("As at", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")), null)));

        summaryArea.setText(buildSummaryText(deposits, activeDeposits, investments, expenses));
    }

    private String buildSummaryText(List<FixedDeposit> allDeposits, List<FixedDeposit> activeDeposits,
                                     List<Investment> investments, List<Expense> expenses) {
        StringBuilder sb = new StringBuilder();
        String asAt = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        sb.append("Wealth Book — position as at ").append(asAt).append("\n\n");

        double principal = activeDeposits.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        double interest = activeDeposits.stream().mapToDouble(FixedDepositCalculator::interest).sum();
        double maturityValue = activeDeposits.stream().mapToDouble(FixedDepositCalculator::maturityAmount).sum();
        double weightedRate = FixedDepositCalculator.weightedAverageRate(activeDeposits);

        sb.append("Fixed deposits\n");
        appendLine(sb, "Active deposits", String.valueOf(activeDeposits.size()));
        appendLine(sb, "Principal", MoneyFormat.currency(principal));
        appendLine(sb, "Interest to come", MoneyFormat.currency(interest));
        appendLine(sb, "Value at maturity", MoneyFormat.currency(maturityValue));
        appendLine(sb, "Weighted avg rate", String.format("%.2f%%", weightedRate));
        sb.append("\n");

        Map<String, List<Investment>> byType = investments.stream()
                .collect(Collectors.groupingBy(Investment::getInvestmentType, LinkedHashMap::new, Collectors.toList()));
        LinkedHashMap<String, Double> rankedTypes = byType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(Investment::getCurrentTotalValue).sum(),
                        (a, b) -> a, LinkedHashMap::new))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        sb.append("Other investments\n");
        for (Map.Entry<String, Double> entry : rankedTypes.entrySet()) {
            int count = byType.get(entry.getKey()).size();
            appendLine(sb, entry.getKey(), MoneyFormat.currency(entry.getValue()) + "  (" + count + ")");
        }
        double investmentsValue = investments.stream().mapToDouble(Investment::getCurrentTotalValue).sum();
        sb.append("\n");
        appendLine(sb, "TOTAL", MoneyFormat.currency(principal + investmentsValue));
        sb.append("\n");

        double committedMonthly = recurringExpenseDao.findAll().stream()
                .filter(RecurringExpense::isActive)
                .mapToDouble(r -> switch (r.getFrequency()) {
                    case WEEKLY -> r.getAmount() * 52 / 12;
                    case MONTHLY -> r.getAmount();
                    case YEARLY -> r.getAmount() / 12;
                })
                .sum();
        sb.append("Recurring expenses\n");
        appendLine(sb, "Committed monthly", MoneyFormat.currency(committedMonthly));
        appendLine(sb, "Over a year", MoneyFormat.currency(committedMonthly * 12));
        sb.append("\n");

        List<FixedDeposit> next90 = activeDeposits.stream()
                .filter(fd -> {
                    Long d = FixedDepositCalculator.daysLeft(fd.getMaturityDate());
                    return d != null && d <= 90;
                })
                .sorted(java.util.Comparator.comparing(FixedDeposit::getMaturityDate))
                .toList();
        sb.append("Maturing in the next 90 days\n");
        if (next90.isEmpty()) {
            sb.append("  Nothing due\n");
        } else {
            for (FixedDeposit fd : next90) {
                String date = LocalDate.parse(fd.getMaturityDate()).format(DateTimeFormatter.ofPattern("d MMM yy"));
                sb.append("  ").append(date).append("  ").append(fd.getDepositor()).append(" / ").append(fd.getBank())
                        .append("  ").append(MoneyFormat.currency(FixedDepositCalculator.maturityAmount(fd))).append("\n");
            }
        }

        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        sb.append("  ").append(String.format("%-20s", label)).append(value).append("\n");
    }

    private void onDownload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Wealth Book workbook");
        chooser.setInitialFileName("Wealth-Book-" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File destination = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (destination == null) return;

        java.util.Set<String> include = new java.util.HashSet<>();
        if (depositsChip.isSelected()) include.add("Fixed deposit");
        if (investmentsChip.isSelected()) include.add("Investments");
        if (expensesChip.isSelected()) include.add("Expense");

        try {
            excelExportService.exportWealthBook(fixedDepositDao.findAll(), investmentDao.findAll(),
                    expenseDao.findAll(), include, destination);
            downloadStatus.setText("Saved to " + destination.getName());
            downloaded = true;
            updateMailHint();
        } catch (Exception ex) {
            AlertUtil.error("Export failed", ex.getMessage());
        }
    }

    private void updateMailHint() {
        mailHint.setText(downloaded
                ? "Workbook downloaded — remember to attach it with the paperclip."
                : "Do step 1 first, or the mail will go out with no spreadsheet.");
    }

    private void onComposeGmail() {
        openMail("https://mail.google.com/mail/?view=cm&fs=1&su=" + encode(subject()) + "&body=" + encode(summaryArea.getText()));
    }

    private void onComposeOutlook() {
        openMail("https://outlook.office.com/mail/deeplink/compose?subject=" + encode(subject()) + "&body=" + encode(summaryArea.getText()));
    }

    private void onComposeDefault() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                Desktop.getDesktop().mail(new URI("mailto:?subject=" + encode(subject()) + "&body=" + encode(summaryArea.getText())));
            } else {
                AlertUtil.info("Not available", "No default mail app is configured on this system.");
            }
        } catch (Exception ex) {
            AlertUtil.error("Couldn't open mail app", ex.getMessage());
        }
    }

    private void openMail(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            AlertUtil.error("Couldn't open browser", ex.getMessage());
        }
    }

    private String subject() {
        return "Wealth Book — portfolio as at " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
