package com.spendlocker.ui;

import com.spendlocker.dao.FixedDepositDao;
import com.spendlocker.model.FixedDeposit;
import com.spendlocker.ui.dialog.DepositFormDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import com.spendlocker.util.FixedDepositCalculator;
import com.spendlocker.util.MoneyFormat;
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
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Consumer;

/** The reference design's "Deposits" tab: search + Active/Matured/All + a sortable table. */
public class DepositsView extends BorderPane {

    private static final String ACTIVE = "Active";
    private static final String MATURED = "Matured";
    private static final String ALL = "All";

    private final FixedDepositDao fixedDepositDao = new FixedDepositDao();
    private final TableView<FixedDeposit> table = new TableView<>();
    private final ObservableList<FixedDeposit> data = FXCollections.observableArrayList();
    private final FilteredList<FixedDeposit> filteredData = new FilteredList<>(data, fd -> true);
    private final TextField searchField = new TextField();
    private String statusFilter = ACTIVE;

    public DepositsView() {
        setPadding(new Insets(24));

        Label title = new Label("Deposits", new FontIcon(Feather.CREDIT_CARD));
        title.getStyleClass().add("title-1");

        Button addButton = new Button("Add to book", new FontIcon(Feather.PLUS));
        addButton.getStyleClass().add("accent");
        addButton.setOnAction(e -> onAdd());
        Button editButton = new Button("Edit", new FontIcon(Feather.EDIT_2));
        editButton.setOnAction(e -> onEdit());
        Button deleteButton = new Button("Delete", new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().add("danger");
        deleteButton.setOnAction(e -> onDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, title, spacer, addButton, editButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("page-header");
        toolbar.setPadding(new Insets(0, 0, 12, 0));

        searchField.setPromptText("Search depositor or bank");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());

        HBox segGroup = buildStatusSegment();

        HBox filterBar = new HBox(10, searchField, segGroup);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(0, 0, 16, 0));

        VBox header = new VBox(toolbar, filterBar);

        buildColumns();
        table.setItems(filteredData);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(emptyState());

        setTop(header);
        setCenter(table);
        refresh();
    }

    private HBox buildStatusSegment() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton activeBtn = segButton(ACTIVE, group, true);
        ToggleButton maturedBtn = segButton(MATURED, group, false);
        ToggleButton allBtn = segButton(ALL, group, false);
        activeBtn.setOnAction(e -> { statusFilter = ACTIVE; applyFilter(); });
        maturedBtn.setOnAction(e -> { statusFilter = MATURED; applyFilter(); });
        allBtn.setOnAction(e -> { statusFilter = ALL; applyFilter(); });

