package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:filescanner.db";

    /**
     * Establishes a connection to the SQLite database.
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Initializes database tables if they do not exist.
     */
    public static void initializeDatabase() {
        // SQL statement to create scan history table
        String createHistoryTable = """
            CREATE TABLE IF NOT EXISTS scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                directory_path TEXT NOT NULL,
                total_files INTEGER NOT NULL,
                total_size_bytes INTEGER NOT NULL,
                scan_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;

        // SQL statement to create file metadata table
        String createFilesTable = """
            CREATE TABLE IF NOT EXISTS scanned_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scan_id INTEGER,
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_size_bytes INTEGER NOT NULL,
                file_extension TEXT,
                FOREIGN KEY (scan_id) REFERENCES scan_history(id) ON DELETE CASCADE
            );
            """;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            // Enable foreign key constraints in SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Execute table creation
            stmt.execute(createHistoryTable);
            stmt.execute(createFilesTable);

            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }
}