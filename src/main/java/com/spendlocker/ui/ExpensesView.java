package com.spendlocker.ui;

import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.excel.ColumnMapping;
import com.spendlocker.excel.CsvService;
import com.spendlocker.excel.ExcelExportService;
import com.spendlocker.excel.ExcelImportService;
import com.spendlocker.model.Expense;
import com.spendlocker.ui.dialog.ColumnMappingDialog;
import com.spendlocker.ui.dialog.ExpenseFormDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import com.spendlocker.util.FinancialYear;
import com.spendlocker.util.TableColumnUtil;
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

public class ExpensesView extends BorderPane {

    private static final String ALL_CATEGORIES = "All Categories";
    private static final String ALL_TIME = "All Time";
    private static final String THIS_WEEK = "This Week";
    private static final String THIS_MONTH = "This Month";
    private static final String THIS_YEAR = "This Year";
    private static final int PRIOR_FINANCIAL_YEARS_SHOWN = 5;

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final ExcelExportService excelExportService = new ExcelExportService();
    private final ExcelImportService excelImportService = new ExcelImportService();
    private final CsvService csvService = new CsvService();
    private final TableView<Expense> table = new TableView<>();
    private final ObservableList<Expense> data = FXCollections.observableArrayList();
    private final FilteredList<Expense> filteredData = new FilteredList<>(data, e -> true);
    private final ComboBox<String> dateRangeFilter = new ComboBox<>();
    private final ComboBox<String> categoryFilter = new ComboBox<>();

    public ExpensesView() {
        setPadding(new Insets(24));

        Label title = new Label("Expenses", new FontIcon(Feather.CREDIT_CARD));
        title.getStyleClass().add("title-1");

        Button addButton = new Button("Add Expense", new FontIcon(Feather.PLUS));
        addButton.getStyleClass().add("accent");
        addButton.setOnAction(e -> onAdd());
        Button duplicateButton = new Button("Duplicate", new FontIcon(Feather.COPY));
        duplicateButton.setOnAction(e -> onDuplicate());
        Button editButton = new Button("Edit", new FontIcon(Feather.EDIT_2));
        editButton.setOnAction(e -> onEdit());
        Button deleteButton = new Button("Delete", new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().add("danger");
        deleteButton.setOnAction(e -> onDelete());

        MenuButton exportMenu = new MenuButton("Export", new FontIcon(Feather.UPLOAD));
        MenuItem exportExcel = new MenuItem("Export to Excel (.xlsx)");
        exportExcel.setOnAction(e -> onExportExcel());
        MenuItem exportCsv = new MenuItem("Export to CSV");
        exportCsv.setOnAction(e -> onExportCsv());
        exportMenu.getItems().addAll(exportExcel, exportCsv);

        MenuButton importMenu = new MenuButton("Import", new FontIcon(Feather.DOWNLOAD));
        MenuItem importExcel = new MenuItem("Import from Excel (.xlsx)");
        importExcel.setOnAction(e -> onImportExcel());
        MenuItem importCsv = new MenuItem("Import from CSV");
        importCsv.setOnAction(e -> onImportCsv());
        importMenu.getItems().addAll(importExcel, importCsv);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, title, spacer, importMenu, exportMenu, addButton, duplicateButton, editButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 12, 0));

        dateRangeFilter.getItems().addAll(ALL_TIME, THIS_WEEK, THIS_MONTH, THIS_YEAR);
        dateRangeFilter.getItems().addAll(FinancialYear.recentLabels(PRIOR_FINANCIAL_YEARS_SHOWN));
        dateRangeFilter.setValue(ALL_TIME);
        dateRangeFilter.setOnAction(e -> applyFilter());

        categoryFilter.setValue(ALL_CATEGORIES);
        categoryFilter.setOnAction(e -> applyFilter());

