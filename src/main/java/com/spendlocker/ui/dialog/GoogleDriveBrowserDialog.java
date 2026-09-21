package com.spendlocker.ui.dialog;

import com.google.api.services.drive.model.File;
import com.spendlocker.dao.DocumentDao;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.google.GoogleDriveService;
import com.spendlocker.model.Document;
import com.spendlocker.model.FileType;
import com.spendlocker.model.UploadSource;
import com.spendlocker.pdf.PdfService;
import com.spendlocker.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Search, download, or import (into the local vault) files stored in the user's Google Drive. */
public class GoogleDriveBrowserDialog {

    private static final String ALL_CATEGORIES = "All Categories";
    private static final String UNCATEGORIZED = "Uncategorized";

    private final GoogleDriveService driveService = GoogleDriveService.getInstance();
    private final DocumentDao documentDao = new DocumentDao();
    private final PdfService pdfService = new PdfService();

    private final TableView<File> table = new TableView<>();
    private final ObservableList<File> data = FXCollections.observableArrayList();
    private final FlowPane gridPane = new FlowPane(14, 14);
    private final ScrollPane gridScroll = new ScrollPane(gridPane);
    private final VBox categoryMenu = new VBox(4);
    private final ToggleGroup categoryGroup = new ToggleGroup();

    private List<File> allFiles = new ArrayList<>();
    private Map<String, Document> localByDriveId = new HashMap<>();
    private String selectedCategory = ALL_CATEGORIES;
    private BorderPane root;
    private File selectedFile;
    private Runnable onImported;

    public static void show(Window owner, Runnable onImported) {
        new GoogleDriveBrowserDialog().display(owner, onImported);
    }

    private void display(Window owner, Runnable onImported) {
        this.onImported = onImported;
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Google Drive — Browse & Import");
        com.spendlocker.util.DialogUtil.center(stage);

        Label title = new Label("Google Drive", new FontIcon(FontAwesomeBrands.GOOGLE_DRIVE));
        title.getStyleClass().add("title-1");

        TextField searchField = new TextField();
        searchField.setPromptText("Search files by name...");
        searchField.setPrefWidth(220);
        Button searchButton = new Button("Search", new FontIcon(Feather.SEARCH));
        searchButton.setOnAction(e -> runSearch(searchField.getText()));
        searchField.setOnAction(e -> runSearch(searchField.getText()));
        Button refreshButton = new Button("All Files", new FontIcon(Feather.LIST));
        refreshButton.setOnAction(e -> loadAll());

        ToggleGroup viewGroup = new ToggleGroup();
        ToggleButton listViewBtn = new ToggleButton("", new FontIcon(Feather.LIST));
        listViewBtn.setTooltip(new Tooltip("List view"));
        listViewBtn.setToggleGroup(viewGroup);
        listViewBtn.setSelected(true);
        ToggleButton gridViewBtn = new ToggleButton("", new FontIcon(Feather.GRID));
        gridViewBtn.setTooltip(new Tooltip("Box view"));
        gridViewBtn.setToggleGroup(viewGroup);
        listViewBtn.setOnAction(e -> switchView(false));
        gridViewBtn.setOnAction(e -> switchView(true));
        HBox viewToggle = new HBox(0, listViewBtn, gridViewBtn);

        HBox searchRow = new HBox(8, searchField, searchButton, refreshButton,
                new Separator(javafx.geometry.Orientation.VERTICAL), viewToggle);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        buildColumns();
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> selectedFile = sel);

        gridPane.setPadding(new Insets(12));
        gridScroll.setFitToWidth(true);
        gridScroll.getStyleClass().add("edge-to-edge");

        Label categoryHeading = new Label("Categories");
        categoryHeading.getStyleClass().add("text-caption");
        categoryMenu.getChildren().add(categoryHeading);
        categoryMenu.setPadding(new Insets(4, 12, 0, 0));
        categoryMenu.setPrefWidth(170);