        HBox seg = new HBox(activeBtn, maturedBtn, allBtn);
        seg.getStyleClass().add("seg-group");
        return seg;
    }

    private ToggleButton segButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        return button;
    }

    private VBox emptyState() {
        FontIcon icon = new FontIcon(Feather.CREDIT_CARD);
        icon.setIconSize(32);
        icon.getStyleClass().add("text-caption");
        Label message = new Label("No deposits here yet — click Add to book to get started.");
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
        data.setAll(fixedDepositDao.findAll());
        applyFilter();
    }

    /** Drill-down from the Dashboard/Banks screen: prefill the search and widen the filter. */
    public void filterByBank(String bank) {
        searchField.setText(bank);
        statusFilter = ALL;
        applyFilter();
    }

    /** Drill-down from the People screen — the same search box matches depositor or bank. */
    public void filterByDepositor(String depositor) {
        filterByBank(depositor);
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredData.setPredicate(fd -> matchesStatus(fd) && matchesSearch(fd, query));
    }

    private boolean matchesStatus(FixedDeposit fd) {
        boolean active = FixedDepositCalculator.isActive(fd.getMaturityDate());
        return switch (statusFilter) {
            case ACTIVE -> active;
            case MATURED -> !active;
            default -> true;
        };
    }

    private boolean matchesSearch(FixedDeposit fd, String query) {
        if (query.isEmpty()) return true;
        return containsIgnoreCase(fd.getDepositor(), query) || containsIgnoreCase(fd.getBank(), query);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void buildColumns() {
        TableColumn<FixedDeposit, String> depositorCol = new TableColumn<>("Depositor");
        TableColumnUtil.fitHeader(depositorCol, 110);
        depositorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepositor()));

        TableColumn<FixedDeposit, FixedDeposit> bankCol = new TableColumn<>("Bank");
        TableColumnUtil.fitHeader(bankCol, 160);
        bankCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        bankCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FixedDeposit fd, boolean empty) {
                super.updateItem(fd, empty);
                if (empty || fd == null) {
                    setGraphic(null);
                    return;
                }
                Label bank = new Label(fd.getBank());
                VBox box = new VBox(bank);
                if (fd.getFdNumber() != null && !fd.getFdNumber().isBlank()) {
                    Label fdNumber = new Label(fd.getFdNumber());
                    fdNumber.getStyleClass().add("text-caption");
                    box.getChildren().add(fdNumber);
                }
                setGraphic(box);
            }
        });

        TableColumn<FixedDeposit, FixedDeposit> rateCol = new TableColumn<>("Rate");
        TableColumnUtil.fitHeader(rateCol, 90);
        rateCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        rateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FixedDeposit fd, boolean empty) {
                super.updateItem(fd, empty);
                if (empty || fd == null) {
                    setGraphic(null);
                    return;
                }
                if (fd.getRatePercent() <= 0) {
                    Label tag = new Label("add rate");
                    tag.getStyleClass().addAll("tag", "warning");
                    setGraphic(tag);
                } else {
                    Label rate = new Label(String.format("%.2f%%", fd.getRatePercent()));
                    setGraphic(rate);
                }
            }
        });

        TableColumn<FixedDeposit, Number> principalCol = new TableColumn<>("Principal");
        TableColumnUtil.fitHeader(principalCol, 100);
        principalCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrincipal()));
        principalCol.setCellFactory(col -> currencyCell());

        TableColumn<FixedDeposit, Number> interestCol = new TableColumn<>("Interest");
        TableColumnUtil.fitHeader(interestCol, 100);
        interestCol.setCellValueFactory(c -> new SimpleObjectProperty<>(FixedDepositCalculator.interest(c.getValue())));
        interestCol.setCellFactory(col -> currencyCell());

        TableColumn<FixedDeposit, Number> maturityCol = new TableColumn<>("Maturity");
        TableColumnUtil.fitHeader(maturityCol, 100);
        maturityCol.setCellValueFactory(c -> new SimpleObjectProperty<>(FixedDepositCalculator.maturityAmount(c.getValue())));
        maturityCol.setCellFactory(col -> currencyCell());

        TableColumn<FixedDeposit, FixedDeposit> maturesCol = new TableColumn<>("Matures");
        TableColumnUtil.fitHeader(maturesCol, 150);
        maturesCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        maturesCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FixedDeposit fd, boolean empty) {
                super.updateItem(fd, empty);
                if (empty || fd == null) {
                    setGraphic(null);
                    return;
                }
                Label date = new Label(fd.getMaturityDate());
                Long daysLeft = FixedDepositCalculator.daysLeft(fd.getMaturityDate());
                HBox box = new HBox(6, date);
                box.setAlignment(Pos.CENTER_LEFT);
                if (daysLeft != null) {
                    Label tag = new Label();
                    if (daysLeft < 0) {
                        tag.setText("matured");
                        tag.getStyleClass().addAll("tag", "neutral");
                    } else if (daysLeft <= 30) {
                        tag.setText(daysLeft + "d");
                        tag.getStyleClass().addAll("tag", "negative");
                    } else if (daysLeft <= 90) {
                        tag.setText(daysLeft + "d");
                        tag.getStyleClass().addAll("tag", "warning");
                    }
                    if (!tag.getText().isEmpty()) box.getChildren().add(tag);
                }
                setGraphic(box);
            }
        });

        TableColumn<FixedDeposit, Void> actionsCol = new TableColumn<>("Actions");
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

        table.getColumns().addAll(depositorCol, bankCol, rateCol, principalCol, interestCol, maturityCol, maturesCol, actionsCol);
        TableColumnUtil.bindProportionalWidths(table, actionsCol.getPrefWidth() + 20,
                depositorCol, bankCol, rateCol, principalCol, interestCol, maturityCol, maturesCol);
    }

    private TableCell<FixedDeposit, Number> currencyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : MoneyFormat.currency(value.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        };
    }

    private void onAdd() {
        DepositFormDialog.show(null).ifPresent(fd -> {
            fixedDepositDao.insert(fd);
            refresh();
        });
    }

    private void onEdit() {
        onEdit(table.getSelectionModel().getSelectedItem());
    }

    private void onEdit(FixedDeposit selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select a deposit to edit.");
            return;
        }
        DepositFormDialog.show(selected).ifPresent(fd -> {
            fixedDepositDao.update(fd);
            refresh();
        });
    }

    private void onDelete() {
        onDelete(table.getSelectionModel().getSelectedItem());
    }

    private void onDelete(FixedDeposit selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select a deposit to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Move " + selected.getDepositor() + "'s deposit at " + selected.getBank() + " to Trash?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Deposit");
        confirm.setHeaderText(null);
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        fixedDepositDao.softDelete(selected.getId());
        refresh();
    }
}
