package com.spendlocker.ui;

import com.spendlocker.model.Document;
import com.spendlocker.model.Expense;
import com.spendlocker.model.Investment;
import com.spendlocker.search.SearchResults;
import com.spendlocker.search.SearchService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class SearchResultsView extends VBox {

    private final SearchService searchService = new SearchService();

    public SearchResultsView(String query, Runnable openExpenses, Runnable openDocuments, Runnable openInvestments) {
        setSpacing(20);
        setPadding(new Insets(24));

        Label title = new Label("Search results for \"" + query + "\"", new FontIcon(Feather.SEARCH));
        title.getStyleClass().add("title-1");
        HBox header = new HBox(title);
        header.getStyleClass().add("page-header");
        header.setPadding(new Insets(0, 0, 12, 0));
        getChildren().add(header);

        SearchResults results = searchService.search(query);
        if (results.isEmpty()) {
            Label empty = new Label("No matches in expenses, documents, or investments.");
            empty.getStyleClass().add("text-caption");
            getChildren().add(empty);
            return;
        }

        if (!results.expenses().isEmpty()) {
            getChildren().add(resultSection("Expenses (" + results.expenses().size() + ")", openExpenses,
                    results.expenses().stream().map(this::expenseRow).toList()));
        }
        if (!results.documents().isEmpty()) {
            getChildren().add(resultSection("Documents (" + results.documents().size() + ")", openDocuments,
                    results.documents().stream().map(this::documentRow).toList()));
        }
        if (!results.investments().isEmpty()) {
            getChildren().add(resultSection("Investments (" + results.investments().size() + ")", openInvestments,
                    results.investments().stream().map(this::investmentRow).toList()));
        }
    }

    private VBox resultSection(String heading, Runnable openTab, java.util.List<HBox> rows) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("title-3");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button openButton = new Button("Open tab", new FontIcon(Feather.ARROW_RIGHT));
        openButton.setOnAction(e -> openTab.run());
        HBox headerRow = new HBox(8, headingLabel, spacer, openButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(6);
        list.getChildren().addAll(rows);

        VBox card = new VBox(10, headerRow, list);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private HBox expenseRow(Expense e) {
        Label text = new Label(String.format("%s — %s — %s — %s",
                e.getTransactionDate(), currency(e.getAmount()), e.getCategory(),
                e.getMerchantOrVendor() == null || e.getMerchantOrVendor().isBlank() ? "—" : e.getMerchantOrVendor()));
        HBox row = new HBox(text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox documentRow(Document d) {
        Label text = new Label(String.format("%s — %s — %s", d.getFileName(), d.getFileType(), d.getUploadDate()));
        HBox row = new HBox(text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox investmentRow(Investment inv) {
        Label text = new Label(String.format("%s (%s) — %s — %s",
                inv.getAssetName(), inv.getAssetTicker() == null ? "—" : inv.getAssetTicker(),
                inv.getInvestmentType(), inv.getPurchaseDate()));
        HBox row = new HBox(text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String currency(double amount) {
        return com.spendlocker.util.MoneyFormat.currency(amount);
    }
}
