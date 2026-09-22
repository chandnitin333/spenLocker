package com.spendlocker.ui;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** The reference design's "Banks" tab: a metric toggle, a donut, and a ranked bar list. */
public class BanksView extends BorderPane {

    private static final String PRINCIPAL = "Principal";
    private static final String INTEREST = "Interest";
    private static final String MATURITY = "Maturity value";
    private static final double TRACK_WIDTH = 220;

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final Consumer<String> onBankDrilldown;
    private final VBox content = new VBox(20);
    private String metric = PRINCIPAL;

    public BanksView(Consumer<String> onBankDrilldown) {
        this.onBankDrilldown = onBankDrilldown;
        setPadding(new Insets(24));

        Label title = new Label("Banks", new FontIcon(Feather.PIE_CHART));
        title.getStyleClass().add("title-1");
        HBox toolbar = new HBox(title);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 12, 0));

        HBox seg = buildMetricSegment();
        VBox header = new VBox(toolbar, seg);
        seg.setPadding(new Insets(16, 0, 0, 0));

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        setTop(header);
        setCenter(scroll);
        refresh();
    }

    private HBox buildMetricSegment() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton principalBtn = segButton(PRINCIPAL, group, true);
        ToggleButton interestBtn = segButton(INTEREST, group, false);
        ToggleButton maturityBtn = segButton(MATURITY, group, false);
        principalBtn.setOnAction(e -> { metric = PRINCIPAL; refresh(); });
        interestBtn.setOnAction(e -> { metric = INTEREST; refresh(); });
        maturityBtn.setOnAction(e -> { metric = MATURITY; refresh(); });
        HBox seg = new HBox(principalBtn, interestBtn, maturityBtn);
        seg.getStyleClass().add("seg-group");
        return seg;
    }

    private ToggleButton segButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        return button;
    }

    public void refresh() {
        content.getChildren().clear();

        List<FixedDeposit> active = fixedDepositDao.findAll().stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .toList();
        if (active.isEmpty()) {
            Label empty = new Label("No active deposits yet.");
            empty.getStyleClass().add("text-caption");
            content.getChildren().add(empty);
            return;
        }

        ToDoubleFunction<FixedDeposit> metricValue = switch (metric) {
            case INTEREST -> FixedDepositCalculator::interest;
            case MATURITY -> FixedDepositCalculator::maturityAmount;
            default -> FixedDeposit::getPrincipal;
        };

        Map<String, List<FixedDeposit>> byBank = active.stream()
                .collect(Collectors.groupingBy(FixedDeposit::getBank, LinkedHashMap::new, Collectors.toList()));

        LinkedHashMap<String, Double> ranked = byBank.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(metricValue).sum(),
                        (a, b) -> a, LinkedHashMap::new))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        double total = ranked.values().stream().mapToDouble(Double::doubleValue).sum();

        DonutChart donut = new DonutChart();
        donut.setPrefSize(220, 220);
        donut.setMinSize(220, 220);
        donut.setData(ranked, MoneyFormat.currency(total), "at " + metric.toLowerCase(java.util.Locale.ROOT));

        VBox barList = new VBox(2);
        int i = 0;
        int n = ranked.size();
        for (Map.Entry<String, Double> entry : ranked.entrySet()) {
            String bank = entry.getKey();
            double value = entry.getValue();
            double fraction = total > 0 ? value / total : 0;
            List<FixedDeposit> deposits = byBank.get(bank);
            double avgRate = FixedDepositCalculator.weightedAverageRate(deposits);
            barList.getChildren().add(bankRow(bank, deposits.size(), avgRate, value, fraction, shade(i, n)));
            i++;
        }

        HBox row = new HBox(24, donut, barList);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(barList, Priority.ALWAYS);

        content.getChildren().add(row);
    }

    private HBox bankRow(String bank, int count, double avgRate, double value, double fraction, Color color) {
        Label name = new Label(bank + " ›");
        name.setStyle("-fx-font-weight: 600; -fx-cursor: hand;");
        name.setOnMouseClicked(e -> onBankDrilldown.accept(bank));
        Label sub = new Label(count + (count == 1 ? " deposit · " : " deposits · ") + String.format("%.2f%%", avgRate));
        sub.getStyleClass().add("text-caption");
        VBox left = new VBox(2, name, sub);
        left.setPrefWidth(180);
        left.setMinWidth(180);

        Region track = new Region();
        track.setPrefSize(TRACK_WIDTH, 11);
        track.setMinSize(TRACK_WIDTH, 11);
        track.setMaxSize(TRACK_WIDTH, 11);
        track.getStyleClass().add("bank-track");

        Region fill = new Region();
        fill.setPrefSize(TRACK_WIDTH * Math.max(fraction, 0.01), 11);
        fill.setMinHeight(11);
        fill.setMaxHeight(11);
        fill.setStyle("-fx-background-color: " + toHex(color) + "; -fx-background-radius: 3;");
        javafx.scene.layout.StackPane trackStack = new javafx.scene.layout.StackPane(track, fill);
        trackStack.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(trackStack, new Tooltip(bank + " — " + MoneyFormat.currency(value) + String.format(" (%.1f%%)", fraction * 100)));

        Label valueLabel = new Label(MoneyFormat.currency(value));
        valueLabel.setStyle("-fx-font-weight: 600;");
        Label pctLabel = new Label(String.format("%.1f%%", fraction * 100));
        pctLabel.getStyleClass().add("text-caption");
        VBox right = new VBox(2, valueLabel, pctLabel);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPrefWidth(110);

        HBox row = new HBox(14, left, trackStack, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        return row;
    }

    private Color shade(int i, int n) {
        double lightness = 25 + (n <= 1 ? 0 : ((double) i / (n - 1)) * 36);
        return hsl(172, 34, lightness);
    }

    private Color hsl(double h, double s, double l) {
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
        return Color.color(r + m, g + m, b + m);
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
}
