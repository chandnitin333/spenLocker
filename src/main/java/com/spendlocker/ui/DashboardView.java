package com.spendlocker.ui;

import com.spendlocker.dao.BudgetDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.util.FixedDepositCalculator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class DashboardView extends VBox {

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final BudgetDao budgetDao = new BudgetDao();
    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final Consumer<String> onInvestmentTypeDrilldown;
    private final Consumer<String> onBankDrilldown;

    /**
     * @param onInvestmentTypeDrilldown called with an investment type when a by-kind card is
     *                                  clicked, to jump to Investments filtered to it.
     * @param onBankDrilldown           called with a bank name when a bank-concentration segment
     *                                  is clicked, to jump to Deposits filtered to it.
     */
    public DashboardView(Consumer<String> onInvestmentTypeDrilldown, Consumer<String> onBankDrilldown) {
        this.onInvestmentTypeDrilldown = onInvestmentTypeDrilldown;
        this.onBankDrilldown = onBankDrilldown;
        setSpacing(20);
        setPadding(new Insets(24));
        refresh();
    }

    public void refresh() {
        getChildren().clear();

        Label title = new Label("Dashboard", new FontIcon(Feather.HOME));
        title.getStyleClass().add("title-1");

        Label asAtLabel = new Label("As at " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")));
        asAtLabel.getStyleClass().add("text-caption");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, titleSpacer, asAtLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("page-header");
        titleRow.setPadding(new Insets(0, 0, 12, 0));

        List<FixedDeposit> allDeposits = fixedDepositDao.findAll();

        // hero (KPIs) -> bank concentration -> maturity runway -> next-out -> everything you
        // track, by kind -> what-needs-attention: the reference design's own page flow,
        // confirmed by rendering wealth-book.html itself.
        getChildren().addAll(titleRow, buildStatCards(allDeposits), buildBankConcentration(allDeposits),
                buildMaturityRunway(allDeposits));
        VBox nextUp = buildNextUp(allDeposits);
        if (nextUp != null) {
            getChildren().add(nextUp);
        }
        getChildren().add(buildByKindGrid(allDeposits));
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
     * The next 6 upcoming maturities with countdown rings — matches the reference's "Next out"
     * exactly. Ring color and the "due soon" cutoffs (30 / 90 days) match the reference too.
     */
    private VBox buildNextUp(List<FixedDeposit> allDeposits) {
        List<FixedDeposit> upcoming = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .sorted(java.util.Comparator.comparing(FixedDeposit::getMaturityDate))
                .limit(6)
                .toList();
        if (upcoming.isEmpty()) return null;

        double dueSoonTotal = 0;
        int dueSoonCount = 0;

        VBox rows = new VBox();
        for (int i = 0; i < upcoming.size(); i++) {
            FixedDeposit fd = upcoming.get(i);
            LocalDate due = LocalDate.parse(fd.getMaturityDate());
            long daysLeft = FixedDepositCalculator.daysLeft(fd.getMaturityDate());
            double maturityAmount = FixedDepositCalculator.maturityAmount(fd);
            if (daysLeft <= 90) {
                dueSoonTotal += maturityAmount;
                dueSoonCount++;
            }
            Color color = daysLeft <= 30 ? Color.web("#A32D2D")
                    : daysLeft <= 90 ? Color.web("#9A6A12")
                    : Color.web("#2C7A6E");
            CountdownRing ring = new CountdownRing((int) daysLeft, color);

            Label who = new Label(fd.getDepositor());
            who.setStyle("-fx-font-weight: 600;");
            Label bank = new Label(fd.getBank());
            bank.getStyleClass().add("text-caption");
            HBox whoBox = new HBox(6, who, bank);
            whoBox.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label dateLabel = new Label(due.format(DateTimeFormatter.ofPattern("d MMM yy")));
            dateLabel.getStyleClass().add("text-caption");
            Label amountLabel = new Label(currency(maturityAmount));

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
                ? String.format("%s across %d deposits in the next 90 days", currency(dueSoonTotal), dueSoonCount)
                : "Nothing due in the next 90 days");
        summary.getStyleClass().add("text-caption");

        Label heading = new Label("Next out", new FontIcon(Feather.CLOCK));
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

    private static final int RUNWAY_MONTHS = 18;

    /** 18 months of FD maturities as a bar+cumulative-line chart, urgency-colored per month by
     *  its earliest deposit — the reference's "Maturity runway" exactly. */
    private VBox buildMaturityRunway(List<FixedDeposit> allDeposits) {
        List<FixedDeposit> active = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .toList();

        YearMonth base = YearMonth.now();
        List<YearMonth> months = new java.util.ArrayList<>();
        for (int i = 0; i < RUNWAY_MONTHS; i++) months.add(base.plusMonths(i));

        double[] totals = new double[RUNWAY_MONTHS];
        Long[] minDays = new Long[RUNWAY_MONTHS];
        for (FixedDeposit fd : active) {
            YearMonth maturityMonth = YearMonth.from(LocalDate.parse(fd.getMaturityDate()));
            int idx = months.indexOf(maturityMonth);
            if (idx < 0) continue; // outside the 18-month window
            totals[idx] += FixedDepositCalculator.maturityAmount(fd);
            Long daysLeft = FixedDepositCalculator.daysLeft(fd.getMaturityDate());
            if (minDays[idx] == null || daysLeft < minDays[idx]) minDays[idx] = daysLeft;
        }

        List<Double> values = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        List<Color> colors = new java.util.ArrayList<>();
        for (int i = 0; i < RUNWAY_MONTHS; i++) {
            values.add(totals[i]);
            labels.add(months.get(i).getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            Long d = minDays[i];
            colors.add(d == null ? Color.web("#2C7A6E")
                    : d <= 30 ? Color.web("#A32D2D") : d <= 90 ? Color.web("#9A6A12") : Color.web("#2C7A6E"));
        }

        double sixMonthTotal = values.subList(0, Math.min(6, values.size())).stream().mapToDouble(Double::doubleValue).sum();

        RunwayChart chart = new RunwayChart();
        chart.setPrefHeight(220);
        chart.setMinHeight(220);
        String symbol = NumberFormat.getCurrencyInstance(new Locale("en", "IN")).getCurrency().getSymbol();
        chart.setData(labels, values, symbol, colors);

        Label heading = new Label("Maturity runway");
        heading.getStyleClass().add("title-3");
        Label readout = new Label(sixMonthTotal > 0
                ? String.format("%s back in the next six months", currency(sixMonthTotal))
                : "Nothing matures in the next six months");
        readout.getStyleClass().add("text-caption");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headingRow = new HBox(10, heading, spacer, readout);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, headingRow, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return section;
    }

    /** Bank concentration: the reference's single teal-ramp bar, ranked largest-first. */
    private VBox buildBankConcentration(List<FixedDeposit> allDeposits) {
        LinkedHashMap<String, Double> byBank = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .collect(java.util.stream.Collectors.groupingBy(
                        FixedDeposit::getBank,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.summingDouble(FixedDeposit::getPrincipal)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        ConcentrationBar bar = new ConcentrationBar();
        bar.setData(byBank, "banks", onBankDrilldown);
        return chartCard("Bank concentration", bar);
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

    /** The reference design's FD-based hero KPI strip, exactly: Principal locked up / Interest
     *  still to come / Value at maturity / Other investments / Back within 30 days. */
    private HBox buildStatCards(List<FixedDeposit> allDeposits) {
        List<FixedDeposit> active = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .toList();

        double principal = active.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        double interest = active.stream().mapToDouble(FixedDepositCalculator::interest).sum();
        double maturityValue = active.stream().mapToDouble(FixedDepositCalculator::maturityAmount).sum();
        double weightedRate = FixedDepositCalculator.weightedAverageRate(active);

        double otherInvestments = investmentDao.sumCurrentValue();
        int otherCount = investmentDao.findAll().size();

        List<FixedDeposit> soon = active.stream()
                .filter(fd -> {
                    Long daysLeft = FixedDepositCalculator.daysLeft(fd.getMaturityDate());
                    return daysLeft != null && daysLeft <= 30;
                })
                .toList();

        VBox soonCell;
        if (soon.isEmpty()) {
            String nextDate = active.stream()
                    .map(FixedDeposit::getMaturityDate)
                    .min(java.util.Comparator.naturalOrder())
                    .map(d -> LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM yy")))
                    .orElse("—");
            soonCell = KpiStrip.cell("Back within 30 days", "nothing due", "next is " + nextDate);
        } else {
            double soonMaturity = soon.stream().mapToDouble(FixedDepositCalculator::maturityAmount).sum();
            double soonPrincipal = soon.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
            double soonInterest = soonMaturity - soonPrincipal;
            soonCell = KpiStrip.cell("Back within 30 days", currency(soonMaturity),
                    shortMoney(soonPrincipal) + " principal + " + shortMoney(soonInterest) + " interest", true);
        }

        return KpiStrip.strip(
                KpiStrip.cell("Principal locked up", currency(principal), active.size() + " active deposits"),
                KpiStrip.cell("Interest still to come", currency(interest), String.format("at %.2f%% average", weightedRate)),
                KpiStrip.cell("Value at maturity", currency(maturityValue), "principal + interest"),
                KpiStrip.cell("Other investments", currency(otherInvestments), otherCount + " holdings"),
                soonCell);
    }

    /** "Everything you track, by kind" — the reference Dashboard's own card grid: one card per
     *  kind (FDs, each investment type, expenses), clickable where a drill-down target exists. */
    private VBox buildByKindGrid(List<FixedDeposit> allDeposits) {
        Label heading = new Label("Everything you track, by kind", new FontIcon(Feather.GRID));
        heading.getStyleClass().add("title-3");
        Label subheading = new Label("Open any card for the full list.");
        subheading.getStyleClass().add("text-caption");

        FlowPane grid = new FlowPane(16, 16);

        long activeDeposits = allDeposits.stream().filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate())).count();
        double principal = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .mapToDouble(FixedDeposit::getPrincipal).sum();
        double interestToCome = allDeposits.stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .mapToDouble(FixedDepositCalculator::interest).sum();
        grid.getChildren().add(kindCard("#135049", "Fixed deposit", activeDeposits + " held",
                currency(principal), currency(interestToCome) + " interest to come", null));

        Map<String, List<com.spendlocker.model.Investment>> byType = investmentDao.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.spendlocker.model.Investment::getInvestmentType, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (Map.Entry<String, List<com.spendlocker.model.Investment>> entry : byType.entrySet()) {
            List<com.spendlocker.model.Investment> list = entry.getValue();
            double invested = list.stream().mapToDouble(com.spendlocker.model.Investment::getPrincipalAmount).sum();
            double current = list.stream().mapToDouble(com.spendlocker.model.Investment::getCurrentTotalValue).sum();
            double gain = current - invested;
            double gainPct = invested > 0 ? (gain / invested) * 100 : 0;
            String sub = String.format("%s %s (%s%.1f%%)", gain >= 0 ? "up" : "down",
                    currency(Math.abs(gain)), gain >= 0 ? "+" : "-", Math.abs(gainPct));
            String type = entry.getKey();
            grid.getChildren().add(kindCard("#2C7A6E", type, list.size() + " held", currency(current), sub,
                    () -> onInvestmentTypeDrilldown.accept(type)));
        }

        double monthlySpend = expenseDao.sumForCurrentMonth();
        int expenseCount = expenseDao.findAll().size();
        grid.getChildren().add(kindCard("#A32D2D", "Expense", expenseCount + " recorded",
                currency(monthlySpend) + " /mo", "a month · " + currency(monthlySpend * 12) + " a year", null));

        return new VBox(4, heading, subheading, grid);
    }

    private VBox kindCard(String swatchColor, String name, String countLine, String value, String subLine, Runnable onClick) {
        Region swatch = new Region();
        swatch.getStyleClass().add("legend-swatch");
        swatch.setStyle("-fx-background-color: " + swatchColor + ";");
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: 600;");
        HBox nameRow = new HBox(6, swatch, nameLabel);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label countLabel = new Label(countLine);
        countLabel.getStyleClass().add("text-caption");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("title-2");

        Label subLabel = new Label(subLine);
        subLabel.getStyleClass().add("text-caption");
        if (subLine.startsWith("up")) subLabel.setStyle("-fx-text-fill: -color-success-fg;");
        else if (subLine.startsWith("down")) subLabel.setStyle("-fx-text-fill: -color-danger-fg;");

        VBox card = new VBox(4, nameRow, countLabel, valueLabel, subLabel);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(228);
        if (onClick != null) {
            card.setCursor(javafx.scene.Cursor.HAND);
            card.setOnMouseClicked(e -> onClick.run());
        }
        return card;
    }

    /** Indian-style short money: Cr / L / k thresholds, matching the reference's KPI sub-notes. */
    private String shortMoney(double value) {
        String symbol = "₹";
        if (value >= 1_00_00_000) return symbol + String.format("%.2f Cr", value / 1_00_00_000.0);
        if (value >= 1_00_000) return symbol + String.format("%.2f L", value / 1_00_000.0);
        if (value >= 1_000) return symbol + String.format("%.0fk", value / 1_000.0);
        return symbol + String.format("%.0f", value);
    }

    private String currency(double amount) {
        return com.spendlocker.util.MoneyFormat.currency(amount);
    }
}
