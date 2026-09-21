package com.spendlocker.ui;

import com.spendlocker.dao.DocumentDao;
import com.spendlocker.dao.ExpenseDao;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.excel.ColumnMapping;
import com.spendlocker.excel.ExcelImportService;
import com.spendlocker.google.GoogleDriveService;
import com.spendlocker.model.Document;
import com.spendlocker.model.Expense;
import com.spendlocker.model.FileType;
import com.spendlocker.model.UploadSource;
import com.spendlocker.pdf.ParsedReceipt;
import com.spendlocker.pdf.PdfService;
import com.spendlocker.pdf.ReceiptParser;
import com.spendlocker.ui.dialog.ColumnMappingDialog;
import com.spendlocker.ui.dialog.DocumentCategoryDialog;
import com.spendlocker.ui.dialog.ExpenseFormDialog;
import com.spendlocker.ui.dialog.GoogleDriveBrowserDialog;
import com.spendlocker.util.AlertUtil;
import com.spendlocker.util.DialogUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentsView extends BorderPane {

    private static final String ALL_CATEGORIES = "All Documents";
    private static final String UNCATEGORIZED = "Uncategorized";

    private final DocumentDao documentDao = new DocumentDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final PdfService pdfService = new PdfService();
    private final ReceiptParser receiptParser = new ReceiptParser();
    private final ExcelImportService excelImportService = new ExcelImportService();
    private final GoogleDriveService driveService = GoogleDriveService.getInstance();

    private final TableView<Document> table = new TableView<>();
    private final ObservableList<Document> data = FXCollections.observableArrayList();
    private final VBox categoryMenu = new VBox(4);
    private final ToggleGroup categoryGroup = new ToggleGroup();
    private List<Document> allDocuments = new ArrayList<>();
    private String selectedCategory = ALL_CATEGORIES;

    public DocumentsView() {
        setPadding(new Insets(24));

        Label title = new Label("Document Vault", new FontIcon(Feather.FOLDER));
        title.getStyleClass().add("title-1");

        Button importButton = new Button("Import Document", new FontIcon(Feather.UPLOAD));
        importButton.getStyleClass().add("accent");
        importButton.setOnAction(e -> onImportDocument());
        Button importExcelButton = new Button("Import Expenses (.xlsx)", new FontIcon(Feather.FILE_TEXT));
        importExcelButton.setOnAction(e -> onImportExcel());
        Button openButton = new Button("Open", new FontIcon(Feather.EXTERNAL_LINK));
        openButton.setOnAction(e -> onOpen());
        Button uploadToDriveButton = new Button("Upload to Drive", new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
        uploadToDriveButton.setOnAction(e -> onUploadToDrive());
        Button browseDriveButton = new Button("Browse Drive", new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
        browseDriveButton.setOnAction(e -> onBrowseDrive());
        Button deleteButton = new Button("Delete", new FontIcon(Feather.TRASH_2));
        deleteButton.getStyleClass().add("danger");
        deleteButton.setOnAction(e -> onDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, title, spacer, browseDriveButton, uploadToDriveButton,
                openButton, importButton, importExcelButton, deleteButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 16, 0));

        buildColumns();
        table.setItems(data);
        // Unconstrained (not flex-last-column): fixed per-column widths so the file name can't
        // squeeze the Actions column down to nothing — a horizontal scrollbar appears instead.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(emptyState());

        Label categoryHeading = new Label("Categories");
        categoryHeading.getStyleClass().add("text-caption");
        categoryMenu.getChildren().add(categoryHeading);
        categoryMenu.setPadding(new Insets(4, 16, 0, 0));
        categoryMenu.setPrefWidth(170);

        setTop(toolbar);
        setLeft(categoryMenu);
        setCenter(table);
        BorderPane.setMargin(table, new Insets(0, 0, 0, 16));
        refresh();
    }

    private VBox emptyState() {
        FontIcon icon = new FontIcon(Feather.FOLDER);
        icon.setIconSize(32);
        icon.getStyleClass().add("text-caption");
        Label message = new Label("No documents yet — click Import Document or Browse Drive to get started.");
        message.getStyleClass().add("text-caption");
        VBox box = new VBox(10, icon, message);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    public void refresh() {
        allDocuments = documentDao.findAll();
        rebuildCategoryMenu();
        applyFilter();
    }

    /** Rebuilds the left-side category menu from whatever categories are present in the vault. */
    private void rebuildCategoryMenu() {
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        allDocuments.stream()
                .map(Document::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(categories::add);
        categories.add(UNCATEGORIZED);

        if (!categories.contains(selectedCategory)) {
            selectedCategory = ALL_CATEGORIES;
        }

        categoryMenu.getChildren().removeIf(node -> node instanceof ToggleButton);
        for (String category : categories) {
            ToggleButton btn = new ToggleButton(category);
            btn.setToggleGroup(categoryGroup);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.getStyleClass().add("nav-button");
            btn.setSelected(category.equals(selectedCategory));
            btn.setOnAction(e -> {
                selectedCategory = category;
                applyFilter();
            });
            categoryMenu.getChildren().add(btn);
        }
    }

    private void applyFilter() {
        List<Document> filtered = allDocuments.stream()
                .filter(this::matchesSelectedCategory)
                .collect(Collectors.toList());
        data.setAll(filtered);
    }

    private boolean matchesSelectedCategory(Document doc) {
        if (selectedCategory.equals(ALL_CATEGORIES)) return true;
        boolean uncategorized = doc.getCategory() == null || doc.getCategory().isBlank();
        if (selectedCategory.equals(UNCATEGORIZED)) return uncategorized;
        return !uncategorized && selectedCategory.equals(doc.getCategory());
    }

    private void buildColumns() {
        TableColumn<Document, String> nameCol = new TableColumn<>("File Name");
        nameCol.setPrefWidth(220);
        nameCol.setMinWidth(160);
        nameCol.setMaxWidth(320);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFileName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Document doc = getTableRow().getItem();
                setText(name);
                setGraphic(doc != null ? coloredIcon(doc.getFileType()) : null);
            }
        });

        TableColumn<Document, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(70);
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFileType().toString()));

        TableColumn<Document, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setPrefWidth(80);
        sizeCol.setCellValueFactory(c -> new SimpleStringProperty(humanSize(c.getValue().getFileSizeBytes())));

        TableColumn<Document, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setPrefWidth(100);
        sourceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUploadSource().toString()));

        TableColumn<Document, String> driveCol = new TableColumn<>("Drive");
        driveCol.setPrefWidth(80);
        driveCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDriveFileId() != null && !c.getValue().getDriveFileId().isBlank() ? "Synced" : "—"));

        TableColumn<Document, String> dateCol = new TableColumn<>("Uploaded");
        dateCol.setPrefWidth(110);
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUploadDate()));

        TableColumn<Document, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setPrefWidth(110);
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));

        TableColumn<Document, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setPrefWidth(120);
        tagsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTags()));

        TableColumn<Document, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setSortable(false);
        actionsCol.setResizable(false);
        actionsCol.setPrefWidth(112);
        actionsCol.setMinWidth(112);
        actionsCol.setMaxWidth(112);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button openBtn = new Button(null, new FontIcon(Feather.EXTERNAL_LINK));
            private final Button uploadBtn = new Button(null, new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
            private final Button deleteBtn = new Button(null, new FontIcon(Feather.TRASH_2));
            private final HBox box = new HBox(6, openBtn, uploadBtn, deleteBtn);
            {
                openBtn.getStyleClass().add("icon-button");
                uploadBtn.getStyleClass().add("icon-button");
                deleteBtn.getStyleClass().add("icon-button");
                box.setAlignment(Pos.CENTER);
                openBtn.setTooltip(new Tooltip("Open"));
                uploadBtn.setTooltip(new Tooltip("Upload to Drive"));
                deleteBtn.setTooltip(new Tooltip("Delete"));
                openBtn.setOnAction(e -> open(getTableRow().getItem()));
                uploadBtn.setOnAction(e -> uploadToDrive(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> onDelete(getTableRow().getItem()));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Document doc = getTableRow().getItem();
                boolean synced = doc.getDriveFileId() != null && !doc.getDriveFileId().isBlank();
                uploadBtn.setDisable(synced);
                uploadBtn.setGraphic(new FontIcon(synced ? Feather.CHECK_CIRCLE : FontAwesomeBrands.GOOGLE_DRIVE));
                setGraphic(box);
            }
        });

        table.getColumns().addAll(nameCol, categoryCol, typeCol, sizeCol, sourceCol, driveCol, dateCol, tagsCol, actionsCol);
    }

    /** A distinct, format-branded icon per file type (PDF red, Word blue, Excel green, images purple). */
    private FontIcon coloredIcon(FileType type) {
        FontIcon icon = new FontIcon(iconFor(type));
        icon.setIconColor(colorFor(type));
        return icon;
    }

    private Ikon iconFor(FileType type) {
        return switch (type) {
            case PDF -> FontAwesomeSolid.FILE_PDF;
            case DOCX -> FontAwesomeSolid.FILE_WORD;
            case XLSX -> FontAwesomeSolid.FILE_EXCEL;
            case PNG, JPG -> FontAwesomeSolid.FILE_IMAGE;
            default -> FontAwesomeSolid.FILE_ALT;
        };
    }

    private javafx.scene.paint.Color colorFor(FileType type) {
        return switch (type) {
            case PDF -> javafx.scene.paint.Color.web("#E53935");
            case DOCX -> javafx.scene.paint.Color.web("#2B579A");
            case XLSX -> javafx.scene.paint.Color.web("#1D6F42");
            case PNG, JPG -> javafx.scene.paint.Color.web("#8E44AD");
            default -> javafx.scene.paint.Color.web("#757575");
        };
    }

    private void onImportDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a document to import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Documents", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.xlsx", "*.docx"));
        Window window = getScene() != null ? getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected == null) return;

        try {
            Path vaultDocsDir = Path.of(DatabaseManager.vaultDirectory(), "documents");
            Files.createDirectories(vaultDocsDir);
            Path target = vaultDocsDir.resolve(System.currentTimeMillis() + "_" + selected.getName());
            Files.copy(selected.toPath(), target);

            Document doc = new Document();
            doc.setFileName(selected.getName());
            doc.setFilePath(target.toString());
            FileType fileType = FileType.fromExtension(selected.getName());
            doc.setFileType(fileType);
            doc.setFileSizeBytes(Files.size(target));
            doc.setUploadSource(UploadSource.MANUAL);
            doc.setCategory(DocumentCategoryDialog.show(selected.getName()).orElse(null));

            String extractedText = null;
            if (fileType == FileType.PDF) {
                try {
                    extractedText = pdfService.extractText(target.toFile());
                    doc.setExtractedText(extractedText);
                } catch (IOException ex) {
                    // extraction is best-effort; keep the document importable without it
                }
            }

            documentDao.insert(doc);
            refresh();

            if (fileType == FileType.PDF && extractedText != null) {
                offerExpenseFromReceipt(doc, extractedText);
            }
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private void offerExpenseFromReceipt(Document doc, String extractedText) {
        ParsedReceipt parsed = receiptParser.parse(extractedText);
        if (!parsed.hasAnySignal()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This looks like a receipt or invoice. Create an expense entry from it?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Smart Document Detection");
        confirm.setHeaderText("Detected possible expense data in " + doc.getFileName());
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        Expense draft = new Expense();
        if (parsed.date() != null) draft.setTransactionDate(parsed.date().toString());
        if (parsed.amount() != null) draft.setAmount(parsed.amount());
        draft.setMerchantOrVendor(parsed.vendor());
        draft.setDocumentId(doc.getId());
        draft.setNotes("Auto-detected from " + doc.getFileName());

        ExpenseFormDialog.show(draft).ifPresent(expense -> {
            expenseDao.insert(expense);
            AlertUtil.info("Expense created", "Linked to " + doc.getFileName());
        });
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
                    AlertUtil.info("Import complete", imported.size() + " expenses imported from " + selected.getName());
                } catch (IOException ex) {
                    AlertUtil.error("Import failed", ex.getMessage());
                }
            });
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private void onOpen() {
        open(table.getSelectionModel().getSelectedItem());
    }

    private void open(Document selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select a document to open.");
            return;
        }
        File localFile = new File(selected.getFilePath());
        if (!localFile.exists()) {
            AlertUtil.error("Open failed", "Local file not found: " + selected.getFilePath());
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(localFile);
            } else {
                AlertUtil.error("Open failed", "Opening files isn't supported on this system.");
            }
        } catch (IOException ex) {
            AlertUtil.error("Open failed", ex.getMessage());
        }
    }

    private void onUploadToDrive() {
        uploadToDrive(table.getSelectionModel().getSelectedItem());
    }

    private void uploadToDrive(Document selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select a document to upload.");
            return;
        }
        if (selected.getDriveFileId() != null && !selected.getDriveFileId().isBlank()) {
            AlertUtil.info("Already uploaded", selected.getFileName() + " is already synced to Google Drive.");
            return;
        }
        File localFile = new File(selected.getFilePath());
        if (!localFile.exists()) {
            AlertUtil.error("Upload failed", "Local file not found: " + selected.getFilePath());
            return;
        }
        SessionGuard.suspendAutoLock();
        new Thread(() -> {
            try {
                if (!driveService.isSignedIn()) {
                    driveService.signIn();
                }
                var uploaded = driveService.uploadFile(localFile, mimeTypeFor(selected.getFileType()));
                javafx.application.Platform.runLater(() -> {
                    // Write on the FX thread, not this background upload thread — the DB
                    // connection isn't safe for concurrent cross-thread access.
                    documentDao.updateDriveFileId(selected.getId(), uploaded.getId());
                    SessionGuard.resumeAutoLock();
                    refresh();
                    AlertUtil.info("Uploaded", selected.getFileName() + " uploaded to Google Drive.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    SessionGuard.resumeAutoLock();
                    AlertUtil.error("Upload failed", ex.getMessage());
                });
            }
        }, "drive-upload").start();
    }

    private void onBrowseDrive() {
        GoogleDriveBrowserDialog.show(getScene() != null ? getScene().getWindow() : null, this::refresh);
    }

    private String mimeTypeFor(FileType type) {
        return switch (type) {
            case PDF -> "application/pdf";
            case PNG -> "image/png";
            case JPG -> "image/jpeg";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private void onDelete() {
        onDelete(table.getSelectionModel().getSelectedItem());
    }

    private void onDelete(Document selected) {
        if (selected == null) {
            AlertUtil.info("No selection", "Select a document to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Move \"" + selected.getFileName() + "\" to Trash?"
                        + (selected.getDriveFileId() != null && !selected.getDriveFileId().isBlank()
                                ? "\n(Its Google Drive copy stays untouched unless you delete it permanently from Trash.)" : ""),
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Document");
        confirm.setHeaderText(null);
        DialogUtil.center(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        documentDao.softDelete(selected.getId());
        refresh();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
