package com.spendlocker.ui;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.dao.RecurringExpenseDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.model.Investment;
import com.spendlocker.model.RecurringExpense;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The reference design's "Net worth" tab: an asset-mix donut beside a totals list. */
public class NetWorthView extends BorderPane {

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final InvestmentDao investmentDao = new InvestmentDao();
    private final RecurringExpenseDao recurringExpenseDao = new RecurringExpenseDao();
    private final VBox content = new VBox(20);

    public NetWorthView() {
        setPadding(new Insets(24));

        Label title = new Label("Net worth", new FontIcon(Feather.PIE_CHART));
        title.getStyleClass().add("title-1");
        HBox toolbar = new HBox(title);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 16, 0));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        setTop(toolbar);
        setCenter(scroll);
        refresh();
    }

    public void refresh() {
        content.getChildren().clear();

        List<FixedDeposit> activeDeposits = fixedDepositDao.findAll().stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .toList();
        double fdPrincipal = activeDeposits.stream().mapToDouble(FixedDeposit::getPrincipal).sum();
        double fdMaturityValue = activeDeposits.stream().mapToDouble(FixedDepositCalculator::maturityAmount).sum();

        List<Investment> investments = investmentDao.findAll();
        Map<String, List<Investment>> byType = investments.stream()
                .collect(Collectors.groupingBy(Investment::getInvestmentType, LinkedHashMap::new, Collectors.toList()));

        double investedTotal = investments.stream().mapToDouble(Investment::getPrincipalAmount).sum();
        double investmentsCurrentTotal = investments.stream().mapToDouble(Investment::getCurrentTotalValue).sum();
        double totalWealth = fdPrincipal + investmentsCurrentTotal;

        LinkedHashMap<String, Double> donutData = new LinkedHashMap<>();
        donutData.put("Fixed deposits", fdPrincipal);
        byType.forEach((type, list) -> donutData.put(type, list.stream().mapToDouble(Investment::getCurrentTotalValue).sum()));

        DonutChart donut = new DonutChart();
        donut.setPrefSize(220, 220);
        donut.setMinSize(220, 220);
        donut.setData(donutData, MoneyFormat.currency(totalWealth), "total wealth");

        VBox totalsList = new VBox(2);
        int i = 0, n = donutData.size();
        totalsList.getChildren().add(totalsRow("Fixed deposits", fdPrincipal, null, totalWealth, shade(i++, n)));
        for (Map.Entry<String, List<Investment>> entry : byType.entrySet()) {
            double invested = entry.getValue().stream().mapToDouble(Investment::getPrincipalAmount).sum();
            double current = entry.getValue().stream().mapToDouble(Investment::getCurrentTotalValue).sum();
            totalsList.getChildren().add(totalsRow(entry.getKey(), current, current - invested, totalWealth, shade(i++, n)));
        }

        Label grandLabel = new Label("Total wealth");
        grandLabel.getStyleClass().add("title-3");
        Label grandValue = new Label(MoneyFormat.currency(totalWealth));
        grandValue.getStyleClass().add("title-2");
        Region grandSpacer = new Region();
        HBox.setHgrow(grandSpacer, Priority.ALWAYS);
        HBox grandRow = new HBox(10, grandLabel, grandSpacer, grandValue);
        grandRow.setAlignment(Pos.CENTER_LEFT);
        grandRow.getStyleClass().add("dash-section");
        grandRow.setPadding(new Insets(12, 4, 4, 4));

        VBox rightColumn = new VBox(4, totalsList, grandRow);

        HBox row = new HBox(24, donut, rightColumn);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        double committedMonthly = recurringExpenseDao.findAll().stream()
                .filter(RecurringExpense::isActive)
                .mapToDouble(r -> switch (r.getFrequency()) {
                    case WEEKLY -> r.getAmount() * 52 / 12;
                    case MONTHLY -> r.getAmount();
                    case YEARLY -> r.getAmount() / 12;
                })
                .sum();
        double unrealisedGain = investmentsCurrentTotal - investedTotal;
        double onceMatured = totalWealth - fdPrincipal + fdMaturityValue;

        VBox footnotes = new VBox(4,
                footnote("Recurring expenses, not counted above — " + MoneyFormat.currency(committedMonthly) + " a month"),
                footnote("Unrealised gain on market holdings — " + (unrealisedGain >= 0 ? "+" : "-") + MoneyFormat.currency(Math.abs(unrealisedGain))),
                footnote("Once every deposit runs its full term — " + MoneyFormat.currency(onceMatured)));

        content.getChildren().addAll(row, new Separator(), footnotes);
    }

    private VBox totalsRow(String name, double value, Double gain, double totalWealth, javafx.scene.paint.Color color) {
        Region swatch = new Region();
        swatch.getStyleClass().add("legend-swatch");
        swatch.setStyle("-fx-background-color: " + toHex(color) + ";");
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: 600;");
        HBox nameRow = new HBox(6, swatch, nameLabel);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label valueLabel = new Label(MoneyFormat.currency(value));
        HBox valueRow = new HBox(8, valueLabel);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        if (gain != null) {
            Label gainLabel = new Label((gain >= 0 ? "+" : "−") + MoneyFormat.currency(Math.abs(gain)));
            gainLabel.getStyleClass().addAll("tag", gain >= 0 ? "positive" : "negative");
            valueRow.getChildren().add(gainLabel);
        }

        double share = totalWealth > 0 ? (value / totalWealth) * 100 : 0;
        Label shareLabel = new Label(String.format("%.0f%% of total", share));
        shareLabel.getStyleClass().add("text-caption");

        VBox rowBox = new VBox(2, nameRow, valueRow, shareLabel);
        rowBox.setPadding(new Insets(8, 4, 8, 4));
        rowBox.getStyleClass().add("next-up-row");
        rowBox.getStyleClass().add("divider");
        return rowBox;
    }

    private Label footnote(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-caption");
        return label;
    }

    private javafx.scene.paint.Color shade(int i, int n) {
        double lightness = 25 + (n <= 1 ? 0 : ((double) i / (n - 1)) * 36);
        return hsl(172, 34, lightness);
    }

    private javafx.scene.paint.Color hsl(double h, double s, double l) {
        s /= 100;
        l /= 100;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return javafx.scene.paint.Color.color(r + m, g + m, b + m);
    }

    private String toHex(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}