        Button importButton = new Button("Import into Vault", new FontIcon(Feather.DOWNLOAD));
        importButton.getStyleClass().add("accent");
        importButton.setOnAction(e -> onImport(stage));
        Button downloadButton = new Button("Download to...", new FontIcon(Feather.SAVE));
        downloadButton.setOnAction(e -> onDownloadTo(stage));
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());

        HBox actions = new HBox(10, importButton, downloadButton, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 0, 0, 0));

        VBox header = new VBox(12, title, searchRow);
        header.setPadding(new Insets(20, 20, 12, 20));

        root = new BorderPane();
        root.setTop(header);
        root.setLeft(categoryMenu);
        root.setCenter(table);
        BorderPane.setMargin(categoryMenu, new Insets(0, 0, 0, 20));
        BorderPane.setMargin(table, new Insets(0, 20, 0, 20));
        BorderPane.setMargin(gridScroll, new Insets(0, 20, 0, 20));
        root.setBottom(actions);
        BorderPane.setMargin(actions, new Insets(0, 20, 20, 20));

        stage.setScene(new javafx.scene.Scene(root, 760, 540));
        loadAll();
        stage.showAndWait();
    }

    private void switchView(boolean grid) {
        root.setCenter(grid ? gridScroll : table);
    }

    private void buildColumns() {
        TableColumn<File, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(name);
                setGraphic(coloredIcon(FileType.fromExtension(name)));
            }
        });

        TableColumn<File, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(categoryFor(c.getValue())));

        TableColumn<File, String> modifiedCol = new TableColumn<>("Modified");
        modifiedCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getModifiedTime() != null ? c.getValue().getModifiedTime().toString() : ""));

        TableColumn<File, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getSize() != null ? humanSize(c.getValue().getSize()) : ""));

        table.getColumns().addAll(nameCol, categoryCol, modifiedCol, sizeCol);
    }

    private void loadAll() {
        runInBackground(driveService::listVaultFiles);
    }

    private void runSearch(String query) {
        if (query == null || query.isBlank()) {
            loadAll();
            return;
        }
        runInBackground(() -> driveService.searchFilesByName(query));
    }

    private interface DriveQuery {
        List<File> run() throws IOException;
    }

    private void runInBackground(DriveQuery query) {
        table.setDisable(true);
        gridPane.setDisable(true);
        com.spendlocker.ui.SessionGuard.suspendAutoLock();
        new Thread(() -> {
            try {
                ensureSignedIn();
                List<File> files = query.run();
                javafx.application.Platform.runLater(() -> {
                    com.spendlocker.ui.SessionGuard.resumeAutoLock();
                    allFiles = files;
                    refreshLocalIndex();
                    rebuildCategoryMenu();
                    applyFilter();
                    table.setDisable(false);
                    gridPane.setDisable(false);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    com.spendlocker.ui.SessionGuard.resumeAutoLock();
                    table.setDisable(false);
                    gridPane.setDisable(false);
                    AlertUtil.error("Google Drive", ex.getMessage());
                });
            }
        }, "drive-browser").start();
    }

    /** Cross-references Drive files against locally tracked documents to resolve each file's category. */
    private void refreshLocalIndex() {
        localByDriveId = documentDao.findAll().stream()
                .filter(d -> d.getDriveFileId() != null && !d.getDriveFileId().isBlank())
                .collect(Collectors.toMap(Document::getDriveFileId, d -> d, (a, b) -> a));
    }

    /** Rebuilds the left-side category menu from whatever categories are present in the current file set. */
    private void rebuildCategoryMenu() {
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        localByDriveId.values().stream()
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

    private String categoryFor(File file) {
        Document doc = localByDriveId.get(file.getId());
        if (doc != null && doc.getCategory() != null && !doc.getCategory().isBlank()) {
            return doc.getCategory();
        }
        return UNCATEGORIZED;
    }

    private void applyFilter() {
        List<File> filtered = selectedCategory.equals(ALL_CATEGORIES)
                ? allFiles
                : allFiles.stream().filter(f -> categoryFor(f).equals(selectedCategory)).collect(Collectors.toList());
        data.setAll(filtered);
        rebuildGrid(filtered);
    }

    private void rebuildGrid(List<File> files) {
        gridPane.getChildren().clear();
        for (File file : files) {
            gridPane.getChildren().add(buildTile(file));
        }
    }

    private javafx.scene.Node buildTile(File file) {
        FontIcon icon = coloredIcon(FileType.fromExtension(file.getName()));
        icon.setIconSize(36);

        Label nameLabel = new Label(file.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(120);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setStyle("-fx-text-alignment: center;");

        Label categoryLabel = new Label(categoryFor(file));
        categoryLabel.getStyleClass().add("text-muted");

        VBox tile = new VBox(6, icon, nameLabel, categoryLabel);
        tile.setAlignment(Pos.CENTER);
        tile.setPadding(new Insets(14));
        tile.setPrefWidth(140);
        tile.getStyleClass().add("card");
        tile.setOnMouseClicked(e -> {
            selectedFile = file;
            table.getSelectionModel().select(file);
            gridPane.getChildren().forEach(node -> node.getStyleClass().remove("selected"));
            tile.getStyleClass().add("selected");
        });
        return tile;
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

    private void ensureSignedIn() throws IOException, java.security.GeneralSecurityException {
        if (!driveService.isSignedIn()) {
            driveService.signIn();
        }
    }

    private void onImport(Stage stage) {
        File selected = selectedFile;
        if (selected == null) {
            AlertUtil.info("No selection", "Select a file to import.");
            return;
        }
        try {
            Path vaultDocsDir = Path.of(DatabaseManager.vaultDirectory(), "documents");
            Files.createDirectories(vaultDocsDir);
            Path target = vaultDocsDir.resolve(System.currentTimeMillis() + "_" + selected.getName());
            driveService.downloadFile(selected.getId(), target.toFile());

            Document doc = new Document();
            doc.setFileName(selected.getName());
            doc.setFilePath(target.toString());
            FileType fileType = FileType.fromExtension(selected.getName());
            doc.setFileType(fileType);
            doc.setFileSizeBytes(Files.size(target));
            doc.setUploadSource(UploadSource.GOOGLE_DRIVE);
            doc.setDriveFileId(selected.getId());
            Document existing = localByDriveId.get(selected.getId());
            if (existing != null) {
                doc.setCategory(existing.getCategory());
            }
            if (fileType == FileType.PDF) {
                try {
                    doc.setExtractedText(pdfService.extractText(target.toFile()));
                } catch (IOException ignored) {
                    // best-effort extraction
                }
            }
            documentDao.insert(doc);
            AlertUtil.info("Imported", selected.getName() + " added to your document vault.");
            if (onImported != null) onImported.run();
            stage.close();
        } catch (IOException ex) {
            AlertUtil.error("Import failed", ex.getMessage());
        }
    }

    private void onDownloadTo(Stage stage) {
        File selected = selectedFile;
        if (selected == null) {
            AlertUtil.info("No selection", "Select a file to download.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(selected.getName());
        java.io.File destination = chooser.showSaveDialog(stage);
        if (destination == null) return;

        try {
            driveService.downloadFile(selected.getId(), destination);
            AlertUtil.info("Downloaded", selected.getName() + " saved to " + destination.getParent());
        } catch (IOException ex) {
            AlertUtil.error("Download failed", ex.getMessage());
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
