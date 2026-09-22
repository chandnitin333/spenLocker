package com.spendlocker.ui;

import com.spendlocker.dao.BudgetDao;
import com.spendlocker.dao.DocumentDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.dao.RecurringExpenseDao;
import com.spendlocker.model.RecurringExpense;
import com.spendlocker.util.FinancialYear;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class DashboardView extends VBox {

    private static final int MAX_CATEGORY_SLICES = 7;
    private static final int TREND_MONTHS = 6;
    private static final int PRIOR_FINANCIAL_YEARS_SHOWN = 5;

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final BudgetDao budgetDao = new BudgetDao();
    private final RecurringExpenseDao recurringExpenseDao = new RecurringExpenseDao();
    private final Consumer<String> onCategoryDrilldown;
    private final Consumer<String> onInvestmentTypeDrilldown;

    private String selectedFinancialYear = FinancialYear.labelFor(LocalDate.now());

    /**
     * @param onCategoryDrilldown       called with a category name when a Spending by Category
     *                                  slice is clicked, to jump to Expenses filtered to it.
     * @param onInvestmentTypeDrilldown called with an investment type when an Investment
     *                                  Allocation slice is clicked, to jump to Investments
     *                                  filtered to it.
     */
    public DashboardView(Consumer<String> onCategoryDrilldown, Consumer<String> onInvestmentTypeDrilldown) {
        this.onCategoryDrilldown = onCategoryDrilldown;
        this.onInvestmentTypeDrilldown = onInvestmentTypeDrilldown;
        setSpacing(20);
        setPadding(new Insets(24));
        refresh();
    }

    public void refresh() {
        getChildren().clear();

        Label title = new Label("Dashboard", new FontIcon(Feather.HOME));
        title.getStyleClass().add("title-1");

        Label currentFyLabel = new Label("Current: " + FinancialYear.labelFor(LocalDate.now()));
        currentFyLabel.getStyleClass().add("text-caption");

        ComboBox<String> fyFilter = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                FinancialYear.recentLabels(PRIOR_FINANCIAL_YEARS_SHOWN)));
        fyFilter.setValue(selectedFinancialYear);
        fyFilter.setOnAction(e -> {
            selectedFinancialYear = fyFilter.getValue();
            refresh();
        });

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, titleSpacer, currentFyLabel,
                new Label("View:", new FontIcon(Feather.CALENDAR)), fyFilter);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("page-header");
        titleRow.setPadding(new Insets(0, 0, 12, 0));

        FlowPane chartsRow = new FlowPane(16, 16, buildCategoryChart(), buildAllocationChart());

        // hero (KPIs) -> concentration -> runway -> next-out -> what-needs-attention: the exact
        // flow of the reference design's own page, confirmed by rendering wealth-book.html itself.
        getChildren().addAll(titleRow, buildStatCards(investmentDao.overallRoiPercent()), chartsRow, buildTrendChart());
        VBox nextUp = buildNextUp();
        if (nextUp != null) {
            getChildren().add(nextUp);
        }
        VBox budgetAlerts = buildBudgetAlerts();
        if (budgetAlerts != null) {
            getChildren().add(budgetAlerts);
        }
    }

    /** Flags any budgeted category at >=90% of its monthly limit. Returns null when nothing to flag. */
    private VBox buildBudgetAlerts() {
        Map<String, Double> budgets = budgetDao.findAll();
        if (budgets.isEmpty()) return null;

        Map<String, Double> spentThisMonth = expenseDao.categoryTotalsForCurrentMonth();
        VBox rows = new VBox(8);
        budgets.forEach((category, limit) -> {
            double spent = spentThisMonth.getOrDefault(category, 0.0);
            double pct = limit > 0 ? (spent / limit) * 100 : 0;
            if (pct < 90) return;

            boolean over = pct >= 100;
            FontIcon icon = new FontIcon(over ? Feather.ALERT_TRIANGLE : Feather.ALERT_CIRCLE);
            icon.getStyleClass().add(over ? "badge-negative" : "badge-warning");
            Label text = new Label(String.format("%s: %s of %s (%.0f%%)",
                    category, currency(spent), currency(limit), pct));
            text.getStyleClass().add(over ? "badge-negative" : "badge-warning");
            HBox row = new HBox(8, icon, text);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        });
        if (rows.getChildren().isEmpty()) return null;

        Label heading = new Label("Budget Alerts", new FontIcon(Feather.BELL));
        heading.getStyleClass().add("title-3");
        VBox section = new VBox(10, heading, rows);
        section.getStyleClass().add("dash-section");
        return section;
    }

    /**
     * Upcoming recurring expenses with countdown rings — SpendLocker's equivalent of the
     * reference design's maturity reminders, using next-due-date in place of maturity date.
     * Ring color and the "due soon" cutoffs (30 / 90 days) match the reference exactly.
     */
    private VBox buildNextUp() {
        List<RecurringExpense> upcoming = recurringExpenseDao.findAll().stream()
                .filter(RecurringExpense::isActive)
                .limit(6)
                .toList();
        if (upcoming.isEmpty()) return null;

        LocalDate today = LocalDate.now();
        double dueSoonTotal = 0;
        int dueSoonCount = 0;

        VBox rows = new VBox();
        for (int i = 0; i < upcoming.size(); i++) {
            RecurringExpense r = upcoming.get(i);
            LocalDate due = LocalDate.parse(r.getNextDueDate());
            long daysLeft = ChronoUnit.DAYS.between(today, due);
            if (daysLeft <= 90) {
                dueSoonTotal += r.getAmount();
                dueSoonCount++;
            }
            Color color = daysLeft <= 30 ? Color.web("#A32D2D")
                    : daysLeft <= 90 ? Color.web("#9A6A12")
                    : Color.web("#2C7A6E");
            CountdownRing ring = new CountdownRing((int) daysLeft, color);

            String primaryName = r.getMerchantOrVendor() != null && !r.getMerchantOrVendor().isBlank()
                    ? r.getMerchantOrVendor() : r.getCategory();
            Label who = new Label(primaryName);
            who.setStyle("-fx-font-weight: 600;");
            Label category = new Label(r.getCategory());
            category.getStyleClass().add("text-caption");
            HBox whoBox = new HBox(6, who, category);
            whoBox.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label dateLabel = new Label(due.format(DateTimeFormatter.ofPattern("d MMM")));
            dateLabel.getStyleClass().add("text-caption");
            Label amountLabel = new Label(currency(r.getAmount()));

            HBox row = new HBox(12, ring, whoBox, spacer, dateLabel, amountLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(7, 4, 7, 4));
            row.getStyleClass().add("next-up-row");
            if (i < upcoming.size() - 1) {
                row.getStyleClass().add("divider");
            }
            rows.getChildren().add(row);
        }

        Label summary = new Label(dueSoonCount > 0
                ? String.format("%s across %d due in the next 90 days", currency(dueSoonTotal), dueSoonCount)
                : "Nothing due in the next 90 days");
        summary.getStyleClass().add("text-caption");

        Label heading = new Label("Next Up", new FontIcon(Feather.CLOCK));
        heading.getStyleClass().add("title-3");
        VBox section = new VBox(6, heading, summary, rows);
        // The reference's own "Next out" is the one hero block with an explicit divider
        // (border-top) rather than a boxed card — match that instead of boxing it.
        section.getStyleClass().add("dash-section");
        return section;
    }

    /** A plain, boxless section — heading plus content, no card border/background — matching
     *  the reference design's flat hero area (sections are separated by whitespace, not boxes). */
    private VBox chartCard(String heading, javafx.scene.Node chart) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("title-3");
        VBox section = new VBox(10, headingLabel, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        HBox.setHgrow(section, Priority.ALWAYS);
        return section;
    }

    /** Trend over time -> area chart (gradient fill under the line), single series in the accent hue. */
    private VBox buildTrendChart() {
        LinkedHashMap<String, Double> monthly = expenseDao.monthlyTotals(TREND_MONTHS);
        double total = monthly.values().stream().mapToDouble(Double::doubleValue).sum();

        RunwayChart chart = new RunwayChart();
        chart.setPrefHeight(200);
        chart.setMinHeight(200);
        String symbol = NumberFormat.getCurrencyInstance(new Locale("en", "IN")).getCurrency().getSymbol();
        chart.setData(
                monthly.keySet().stream().map(this::formatMonth).toList(),
                new java.util.ArrayList<>(monthly.values()),
                symbol);

        // Title + a live readout on the same row (right-aligned) — matching the reference's
        // "Maturity runway" header, which always pairs the chart title with a summary reading.
        Label heading = new Label("Monthly Spending Trend");
        heading.getStyleClass().add("title-3");
        Label readout = new Label(total > 0
                ? String.format("%s spent across the last %d months", currency(total), TREND_MONTHS)
                : "Nothing spent in this window");
        readout.getStyleClass().add("text-caption");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headingRow = new HBox(10, heading, spacer, readout);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, headingRow, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return section;
    }

    /** Part-to-whole across an open-ended set of categories -> the reference's concentration bar,
     *  top N + "Other" (single teal hue, ranked dark-to-light, no rainbow). */
    private VBox buildCategoryChart() {
        LinkedHashMap<String, Double> categoryTotals = expenseDao.categoryTotalsForRange(
                FinancialYear.startOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE),
                FinancialYear.endOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE));
        LinkedHashMap<String, Double> chartData = topNWithOther(categoryTotals, MAX_CATEGORY_SLICES);

        ConcentrationBar bar = new ConcentrationBar();
        bar.setData(chartData, "categories", onCategoryDrilldown);
        return chartCard("Spending by Category (" + selectedFinancialYear + ")", bar);
    }

    /** Fixed identity -> value, ranked largest-first for the concentration bar's shading. */
    private VBox buildAllocationChart() {
        LinkedHashMap<String, Double> byType = investmentDao.currentValueByType().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        ConcentrationBar bar = new ConcentrationBar();
        bar.setData(byType, "types", onInvestmentTypeDrilldown);
        return chartCard("Investment Allocation", bar);
    }

    private BarChart<Number, String> horizontalBarChart(LinkedHashMap<String, Double> data, Map<String, String> colorByLabel) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(true);
        CategoryAxis yAxis = new CategoryAxis();

        BarChart<Number, String> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(220);
        chart.setBarGap(2);
        chart.setCategoryGap(6);

        XYChart.Series<Number, String> series = new XYChart.Series<>();
        if (data.isEmpty()) {
            series.getData().add(new XYChart.Data<>(0, "No data yet"));
        } else {
            data.forEach((label, value) -> series.getData().add(new XYChart.Data<>(value, label)));
        }
        chart.getData().add(series);

        for (XYChart.Data<Number, String> point : series.getData()) {
            String color = colorByLabel.get(point.getYValue());
            point.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode == null) return;
                if (color != null) newNode.setStyle("-fx-bar-fill: " + color + ";");
                Tooltip.install(newNode, new Tooltip(point.getYValue() + ": " + currency(point.getXValue().doubleValue())));
            });
        }
        return chart;
    }

    private LinkedHashMap<String, Double> topNWithOther(LinkedHashMap<String, Double> ranked, int maxSlices) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        double otherTotal = 0;
        int i = 0;
        for (Map.Entry<String, Double> entry : ranked.entrySet()) {
            if (i < maxSlices) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                otherTotal += entry.getValue();
            }
            i++;
        }
        if (otherTotal > 0) {
            result.put("Other", otherTotal);
        }
        return result;
    }

    private String formatMonth(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        return ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " '" + (ym.getYear() % 100);
    }

    /** The reference design's "hstrip" KPI row: one hairline-bordered strip, hcells divided by
     *  1px gaps rather than separate shadowed cards. */
    private HBox buildStatCards(double roi) {
        double fySpend = expenseDao.sumForRange(
                FinancialYear.startOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE),
                FinancialYear.endOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE));
        String roiBadge = String.format("%s%.2f%% ROI", roi >= 0 ? "+" : "", roi);
        HBox strip = new HBox(1,
                hcell("This Month's Spending", currency(expenseDao.sumForCurrentMonth()), null),
                hcell(selectedFinancialYear + " Spending", currency(fySpend), null),
                hcell("Portfolio Value", currency(investmentDao.sumCurrentValue()), roiBadge),
                hcell("Documents in Vault", String.valueOf(documentDao.count()), null));
        strip.getStyleClass().add("hstrip");
        strip.setFillHeight(true);
        return strip;
    }

    private VBox hcell(String label, String value, String note) {
        Label labelText = new Label(label.toUpperCase(Locale.ROOT));
        labelText.getStyleClass().add("hcell-label");
        Label valueText = new Label(value);
        valueText.getStyleClass().add("hcell-value");

        VBox cell = new VBox(4, labelText, valueText);
        if (note != null) {
            Label noteLabel = new Label(note);
            noteLabel.getStyleClass().addAll("tag", note.startsWith("-") ? "negative" : "positive");
            cell.getChildren().add(noteLabel);
        }
        cell.getStyleClass().add("hcell");
        cell.setPadding(new Insets(14, 16, 14, 16));
        cell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private String currency(double amount) {
        return com.spendlocker.util.MoneyFormat.currency(amount);
    }
}
