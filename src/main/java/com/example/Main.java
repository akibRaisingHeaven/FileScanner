package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize SQLite Database
        Database.initializeDatabase();

        // Load JavaFX View from FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/scanner-view.fxml"));
        Scene scene = new Scene(loader.load(), 950, 650);

        primaryStage.setTitle("Directory Scanner & File Analyzer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}