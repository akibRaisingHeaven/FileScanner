package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.function.Consumer;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Database.initializeDatabase();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/scanner-view.fxml"));
        Scene scene = new Scene(loader.load(), 950, 650);

        primaryStage.setTitle("Directory Scanner & File Analyzer");
        setStageIcon(primaryStage);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void showHistoryWindow(Consumer<Long> onScanSelected) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/example/history-view.fxml"));
        Stage historyStage = new Stage();
        historyStage.initModality(Modality.APPLICATION_MODAL);
        historyStage.setTitle("Database Scan History & Charts");
        setStageIcon(historyStage);

        Scene scene = new Scene(loader.load());
        historyStage.setScene(scene);

        HistoryController controller = loader.getController();
        controller.setOnScanLoadRequested(scanId -> {
            onScanSelected.accept(scanId);
            historyStage.close();
        });

        historyStage.showAndWait();
    }

    public static void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        dialogPane.setMinWidth(480);

        Stage alertStage = (Stage) dialogPane.getScene().getWindow();
        setStageIcon(alertStage);

        alert.showAndWait();
    }

    private static void setStageIcon(Stage stage) {
        try (InputStream iconStream = Main.class.getResourceAsStream("/com/example/icon.jpg")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}