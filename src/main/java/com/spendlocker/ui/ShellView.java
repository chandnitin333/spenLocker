package com.spendlocker.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class ShellView extends BorderPane {

    private final DashboardView dashboardView = new DashboardView(
            type -> {
                this.investmentsView.refresh();
                this.investmentsView.filterByType(type);
                this.setCenter(this.investmentsView);
                this.investmentsBtn.setSelected(true);
                this.currentAddAction = this.investmentsView::triggerAddAction;
            },
            bank -> {
                this.depositsView.refresh();
                this.depositsView.filterByBank(bank);
                this.setCenter(this.depositsView);
                this.depositsBtn.setSelected(true);
                this.currentAddAction = this.depositsView::triggerAddAction;
            });
    private final ExpensesView expensesView = new ExpensesView();
    private final InvestmentsView investmentsView = new InvestmentsView();
    private final DepositsView depositsView = new DepositsView();
    private final BanksView banksView = new BanksView(bank -> {
        this.depositsView.refresh();
        this.depositsView.filterByBank(bank);
        this.setCenter(this.depositsView);
        this.depositsBtn.setSelected(true);
        this.currentAddAction = this.depositsView::triggerAddAction;
    });
    private final DocumentsView documentsView = new DocumentsView();
    private final TrashView trashView = new TrashView();
    private final SettingsView settingsView = new SettingsView();
    private final ScrollPane settingsScroll = scrollable(settingsView);
    private final ScrollPane trashScroll = scrollable(trashView);
    private final ScrollPane dashboardScroll = scrollable(dashboardView);

    private ToggleButton expensesBtn;
    private ToggleButton investmentsBtn;
    private ToggleButton depositsBtn;
    private ToggleButton banksBtn;
    private ToggleButton documentsBtn;
    private TextField searchField;
    private Runnable currentAddAction;

    public ShellView() {
        setLeft(buildSidebar());
        setCenter(dashboardScroll);
        getStyleClass().add("shell");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.setPadding(new Insets(20, 12, 20, 12));
        sidebar.setPrefWidth(220);
        sidebar.getStyleClass().add("sidebar");

        Label brandIcon = new Label();
        brandIcon.setGraphic(new FontIcon(Feather.LOCK));
        brandIcon.getStyleClass().add("brand-icon");

        // "Wealth Book" wordmark, matching the reference's own masthead exactly: regular weight
        // "Wealth" + italic accent-colored "Book", with the uppercase tagline underneath.
        Label brandWealth = new Label("Wealth ");
        brandWealth.getStyleClass().add("brand-title");
        Label brandBook = new Label("Book");
        brandBook.getStyleClass().addAll("brand-title", "brand-title-accent");
        HBox wordmark = new HBox(brandWealth, brandBook);
        wordmark.setAlignment(Pos.BASELINE_LEFT);

        Label tagline = new Label("PERSONAL WEALTH MANAGEMENT");
        tagline.getStyleClass().add("brand-tagline");
        tagline.setWrapText(true);

        VBox brandText = new VBox(2, wordmark, tagline);

        HBox brandRow = new HBox(8, brandIcon, brandText);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.setPadding(new Insets(4, 8, 16, 8));

        searchField = new TextField();
        searchField.setPromptText("Search everything...");
        searchField.getStyleClass().add("search-field");
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !searchField.getText().isBlank()) {
                showSearchResults(searchField.getText());
            }
        });
        VBox searchBox = new VBox(searchField);
        searchBox.setPadding(new Insets(0, 8, 20, 8));

        ToggleGroup group = new ToggleGroup();
        ToggleButton dashboardBtn = navButton("Dashboard", Feather.HOME, group, true);
        depositsBtn = navButton("Deposits", Feather.CREDIT_CARD, group, false);
        banksBtn = navButton("Banks", Feather.PIE_CHART, group, false);
        expensesBtn = navButton("Expenses", Feather.CREDIT_CARD, group, false);
        investmentsBtn = navButton("Investments", Feather.TRENDING_UP, group, false);
        documentsBtn = navButton("Documents", Feather.FOLDER, group, false);
        ToggleButton trashBtn = navButton("Trash", Feather.TRASH_2, group, false);
        ToggleButton settingsBtn = navButton("Settings", Feather.SETTINGS, group, false);

        dashboardBtn.setOnAction(e -> { dashboardView.refresh(); setCenter(dashboardScroll); currentAddAction = null; });
        depositsBtn.setOnAction(e -> { depositsView.refresh(); setCenter(depositsView); currentAddAction = depositsView::triggerAddAction; });
        banksBtn.setOnAction(e -> { banksView.refresh(); setCenter(banksView); currentAddAction = null; });
        expensesBtn.setOnAction(e -> { expensesView.refresh(); setCenter(expensesView); currentAddAction = expensesView::triggerAddAction; });
        investmentsBtn.setOnAction(e -> { investmentsView.refresh(); setCenter(investmentsView); currentAddAction = investmentsView::triggerAddAction; });
        documentsBtn.setOnAction(e -> { documentsView.refresh(); setCenter(documentsView); currentAddAction = null; });
        trashBtn.setOnAction(e -> { trashView.refresh(); setCenter(trashScroll); currentAddAction = null; });
        settingsBtn.setOnAction(e -> { setCenter(settingsScroll); currentAddAction = null; });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(brandRow, searchBox, dashboardBtn, depositsBtn, banksBtn, expensesBtn, investmentsBtn, documentsBtn, trashBtn, spacer, settingsBtn);
        return sidebar;
    }

    /** Wired to the Cmd/Ctrl+F accelerator. */
    public void focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    /** Wired to the Cmd/Ctrl+N accelerator — adds on whichever tab supports it, no-op otherwise. */
    public void triggerAdd() {
        if (currentAddAction != null) {
            currentAddAction.run();
        }
    }

    private void showSearchResults(String query) {
        SearchResultsView results = new SearchResultsView(query,
                () -> { expensesView.refresh(); setCenter(expensesView); expensesBtn.setSelected(true); },
                () -> { documentsView.refresh(); setCenter(documentsView); documentsBtn.setSelected(true); },
                () -> { investmentsView.refresh(); setCenter(investmentsView); investmentsBtn.setSelected(true); });
        setCenter(results);
    }

    private ScrollPane scrollable(javafx.scene.Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return scrollPane;
    }

    private ToggleButton navButton(String text, Ikon icon, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text, new FontIcon(icon));
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(10);
        button.getStyleClass().add("nav-button");
        return button;
    }
}
