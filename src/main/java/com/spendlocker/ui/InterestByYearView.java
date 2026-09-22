package com.spendlocker.ui;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** The reference design's "Interest by year" tab: a brass/teal column chart + a totals table. */
public class InterestByYearView extends BorderPane {

    private record YearRow(int year, int deposits, double principal, double interest) {
        double yieldPercent() {
            return principal > 0 ? (interest / principal) * 100 : 0;
        }
    }

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final VBox content = new VBox(20);

    public InterestByYearView() {
        setPadding(new Insets(24));

        Label title = new Label("Interest by year", new FontIcon(Feather.BAR_CHART_2));
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

        List<FixedDeposit> all = fixedDepositDao.findAll();
        if (all.isEmpty()) {
            Label empty = new Label("No deposits yet.");
            empty.getStyleClass().add("text-caption");
            content.getChildren().add(empty);
            return;
        }

        Map<Integer, List<FixedDeposit>> byYear = all.stream()
                .collect(Collectors.groupingBy(fd -> LocalDate.parse(fd.getMaturityDate()).getYear(), LinkedHashMap::new, Collectors.toList()));

        List<YearRow> rows = byYear.entrySet().stream()
                .map(e -> new YearRow(e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToDouble(FixedDeposit::getPrincipal).sum(),
                        e.getValue().stream().mapToDouble(FixedDepositCalculator::interest).sum()))
                .sorted(Comparator.comparingInt(YearRow::year))
                .toList();

        int currentYear = LocalDate.now().getYear();

        Label note = new Label("Interest lands in the year each deposit matures. Brass years are already banked; teal is still to come.");
        note.getStyleClass().add("text-caption");
        note.setWrapText(true);

        YearColumnChart chart = new YearColumnChart();
        chart.setPrefHeight(220);
        chart.setMinHeight(220);
        String symbol = NumberFormat.getCurrencyInstance(new Locale("en", "IN")).getCurrency().getSymbol();
        chart.setData(
                rows.stream().map(r -> String.valueOf(r.year())).toList(),
                rows.stream().map(r -> r.deposits() + (r.deposits() == 1 ? " FD" : " FDs")).toList(),
                rows.stream().map(YearRow::interest).toList(),
                rows.stream().map(r -> r.year() < currentYear).toList(),
                symbol);

        content.getChildren().addAll(note, chart, buildTable(rows));
    }

    private VBox buildTable(List<YearRow> rows) {
        TableView<YearRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        table.setPrefHeight(Math.min(46 * (rows.size() + 2), 400));

        TableColumn<YearRow, String> yearCol = new TableColumn<>("Year");
        yearCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().year())));

        TableColumn<YearRow, Number> depositsCol = new TableColumn<>("Deposits");
        depositsCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().deposits()));

        TableColumn<YearRow, Number> principalCol = new TableColumn<>("Principal");
        principalCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().principal()));
        principalCol.setCellFactory(col -> currencyCell());

        TableColumn<YearRow, Number> interestCol = new TableColumn<>("Interest earned");
        interestCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().interest()));
        interestCol.setCellFactory(col -> currencyCell());

        TableColumn<YearRow, Number> yieldCol = new TableColumn<>("Effective yield");
        yieldCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().yieldPercent()));
        yieldCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.1f%%", value.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });

        table.getColumns().addAll(yearCol, depositsCol, principalCol, interestCol, yieldCol);

        int totalDeposits = rows.stream().mapToInt(YearRow::deposits).sum();
        double totalPrincipal = rows.stream().mapToDouble(YearRow::principal).sum();
        double totalInterest = rows.stream().mapToDouble(YearRow::interest).sum();

        Label totalLabel = new Label("Total");
        totalLabel.setStyle("-fx-font-weight: bold;");
        Label totalDepositsLabel = new Label(String.valueOf(totalDeposits));
        totalDepositsLabel.setStyle("-fx-font-weight: bold;");
        Label totalPrincipalLabel = new Label(MoneyFormat.currency(totalPrincipal));
        totalPrincipalLabel.setStyle("-fx-font-weight: bold;");
        Label totalInterestLabel = new Label(MoneyFormat.currency(totalInterest));
        totalInterestLabel.setStyle("-fx-font-weight: bold;");

        HBox totalsRow = new HBox(24, totalLabel, totalDepositsLabel, totalPrincipalLabel, totalInterestLabel);
        totalsRow.getStyleClass().add("dash-section");
        totalsRow.setPadding(new Insets(10, 4, 4, 4));

        return new VBox(0, table, totalsRow);
    }

    private TableCell<YearRow, Number> currencyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : MoneyFormat.currency(value.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        };
    }
}
