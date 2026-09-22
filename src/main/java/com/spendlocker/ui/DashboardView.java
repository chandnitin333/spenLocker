package com.spendlocker.ui;

import com.spendlocker.config.AppConfig;
import com.spendlocker.dao.BudgetDao;
import com.spendlocker.dao.DocumentDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.util.FinancialYear;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
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
        boolean dark = ThemeManager.loadSaved(new AppConfig()) == ThemeManager.DARK;
        CategoricalPalette palette = new CategoricalPalette(dark);

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

        FlowPane chartsRow = new FlowPane(16, 16, buildCategoryChart(palette), buildAllocationChart(palette));

        // KPIs first (what happened), then alerts (what needs attention), then trend and
        // breakdowns (why) — the order a reader actually wants, not construction order.
        getChildren().addAll(titleRow, buildStatCards(investmentDao.overallRoiPercent()));
        VBox budgetAlerts = buildBudgetAlerts();
        if (budgetAlerts != null) {
            getChildren().add(budgetAlerts);
        }
        getChildren().addAll(buildTrendChart(), chartsRow);
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
        VBox card = new VBox(10, heading, rows);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private VBox chartCard(String heading, javafx.scene.Node chart) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("title-3");
        VBox card = new VBox(10, headingLabel, chart);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        VBox.setVgrow(chart, Priority.ALWAYS);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Trend over time -> area chart (gradient fill under the line), single series in the accent hue. */
    private VBox buildTrendChart() {
        LinkedHashMap<String, Double> monthly = expenseDao.monthlyTotals(TREND_MONTHS);

        RunwayChart chart = new RunwayChart();
        chart.setPrefHeight(200);
        chart.setMinHeight(200);
        String symbol = NumberFormat.getCurrencyInstance(Locale.getDefault()).getCurrency().getSymbol();
        chart.setData(
                monthly.keySet().stream().map(this::formatMonth).toList(),
                new java.util.ArrayList<>(monthly.values()),
                symbol);

        return chartCard("Monthly Spending Trend", chart);
    }

    /** Part-to-whole across an open-ended set of categories -> donut, top N + "Other". */
    private VBox buildCategoryChart(CategoricalPalette palette) {
        LinkedHashMap<String, Double> categoryTotals = expenseDao.categoryTotalsForRange(
                FinancialYear.startOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE),
                FinancialYear.endOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE));
        LinkedHashMap<String, Double> chartData = topNWithOther(categoryTotals, MAX_CATEGORY_SLICES);

        Map<String, String> colorByLabel = new LinkedHashMap<>();
        int slot = 0;
        for (String label : chartData.keySet()) {
            colorByLabel.put(label, label.equals("Other") ? palette.otherColor() : palette.slot(slot++));
        }

        VBox chartArea = buildDonut(chartData, colorByLabel, "Total Spent", onCategoryDrilldown);
        VBox card = chartCard("Spending by Category (" + selectedFinancialYear + ")", chartArea);
        card.getStyleClass().add("donut-card");
        return card;
    }

    /**
     * Fixed identity -> value, so each investment type keeps the same slot regardless of rank.
     */
    private VBox buildAllocationChart(CategoricalPalette palette) {
        LinkedHashMap<String, Double> byType = investmentDao.currentValueByType();

        Map<String, String> colorByLabel = new LinkedHashMap<>();
        int i = 0;
        for (String label : byType.keySet()) {
            colorByLabel.put(label, palette.slot(i++));
        }

        VBox chartArea = buildDonut(byType, colorByLabel, "Total Value", onInvestmentTypeDrilldown);
        VBox card = chartCard("Investment Allocation", chartArea);
        card.getStyleClass().add("donut-card");
        return card;
    }

    /**
     * A donut (part-to-whole, small fixed set of slices) with a center total, a manually-colored
     * legend (so it matches whatever fixed/ranked colors the caller assigned), and per-slice
     * hover tooltips showing value + percentage.
     */
    private VBox buildDonut(LinkedHashMap<String, Double> data, Map<String, String> colorByLabel,
                             String centerCaption, Consumer<String> onSliceClick) {
        double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
        boolean empty = data.isEmpty();

        PieChart chart = new PieChart();
        chart.setLegendVisible(false);
        chart.setLabelsVisible(false);
        chart.setAnimated(false);
        chart.setPrefSize(220, 220);
        chart.setStartAngle(90);

        if (empty) {
            chart.getData().add(new PieChart.Data("No data yet", 1));
        } else {
            data.forEach((label, value) -> chart.getData().add(new PieChart.Data(label, value)));
        }

        for (PieChart.Data slice : chart.getData()) {
            String color = colorByLabel.get(slice.getName());
            double pct = total > 0 ? (slice.getPieValue() / total) * 100 : 0;
            // "Other" bundles several categories together, so clicking it can't map to one
            // filter value — only individual slices drill down.
            boolean clickable = !empty && onSliceClick != null && !"Other".equals(slice.getName());
            slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode == null) return;
                if (color != null) newNode.setStyle("-fx-pie-color: " + color + ";");
                String tooltipText = String.format("%s: %s (%.1f%%)", slice.getName(), currency(slice.getPieValue()), pct)
                        + (clickable ? "\nClick to view" : "");
                Tooltip.install(newNode, new Tooltip(tooltipText));
                if (clickable) {
                    newNode.setCursor(javafx.scene.Cursor.HAND);
                    newNode.setOnMouseClicked(evt -> onSliceClick.accept(slice.getName()));
                }
            });
        }

        Circle hole = new Circle(42);
        hole.getStyleClass().add("donut-hole");
        Label totalLabel = new Label(currency(total));
        totalLabel.getStyleClass().add("title-3");
        Label totalCaption = new Label(centerCaption);
        totalCaption.getStyleClass().add("text-caption");
        VBox centerText = new VBox(2, totalLabel, totalCaption);
        centerText.setAlignment(Pos.CENTER);
        StackPane donutStack = new StackPane(chart, hole, centerText);

        FlowPane legend = new FlowPane(12, 6);
        colorByLabel.forEach((label, color) -> {
            boolean clickable = !empty && onSliceClick != null && !"Other".equals(label);
            legend.getChildren().add(legendEntry(label, color, clickable ? onSliceClick : null));
        });

        return new VBox(12, donutStack, legend);
    }

    private HBox legendEntry(String label, String color, Consumer<String> onClick) {
        Region swatch = new Region();
        swatch.getStyleClass().add("legend-swatch");
        swatch.setStyle("-fx-background-color: " + color + ";");
        Label text = new Label(label);
        text.getStyleClass().add("text-caption");
        HBox entry = new HBox(6, swatch, text);
        entry.setAlignment(Pos.CENTER_LEFT);
        if (onClick != null) {
            entry.setCursor(javafx.scene.Cursor.HAND);
            entry.setOnMouseClicked(e -> onClick.accept(label));
            Tooltip.install(entry, new Tooltip("Click to view"));
        }
        return entry;
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

    private HBox buildStatCards(double roi) {
        double fySpend = expenseDao.sumForRange(
                FinancialYear.startOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE),
                FinancialYear.endOf(selectedFinancialYear).format(DateTimeFormatter.ISO_LOCAL_DATE));
        HBox cards = new HBox(16,
                statCard(Feather.CREDIT_CARD, "This Month's Spending", currency(expenseDao.sumForCurrentMonth()), null),
                statCard(Feather.CALENDAR, selectedFinancialYear + " Spending", currency(fySpend), null),
                statCard(Feather.TRENDING_UP, "Portfolio Value", currency(investmentDao.sumCurrentValue()),
                        String.format("%s%.2f%% ROI", roi >= 0 ? "+" : "", roi)),
                statCard(Feather.FOLDER, "Documents in Vault", String.valueOf(documentDao.count()), null));
        cards.setFillHeight(true);
        return cards;
    }

    private VBox statCard(Ikon icon, String label, String value, String badge) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.getStyleClass().add("stat-icon");
        HBox iconCircle = new HBox(fontIcon);
        iconCircle.setAlignment(Pos.CENTER);
        iconCircle.getStyleClass().add("stat-icon-circle");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("title-2");
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("text-caption");

        VBox textBox = new VBox(4, valueLabel, captionLabel);

        if (badge != null) {
            Label badgeLabel = new Label(badge);
            badgeLabel.getStyleClass().add(badge.startsWith("-") ? "badge-negative" : "badge-positive");
            textBox.getChildren().add(badgeLabel);
        }

        HBox content = new HBox(14, iconCircle, textBox);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(content);
        card.setPadding(new Insets(18));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private String currency(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount);
    }
}