        HBox filterBar = new HBox(10, new Label("Period:", new FontIcon(Feather.FILTER)), dateRangeFilter,
                new Label("Category:"), categoryFilter);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(0, 0, 16, 0));

        VBox header = new VBox(toolbar, filterBar);

        buildColumns();
        table.setItems(filteredData);
        // Unconstrained (not flex-last-column): fixed per-column widths so Notes/Merchant can't
        // squeeze the Actions column down to nothing — a horizontal scrollbar appears instead.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(emptyState());

        setTop(header);
        setCenter(table);
        refresh();
    }

    private VBox emptyState() {
        FontIcon icon = new FontIcon(Feather.CREDIT_CARD);
        icon.setIconSize(32);
        icon.getStyleClass().add("text-caption");
        Label message = new Label("No expenses yet — click Add Expense to get started.");
        message.getStyleClass().add("text-caption");
        VBox box = new VBox(10, icon, message);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    public void refresh() {
        data.setAll(expenseDao.findAll());
        String previousCategory = categoryFilter.getValue();
        categoryFilter.getItems().setAll(ALL_CATEGORIES);
        categoryFilter.getItems().addAll(expenseDao.distinctCategories());
        categoryFilter.setValue(categoryFilter.getItems().contains(previousCategory) ? previousCategory : ALL_CATEGORIES);
        applyFilter();
    }

    /** Invoked by the Cmd/Ctrl+N shortcut when this view is the active tab. */
    public void triggerAddAction() {
        onAdd();
    }

    /** Drill-down from the Dashboard's Spending by Category chart — shows every matching expense. */
    public void filterByCategory(String category) {
        if (categoryFilter.getItems().contains(category)) {
            categoryFilter.setValue(category);
        }
        dateRangeFilter.setValue(ALL_TIME);
        applyFilter();
    }

    private void applyFilter() {
        String range = dateRangeFilter.getValue();
        String category = categoryFilter.getValue();
        filteredData.setPredicate(expense -> matchesDateRange(expense.getTransactionDate(), range)
                && (ALL_CATEGORIES.equals(category) || category == null || category.equals(expense.getCategory())));
    }

    private boolean matchesDateRange(String transactionDate, String range) {
        if (ALL_TIME.equals(range) || transactionDate == null) return true;
        LocalDate date;
        try {
            date = LocalDate.parse(transactionDate);
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

    private void buildColumns() {
        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        TableColumnUtil.fitHeader(dateCol, 100);
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionDate()));

        TableColumn<Expense, Number> amountCol = new TableColumn<>("Amount");
        TableColumnUtil.fitHeader(amountCol, 100);
        amountCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getAmount()));

        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        TableColumnUtil.fitHeader(categoryCol, 140);
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));

        TableColumn<Expense, String> merchantCol = new TableColumn<>("Merchant/Vendor");
        TableColumnUtil.fitHeader(merchantCol, 180);
        merchantCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMerchantOrVendor()));

        TableColumn<Expense, String> paymentCol = new TableColumn<>("Payment Method");
        TableColumnUtil.fitHeader(paymentCol, 140);
        paymentCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPaymentMethod() != null ? c.getValue().getPaymentMethod().toString() : ""));

        TableColumn<Expense, String> notesCol = new TableColumn<>("Notes");
        TableColumnUtil.fitHeader(notesCol, 220);
        notesCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotes()));

        TableColumn<Expense, Void> actionsCol = new TableColumn<>("Actions");
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

        table.getColumns().addAll(dateCol, amountCol, categoryCol, merchantCol, paymentCol, notesCol, actionsCol);
    }

    private void onAdd() {
        ExpenseFormDialog.show(null).ifPresent(expense -> {
            expenseDao.insert(expense);
            refresh();
        });
    }

    private void onDuplicate() {
        Expense selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info("No selection", "Select an expense to duplicate.");
            return;
        }
        Expense draft = new Expense();
        draft.setTransactionDate(LocalDate.now().toString());
        draft.setAmount(selected.getAmount());
        draft.setCategory(selected.getCategory());
        draft.setMerchantOrVendor(selected.getMerchantOrVendor());
        draft.setPaymentMethod(selected.getPaymentMethod());
        draft.setNotes(selected.getNotes());
        ExpenseFormDialog.show(draft).ifPresent(expense -> {
            expenseDao.insert(expense);
            refresh();
        });
    }

    private void onEdit() {
        onEdit(table.getSelectionModel().getSelectedItem());
    }

    private void onEdit(Expense selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select an expense to edit.");
            return;
        }
        ExpenseFormDialog.show(selected).ifPresent(expense -> {
            expenseDao.update(expense);
            refresh();
        });
    }

    private void onDelete() {
        onDelete(table.getSelectionModel().getSelectedItem());
    }

    private void onDelete(Expense selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select an expense to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Move this " + selected.getCategory() + " expense (" + selected.getTransactionDate() + ") to Trash?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Expense");
        confirm.setHeaderText(null);
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        expenseDao.softDelete(selected.getId());
        refresh();
    }

    private void onImportExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select an .xlsx file of expenses to import");
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
            ColumnMappingDialog.show(headers).ifPresent(mapping -> {
                try {
                    var imported = excelImportService.importExpenses(selected, mapping);
                    refresh();
                    AlertUtil.info("Import complete", imported.size() + " expenses imported from " + selected.getName());
                } catch (IOException ex) {
                    AlertUtil.error("Import failed", ex.getMessage());
                }
            });
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private void onImportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a .csv file of expenses to import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected == null) return;

        try {
            List<String> headers = csvService.readHeaders(selected);
            if (headers.isEmpty()) {
                AlertUtil.error("Import failed", "No header row found in " + selected.getName());
                return;
            }
            ColumnMappingDialog.show(headers).ifPresent(mapping -> {
                try {
                    var imported = csvService.importExpenses(selected, mapping);
                    refresh();
                    AlertUtil.info("Import complete", imported.size() + " expenses imported from " + selected.getName());
                } catch (IOException ex) {
                    AlertUtil.error("Import failed", ex.getMessage());
                }
            });
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private void onExportExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export expenses to Excel");
        chooser.setInitialFileName("expenses.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File destination = chooser.showSaveDialog(window);
        if (destination == null) return;

        try {
            excelExportService.exportExpenses(filteredData, destination);
            AlertUtil.info("Export complete", filteredData.size() + " expenses exported to " + destination.getName());
        } catch (IOException ex) {
            AlertUtil.error("Export failed", ex.getMessage());
        }
    }

    private void onExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export expenses to CSV");
        chooser.setInitialFileName("expenses.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File destination = chooser.showSaveDialog(window);
        if (destination == null) return;

        try {
            csvService.exportExpenses(filteredData, destination);
            AlertUtil.info("Export complete", filteredData.size() + " expenses exported to " + destination.getName());
        } catch (IOException ex) {
            AlertUtil.error("Export failed", ex.getMessage());
        }
    }
}
