package com.example;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.awt.Desktop;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Controller {

    @FXML private TextField pathTextField;
    @FXML private TextField searchTextField;
    @FXML private Button browseButton;
    @FXML private Button scanButton;
    @FXML private Button cancelButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    @FXML private TreeView<String> directoryTreeView;
    @FXML private TableView<FileInfo> fileTableView;
    @FXML private TableColumn<FileInfo, String> colName;
    @FXML private TableColumn<FileInfo, String> colSize;
    @FXML private TableColumn<FileInfo, String> colExtension;
    @FXML private TableColumn<FileInfo, String> colPath;

    @FXML private Label totalFilesLabel;
    @FXML private Label totalSizeLabel;

    private ScanTask currentScanTask;

    private final ObservableList<FileInfo> masterFileList = FXCollections.observableArrayList();
    private FilteredList<FileInfo> filteredFileList;

    int coreCount = Runtime.getRuntime().availableProcessors();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(coreCount);

    @FXML
    public void initialize() {
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colSize.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        colExtension.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getExtension()));
        colPath.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPath()));

        filteredFileList = new FilteredList<>(masterFileList, p -> true);

        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredFileList.setPredicate(file -> {
                if (newValue == null || newValue.trim().isEmpty()) {
                    return true;
                }
                String filterPattern = newValue.toLowerCase().trim();
                return file.getName().toLowerCase().contains(filterPattern);
            });
        });

        SortedList<FileInfo> sortedData = new SortedList<>(filteredFileList);
        sortedData.comparatorProperty().bind(fileTableView.comparatorProperty());

        fileTableView.setItems(sortedData);

        fileTableView.setRowFactory(tv -> {
            TableRow<FileInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    FileInfo selectedFile = row.getItem();
                    openFileInSystem(selectedFile.getPath());
                }
            });
            return row;
        });
    }

    @FXML
    private void handleScan() {
        String path = pathTextField.getText();

        if (path == null || path.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Path", "Please select or enter a directory path.");
            return;
        }

        File targetDir = new File(path.trim());
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "Invalid Directory", "The specified path does not exist or is not a directory.");
            return;
        }

        setControlsScanningState(true);

        currentScanTask = new ScanTask(targetDir);
        progressBar.progressProperty().bind(currentScanTask.progressProperty());
        statusLabel.textProperty().bind(currentScanTask.messageProperty());

        currentScanTask.setOnSucceeded(event -> {
            ScanTask.ScanResult result = currentScanTask.getValue();

            directoryTreeView.setRoot(result.rootTreeItem);

            searchTextField.clear();
            masterFileList.setAll(result.fileList);

            totalFilesLabel.setText("Total Files: " + result.fileList.size());
            totalSizeLabel.setText("Total Size: " + formatSize(result.totalSizeBytes));

            unbindTaskProperties();
            setControlsScanningState(false);
            statusLabel.setText("Scan completed successfully!");
            progressBar.setProgress(1.0);

            showAlert(Alert.AlertType.INFORMATION, "Scan Finished",
                    String.format("Scanning finished!\n\nFiles found: %d\nTotal size: %s\nSaved to database.",
                            result.fileList.size(), formatSize(result.totalSizeBytes)));
        });

        currentScanTask.setOnFailed(event -> {
            unbindTaskProperties();
            setControlsScanningState(false);
            statusLabel.setText("Scan failed.");
            progressBar.setProgress(0);

            Throwable ex = currentScanTask.getException();
            showAlert(Alert.AlertType.ERROR, "Scan Error", "An error occurred during scanning: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        currentScanTask.setOnCancelled(event -> {
            unbindTaskProperties();
            setControlsScanningState(false);
            statusLabel.setText("Scan cancelled.");
            progressBar.setProgress(0);
        });

        threadPool.submit(currentScanTask);
    }

    private void loadHistoricalScanToMainTable(long scanId) {
        List<FileInfo> historicalFiles = Database.fetchFilesByScanId(scanId);

        if (historicalFiles != null && !historicalFiles.isEmpty()) {
            searchTextField.clear();
            masterFileList.setAll(historicalFiles);

            long totalBytes = historicalFiles.stream().mapToLong(FileInfo::getSizeBytes).sum();
            totalFilesLabel.setText("Total Files: " + historicalFiles.size());
            totalSizeLabel.setText("Total Size: " + formatSize(totalBytes));

            statusLabel.setText("Loaded Historical Scan ID #" + scanId);
        } else {
            showAlert(Alert.AlertType.WARNING, "No Data", "No file records found for Scan ID #" + scanId);
        }
    }

    @FXML
    private void handleFetchApiData() {
        FileInfo selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a file from the table first.");
            return;
        }

        statusLabel.setText("Fetching file extension info via HTTP...");

        Task<ApiService.ExtensionDetails> apiTask = new Task<>() {
            @Override
            protected ApiService.ExtensionDetails call() throws Exception {
                ApiService service = new ApiService();
                return service.fetchExtensionInfo(selectedFile.getExtension());
            }
        };

        apiTask.setOnSucceeded(e -> {
            ApiService.ExtensionDetails details = apiTask.getValue();
            statusLabel.setText("API data fetched successfully.");

            showAlert(Alert.AlertType.INFORMATION,
                    "API Inspection: ." + selectedFile.getExtension(),
                    "Format: " + details.getExtensionName() + "\n\nDetails: " + details.getDescription());
        });

        apiTask.setOnFailed(e -> {
            statusLabel.setText("API Request failed.");
            showAlert(Alert.AlertType.ERROR, "Network Error", "Could not fetch JSON data: " + apiTask.getException().getMessage());
        });

        threadPool.submit(apiTask);
    }

    @FXML
    private void handleBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Scan");
        File selectedDirectory = chooser.showDialog(pathTextField.getScene().getWindow());
        if (selectedDirectory != null) {
            pathTextField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void handleCancel() {
        if (currentScanTask != null && currentScanTask.isRunning()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Cancellation");
            confirmAlert.setHeaderText("Cancel ongoing scan?");
            confirmAlert.setContentText("Are you sure you want to stop the directory scan?");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                currentScanTask.cancel();
            }
        }
    }

    @FXML
    private void handleShowHistory() {
        List<String> history = Database.fetchScanHistory();
        if (history.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Scan History", "No past scans found in database.");
            return;
        }

        try {
            Main.showHistoryWindow(this::loadHistoricalScanToMainTable);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Opening View", "Could not load history window: " + e.getMessage());
        }
    }

    private void openFileInSystem(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    threadPool.submit(() -> {
                        try {
                            Desktop.getDesktop().open(file);
                        } catch (Exception e) {
                            Platform.runLater(() ->
                                    showAlert(Alert.AlertType.ERROR, "Error Opening File", "Could not open file: " + e.getMessage())
                            );
                        }
                    });
                } else {
                    showAlert(Alert.AlertType.WARNING, "Not Supported", "Opening files is not supported on your system.");
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "File Not Found", "The file no longer exists at: " + filePath);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to open file: " + e.getMessage());
        }
    }

    private void setControlsScanningState(boolean isScanning) {
        scanButton.setDisable(isScanning);
        browseButton.setDisable(isScanning);
        pathTextField.setDisable(isScanning);
        cancelButton.setDisable(!isScanning);
    }

    private void unbindTaskProperties() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Main.showAlert(alertType, title, content);
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int exp = (int) (Math.log(sizeBytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", sizeBytes / Math.pow(1024, exp), pre);
    }
}