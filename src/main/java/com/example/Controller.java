package com.example;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;

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

    @FXML
    public void initialize() {
        // Configure table column value factories
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colSize.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        colExtension.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getExtension()));
        colPath.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPath()));
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
            statusLabel.setText("Please select a directory first.");
            return;
        }

        File targetDir = new File(path);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            statusLabel.setText("Invalid directory path!");
            return;
        }

        // Lock UI controls during scan setup
        scanButton.setDisable(true);
        cancelButton.setDisable(false);
        statusLabel.setText("Scanning...");
    }

    @FXML
    private void handleCancel() {
        scanButton.setDisable(false);
        cancelButton.setDisable(true);
        statusLabel.setText("Scan cancelled.");
        progressBar.setProgress(0);
    }
}