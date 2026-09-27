package com.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class Controller {

    @FXML private TextField pathTextField;
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

    @FXML
    public void initialize() {
        // Configure table column value factories
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colSize.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        colExtension.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getExtension()));
        colPath.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPath()));
    }

    @FXML
    private void handleFetchApiData() {
        FileInfo selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a file from the table first.");
            return;
        }

        statusLabel.setText("Fetching file extension info via HTTP...");

        // Run HTTP Request on a background thread
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

            // Display parsed JSON data in an Alert Dialog
            showAlert(Alert.AlertType.INFORMATION,
                    "API Inspection: ." + selectedFile.getExtension(),
                    "Format: " + details.getExtensionName() + "\n\nDetails: " + details.getDescription());
        });

        apiTask.setOnFailed(e -> {
            statusLabel.setText("API Request failed.");
            showAlert(Alert.AlertType.ERROR, "Network Error", "Could not fetch JSON data: " + apiTask.getException().getMessage());
        });

        new Thread(apiTask).start();
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
            fileTableView.setItems(FXCollections.observableArrayList(result.fileList));

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

        Thread scanThread = new Thread(currentScanTask);
        scanThread.setDaemon(true);
        scanThread.start();
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

        StringBuilder sb = new StringBuilder("Past Directory Scans:\n\n");
        for (String entry : history) {
            sb.append(entry).append("\n");
        }

        showAlert(Alert.AlertType.INFORMATION, "Database Scan History", sb.toString());
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

    // Missing Helper Method
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int exp = (int) (Math.log(sizeBytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", sizeBytes / Math.pow(1024, exp), pre);
    }
}