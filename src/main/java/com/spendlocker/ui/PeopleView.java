package com.spendlocker.ui;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** The reference design's "People" tab: a card grid, one per depositor. */
public class PeopleView extends BorderPane {

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final Consumer<String> onDepositorDrilldown;
    private final FlowPane grid = new FlowPane(16, 16);

    public PeopleView(Consumer<String> onDepositorDrilldown) {
        this.onDepositorDrilldown = onDepositorDrilldown;
        setPadding(new Insets(24));

        Label title = new Label("People", new FontIcon(Feather.USERS));
        title.getStyleClass().add("title-1");
        HBox toolbar = new HBox(title);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 16, 0));

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        setTop(toolbar);
        setCenter(scroll);
        refresh();
    }

    public void refresh() {
        grid.getChildren().clear();

        List<FixedDeposit> active = fixedDepositDao.findAll().stream()
                .filter(fd -> FixedDepositCalculator.isActive(fd.getMaturityDate()))
                .toList();
        if (active.isEmpty()) {
            Label empty = new Label("No active deposits yet.");
            empty.getStyleClass().add("text-caption");
            grid.getChildren().add(empty);
            return;
        }

        Map<String, List<FixedDeposit>> byDepositor = active.stream()
                .collect(Collectors.groupingBy(FixedDeposit::getDepositor, LinkedHashMap::new, Collectors.toList()));

        double bookTotal = active.stream().mapToDouble(FixedDeposit::getPrincipal).sum();

        LinkedHashMap<String, Double> ranked = byDepositor.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(FixedDeposit::getPrincipal).sum(),
                        (a, b) -> a, LinkedHashMap::new))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);

        double largest = ranked.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);

        for (Map.Entry<String, Double> entry : ranked.entrySet()) {
            String depositor = entry.getKey();
            double principal = entry.getValue();
            List<FixedDeposit> deposits = byDepositor.get(depositor);
            grid.getChildren().add(personCard(depositor, principal, bookTotal, largest, deposits));
        }
    }

    private VBox personCard(String depositor, double principal, double bookTotal, double largest, List<FixedDeposit> deposits) {
        Label name = new Label(depositor);
        name.getStyleClass().add("title-3");

        Label amount = new Label(MoneyFormat.currency(principal));
        amount.getStyleClass().add("title-2");

        double share = bookTotal > 0 ? (principal / bookTotal) * 100 : 0;
        Label shareLabel = new Label(String.format("%.1f%% of the book", share));
        shareLabel.getStyleClass().add("text-caption");

        Region track = new Region();
        track.getStyleClass().add("bank-track");
        track.setPrefSize(200, 6);
        track.setMinSize(200, 6);
        Region fill = new Region();
        double fraction = largest > 0 ? principal / largest : 0;
        fill.setPrefSize(200 * Math.max(fraction, 0.02), 6);
        fill.setMinHeight(6);
        fill.setStyle("-fx-background-color: -color-accent-emphasis; -fx-background-radius: 3;");
        javafx.scene.layout.StackPane progress = new javafx.scene.layout.StackPane(track, fill);
        progress.setAlignment(Pos.CENTER_LEFT);

        double avgRate = FixedDepositCalculator.weightedAverageRate(deposits);
        double interestDue = deposits.stream().mapToDouble(FixedDepositCalculator::interest).sum();
        String nextMatures = deposits.stream()
                .map(FixedDeposit::getMaturityDate)
                .min(java.util.Comparator.naturalOrder())
                .map(d -> LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM yy")))
                .orElse("—");

        javafx.scene.layout.GridPane facts = new javafx.scene.layout.GridPane();
        facts.setHgap(16);
        facts.setVgap(4);
        addFact(facts, 0, "Deposits", String.valueOf(deposits.size()));
        addFact(facts, 1, "Average rate", String.format("%.2f%%", avgRate));
        addFact(facts, 2, "Interest due", MoneyFormat.currency(interestDue));
        addFact(facts, 3, "Next matures", nextMatures);

        VBox card = new VBox(6, name, amount, shareLabel, progress, facts);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(240);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> onDepositorDrilldown.accept(depositor));
        return card;
    }

    private void addFact(javafx.scene.layout.GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("text-caption");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-weight: 600;");
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }
}
