package com.spendlocker.ui;

import com.spendlocker.dao.InvestmentDao;
import com.spendlocker.excel.ExcelImportService;
import com.spendlocker.excel.InvestmentColumnMapping;
import com.spendlocker.google.GoogleDriveService;
import com.spendlocker.google.GoogleSheetsService;
import com.spendlocker.model.Investment;
import com.spendlocker.model.InvestmentType;
import com.spendlocker.ui.dialog.InvestmentColumnMappingDialog;
import com.spendlocker.ui.dialog.InvestmentFormDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import com.spendlocker.util.FinancialYear;
import com.spendlocker.util.LoadingButton;
import com.spendlocker.util.TableColumnUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

public class InvestmentsView extends BorderPane {

    private static final String ALL_TYPES = "All Types";
    private static final String ALL_TIME = "All Time";
    private static final String THIS_WEEK = "This Week";
    private static final String THIS_MONTH = "This Month";
    private static final String THIS_YEAR = "This Year";
    private static final int PRIOR_FINANCIAL_YEARS_SHOWN = 5;

    private final InvestmentDao investmentDao = new InvestmentDao();
    private final ExcelImportService excelImportService = new ExcelImportService();
    private final GoogleDriveService driveService = GoogleDriveService.getInstance();
    private final GoogleSheetsService sheetsService = new GoogleSheetsService();
    private final TableView<Investment> table = new TableView<>();
    private final ObservableList<Investment> data = FXCollections.observableArrayList();
    private final FilteredList<Investment> filteredData = new FilteredList<>(data, i -> true);
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final ComboBox<String> dateRangeFilter = new ComboBox<>();
    private final TextField searchField = new TextField();

    public InvestmentsView() {
        setPadding(new Insets(24));

        Label title = new Label("Investments", new FontIcon(Feather.TRENDING_UP));
        title.getStyleClass().add("title-1");

        Button syncButton = new Button("Sync", new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
        syncButton.setOnAction(e -> onSyncToDrive(syncButton));

        Button addButton = new Button("Add Investment", new FontIcon(Feather.PLUS));
        addButton.getStyleClass().add("accent");
        addButton.setOnAction(e -> onAdd());
        Button editButton = new Button("Edit", new FontIcon(Feather.EDIT_2));
        editButton.setOnAction(e -> onEdit());
        Button deleteButton = new Button("Delete", new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().add("danger");
        deleteButton.setOnAction(e -> onDelete());
        Button importButton = new Button("Import from Excel", new FontIcon(Feather.DOWNLOAD));
        importButton.setOnAction(e -> onImport());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, title, spacer, syncButton, importButton, addButton, editButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 12, 0));

        typeFilter.setValue(ALL_TYPES);
        typeFilter.setOnAction(e -> applyFilters());

        dateRangeFilter.getItems().addAll(ALL_TIME, THIS_WEEK, THIS_MONTH, THIS_YEAR);
        dateRangeFilter.getItems().addAll(FinancialYear.recentLabels(PRIOR_FINANCIAL_YEARS_SHOWN));
        dateRangeFilter.setValue(ALL_TIME);
        dateRangeFilter.setOnAction(e -> applyFilters());

        searchField.setPromptText("Search asset, ticker or notes");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());

