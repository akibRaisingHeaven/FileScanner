package com.example;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ListView;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HistoryController {

    @FXML private ListView<String> historyListView;
    @FXML private PieChart countChart;
    @FXML private PieChart sizeChart;

    private static final int MAX_MAJOR_CATEGORIES = 7;
    private Consumer<Long> onScanLoadRequested;

    public void setOnScanLoadRequested(Consumer<Long> callback) {
        this.onScanLoadRequested = callback;
    }

    @FXML
    public void initialize() {
        List<String> historyList = Database.fetchScanHistory();
        if (historyList.isEmpty()) {
            return;
        }

        historyListView.setItems(FXCollections.observableArrayList(historyList));

        historyListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx.intValue() >= 0) {
                updateChartsForSelectedScan(historyListView.getItems().get(newIdx.intValue()));
            }
        });

        historyListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleLoadSelectedScan();
            }
        });

        countChart.setLabelsVisible(true);
        sizeChart.setLabelsVisible(true);
        historyListView.getSelectionModel().select(0);
    }

    @FXML
    private void handleLoadSelectedScan() {
        String selectedRecord = historyListView.getSelectionModel().getSelectedItem();
        if (selectedRecord != null && onScanLoadRequested != null) {
            long scanId = parseScanId(selectedRecord);
            if (scanId != -1) {
                onScanLoadRequested.accept(scanId);
            }
        }
    }

    private long parseScanId(String record) {
        try {
            int idStart = record.indexOf("ID #") + 4;
            int idEnd = record.indexOf(" -", idStart);
            return Long.parseLong(record.substring(idStart, idEnd));
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateChartsForSelectedScan(String selectedRecord) {
        long scanId = parseScanId(selectedRecord);
        if (scanId == -1) return;

        Map<String, Integer> rawCountStats = Database.getExtensionCountByScanId(scanId);
        countChart.setData(buildCountChartData(rawCountStats));

        Map<String, Long> rawSizeStats = Database.getExtensionSizeByScanId(scanId);
        sizeChart.setData(buildSizeChartData(rawSizeStats));
    }

    private ObservableList<PieChart.Data> buildCountChartData(Map<String, Integer> rawStats) {
        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        List<Map.Entry<String, Integer>> sorted = rawStats.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        int otherCount = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            if (i < MAX_MAJOR_CATEGORIES) {
                chartData.add(new PieChart.Data("." + entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
            } else {
                otherCount += entry.getValue();
            }
        }
        if (otherCount > 0) {
            chartData.add(new PieChart.Data("Other (" + otherCount + ")", otherCount));
        }
        return chartData;
    }

    private ObservableList<PieChart.Data> buildSizeChartData(Map<String, Long> rawStats) {
        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        List<Map.Entry<String, Long>> sorted = rawStats.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());

        long otherBytes = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            if (i < MAX_MAJOR_CATEGORIES) {
                double sizeMB = entry.getValue() / (1024.0 * 1024.0);
                chartData.add(new PieChart.Data(String.format(".%s (%.1f MB)", entry.getKey(), sizeMB), sizeMB));
            } else {
                otherBytes += entry.getValue();
            }
        }
        if (otherBytes > 0) {
            double otherMB = otherBytes / (1024.0 * 1024.0);
            chartData.add(new PieChart.Data(String.format("Other (%.1f MB)", otherMB), otherMB));
        }
        return chartData;
    }
}