        HBox filterBar = new HBox(10, searchField,
                new Label("Category:", new FontIcon(Feather.FILTER)), typeFilter,
                new Label("Purchased:"), dateRangeFilter);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(0, 0, 16, 0));

        VBox header = new VBox(toolbar, filterBar);

        buildColumns();
        table.setItems(filteredData);
        // Unconstrained (not flex-last-column): fixed per-column widths so a wide asset name
        // can't squeeze the Actions column down to nothing — a horizontal scrollbar appears instead.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(emptyState());

        setTop(header);
        setCenter(table);
        refresh();
    }

    private VBox emptyState() {
        FontIcon icon = new FontIcon(Feather.TRENDING_UP);
        icon.setIconSize(32);
        icon.getStyleClass().add("text-caption");
        Label message = new Label("No investments yet — click Add Investment to get started.");
        message.getStyleClass().add("text-caption");
        VBox box = new VBox(10, icon, message);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Invoked by the Cmd/Ctrl+N shortcut when this view is the active tab. */
    public void triggerAddAction() {
        onAdd();
    }

    public void refresh() {
        data.setAll(investmentDao.findAll());

        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (InvestmentType type : InvestmentType.values()) {
            types.add(type.dbValue());
        }
        types.addAll(investmentDao.distinctTypes());
        String previousSelection = typeFilter.getValue();
        typeFilter.getItems().setAll(ALL_TYPES);
        typeFilter.getItems().addAll(types);
        typeFilter.setValue(typeFilter.getItems().contains(previousSelection) ? previousSelection : ALL_TYPES);

        applyFilters();
    }

    /** Drill-down from the Dashboard's Investment Allocation chart. */
    public void filterByType(String type) {
        if (typeFilter.getItems().contains(type)) {
            typeFilter.setValue(type);
        }
        applyFilters();
    }

    private void applyFilters() {
        String type = typeFilter.getValue();
        String range = dateRangeFilter.getValue();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredData.setPredicate(investment ->
                (ALL_TYPES.equals(type) || investment.getInvestmentType().equals(type))
                        && matchesDateRange(investment.getPurchaseDate(), range)
                        && matchesSearch(investment, query));
    }

    private boolean matchesSearch(Investment investment, String query) {
        if (query.isEmpty()) return true;
        return containsIgnoreCase(investment.getAssetName(), query)
                || containsIgnoreCase(investment.getAssetTicker(), query)
                || containsIgnoreCase(investment.getNotes(), query);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesDateRange(String purchaseDate, String range) {
        if (ALL_TIME.equals(range) || purchaseDate == null) return true;
        LocalDate date;
        try {
            date = LocalDate.parse(purchaseDate);
        } catch (Exception e) {
            return true;
        }
        if (FinancialYear.isFinancialYearLabel(range)) {
            return !date.isBefore(FinancialYear.startOf(range)) && !date.isAfter(FinancialYear.endOf(range));
        }
        LocalDate today = LocalDate.now();
        return switch (range) {
            case THIS_WEEK -> {
                WeekFields weekFields = WeekFields.of(Locale.getDefault());
                yield date.get(weekFields.weekOfWeekBasedYear()) == today.get(weekFields.weekOfWeekBasedYear())
                        && date.getYear() == today.getYear();
            }
            case THIS_MONTH -> date.getMonth() == today.getMonth() && date.getYear() == today.getYear();
            case THIS_YEAR -> date.getYear() == today.getYear();
            default -> true;
        };
    }

    private void onImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select an .xlsx file of investments to import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected == null) return;

        try {
            List<String> headers = excelImportService.readHeaders(selected);
            if (headers.isEmpty()) {
                AlertUtil.error("Import failed", "No header row found in " + selected.getName());
                return;
            }
            InvestmentColumnMappingDialog.show(headers).ifPresent(mapping -> {
                try {
                    var imported = excelImportService.importInvestments(selected, mapping);
                    refresh();
                    AlertUtil.info("Import complete", imported.size() + " investments imported from " + selected.getName());
                } catch (IOException ex) {
                    AlertUtil.error("Import failed", ex.getMessage());
                }
            });
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private TableCell<Investment, Number> currencyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : com.spendlocker.util.MoneyFormat.currency(value.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        };
    }

    private void buildColumns() {
        TableColumn<Investment, String> nameCol = new TableColumn<>("Asset");
        TableColumnUtil.fitHeader(nameCol, 180);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAssetName()));

        TableColumn<Investment, String> tickerCol = new TableColumn<>("Ticker");
        TableColumnUtil.fitHeader(tickerCol, 80);
        tickerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAssetTicker()));

        TableColumn<Investment, String> typeCol = new TableColumn<>("Type");
        TableColumnUtil.fitHeader(typeCol, 120);
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getInvestmentType()));

        TableColumn<Investment, String> purchaseDateCol = new TableColumn<>("Purchase Date");
        TableColumnUtil.fitHeader(purchaseDateCol, 110);
        purchaseDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurchaseDate()));

        TableColumn<Investment, Number> principalCol = new TableColumn<>("Principal");
        TableColumnUtil.fitHeader(principalCol, 100);
        principalCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrincipalAmount()));
        principalCol.setCellFactory(col -> currencyCell());

        TableColumn<Investment, Number> unitsCol = new TableColumn<>("Units");
        TableColumnUtil.fitHeader(unitsCol, 90);
        unitsCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotalUnits()));

        TableColumn<Investment, Number> unitPriceCol = new TableColumn<>("Unit Price");
        TableColumnUtil.fitHeader(unitPriceCol, 100);
        unitPriceCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCurrentUnitPrice()));
        unitPriceCol.setCellFactory(col -> currencyCell());

        TableColumn<Investment, Number> currentValueCol = new TableColumn<>("Current Value");
        TableColumnUtil.fitHeader(currentValueCol, 110);
        currentValueCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCurrentTotalValue()));
        currentValueCol.setCellFactory(col -> currencyCell());

        TableColumn<Investment, Number> roiCol = new TableColumn<>("ROI %");
        TableColumnUtil.fitHeader(roiCol, 90);
        roiCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getRoiPercent()));
        roiCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                double roi = value.doubleValue();
                setText(String.format("%s%.2f%%", roi >= 0 ? "+" : "", roi));
                setStyle(roi >= 0 ? "-fx-text-fill: -color-success-fg;" : "-fx-text-fill: -color-danger-fg;");
            }
        });

        TableColumn<Investment, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setSortable(false);
        TableColumnUtil.fitHeader(actionsCol, 90);
        actionsCol.setResizable(false);
        actionsCol.setMaxWidth(actionsCol.getPrefWidth());
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button(null, new FontIcon(Feather.EDIT_2));
            private final Button deleteBtn = new Button(null, new FontIcon(Feather.TRASH_2));
            private final HBox box = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("icon-button");
                deleteBtn.getStyleClass().add("icon-button");
                box.setAlignment(Pos.CENTER);
                editBtn.setOnAction(e -> onEdit(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> onDelete(getTableRow().getItem()));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(nameCol, tickerCol, typeCol, purchaseDateCol, principalCol, unitsCol, unitPriceCol, currentValueCol, roiCol, actionsCol);

        // Let the flexible columns grow/shrink together to fill whatever width the window
        // actually has, instead of sitting at fixed pixel sizes (dead space on a wide screen,
        // needless scrollbar on a narrow one). Actions stays fixed; +20 covers the scrollbar/border.
        TableColumnUtil.bindProportionalWidths(table, actionsCol.getPrefWidth() + 20,
                nameCol, tickerCol, typeCol, purchaseDateCol, principalCol, unitsCol, unitPriceCol, currentValueCol, roiCol);
    }

    /**
     * Pushes every investment to the shared "SpendLocker Sync" Google Sheet, overwriting its
     * Investments tab — the same sheet the Android companion app reads/writes.
     */
    private void onSyncToDrive(Button trigger) {
        List<Investment> snapshot = investmentDao.findAll();
        LoadingButton.startLoading(trigger);
        SessionGuard.suspendAutoLock();
        new Thread(() -> {
            try {
                // A previous sign-in's cached token restores silently here (no browser popup)
                // if still valid — only truly signing in for the first time opens a browser.
                if (!driveService.isSignedIn()) {
                    driveService.signIn();
                }
                sheetsService.syncInvestments(snapshot);
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    LoadingButton.stopLoading(trigger);
                    AlertUtil.info("Synced", snapshot.size() + " investments synced to Google Sheets.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    LoadingButton.stopLoading(trigger);
                    AlertUtil.error("Sync failed", ex.getMessage());
                });
            }
        }, "sheets-sync-investments").start();
    }

    private void onAdd() {
        InvestmentFormDialog.show(null).ifPresent(investment -> {
            investmentDao.insert(investment);
            refresh();
        });
    }

    private void onEdit() {
        onEdit(table.getSelectionModel().getSelectedItem());
    }

    private void onEdit(Investment selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select an investment to edit.");
            return;
        }
        InvestmentFormDialog.show(selected).ifPresent(investment -> {
            investmentDao.update(investment);
            refresh();
        });
    }

    private void onDelete() {
        onDelete(table.getSelectionModel().getSelectedItem());
    }

    private void onDelete(Investment selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select an investment to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Move \"" + selected.getAssetName() + "\" to Trash?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Investment");
        confirm.setHeaderText(null);
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        investmentDao.softDelete(selected.getId());
        refresh();
    }
}
