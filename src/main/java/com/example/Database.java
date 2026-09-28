package com.example;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:filescanner.db";

    public static final String NOTE_MARKER = " | Note: ";

    public static Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection(DB_URL, config.toProperties());
    }

    public static void initializeDatabase() {
        String createHistoryTable = """
            CREATE TABLE IF NOT EXISTS scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                directory_path TEXT NOT NULL,
                total_files INTEGER NOT NULL,
                total_size_bytes INTEGER NOT NULL,
                scan_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;

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

            stmt.execute("PRAGMA foreign_keys = ON;");

//            stmt.execute("DROP TABLE IF EXISTS scanned_files;");
//            stmt.execute("DROP TABLE IF EXISTS scan_history;");

            stmt.execute(createHistoryTable);
            stmt.execute(createFilesTable);

            try {
                stmt.execute("ALTER TABLE scan_history ADD COLUMN note TEXT");
            } catch (SQLException ignored) {
            }

            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }

    public static void saveScanResult(String rootPath, List<FileInfo> files, long totalSize) throws SQLException {
        String insertHistorySql = "INSERT INTO scan_history (directory_path, total_files, total_size_bytes) VALUES (?, ?, ?)";
        String insertFileSql = "INSERT INTO scanned_files (scan_id, file_name, file_path, file_size_bytes, file_extension) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            long scanId = -1;
            try (PreparedStatement pstmtHistory = conn.prepareStatement(insertHistorySql, Statement.RETURN_GENERATED_KEYS)) {
                pstmtHistory.setString(1, rootPath);
                pstmtHistory.setInt(2, files.size());
                pstmtHistory.setLong(3, totalSize);
                pstmtHistory.executeUpdate();

                try (ResultSet rs = pstmtHistory.getGeneratedKeys()) {
                    if (rs.next()) {
                        scanId = rs.getLong(1);
                    }
                }
            }

            if (scanId != -1) {
                try (PreparedStatement pstmtFile = conn.prepareStatement(insertFileSql)) {
                    for (FileInfo file : files) {
                        pstmtFile.setLong(1, scanId);
                        pstmtFile.setString(2, file.getName());
                        pstmtFile.setString(3, file.getPath());
                        pstmtFile.setLong(4, file.getSizeBytes());
                        pstmtFile.setString(5, file.getExtension());
                        pstmtFile.addBatch();
                    }
                    pstmtFile.executeBatch();
                }
            }

            conn.commit();
        }
    }

    public static List<String> fetchScanHistory() {
        List<String> history = new ArrayList<>();
        String sql = "SELECT id, directory_path, total_files, total_size_bytes, scan_timestamp, note FROM scan_history ORDER BY scan_timestamp DESC";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String record = String.format("[%s] ID #%d - %s (%d files, %d bytes)",
                        rs.getString("scan_timestamp"),
                        rs.getInt("id"),
                        rs.getString("directory_path"),
                        rs.getInt("total_files"),
                        rs.getLong("total_size_bytes"));
                String note = rs.getString("note");
                if (note != null && !note.isBlank()) {
                    record += NOTE_MARKER + note;
                }
                history.add(record);
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch history: " + e.getMessage());
        }
        return history;
    }

    public static boolean updateScanNote(long scanId, String note) {
        String sql = "UPDATE scan_history SET note = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, note);
            pstmt.setLong(2, scanId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating note: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteScan(long scanId) {
        String sql = "DELETE FROM scan_history WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scanId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting scan: " + e.getMessage());
            return false;
        }
    }

    public static List<FileInfo> fetchFilesByScanId(long scanId) {
        List<FileInfo> files = new ArrayList<>();
        String sql = "SELECT * FROM scanned_files WHERE scan_id = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scanId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String fileName = rs.getString("file_name");
                String filePath = rs.getString("file_path");
                long sizeBytes = rs.getLong("file_size_bytes");
                String extension = rs.getString("file_extension");

                FileInfo file = new FileInfo(fileName, filePath, sizeBytes, extension);
                files.add(file);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching files by scan ID: " + e.getMessage());
        }

        return files;
    }

    public static Map<String, Integer> getExtensionCountByScanId(long scanId) {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT file_extension, COUNT(*) as count FROM scanned_files WHERE scan_id = ? GROUP BY file_extension";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scanId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String ext = rs.getString("file_extension");
                if (ext == null || ext.isEmpty()) ext = "no ext";
                stats.put(ext, rs.getInt("count"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching extension counts: " + e.getMessage());
        }
        return stats;
    }

    public static Map<String, Long> getExtensionSizeByScanId(long scanId) {
        Map<String, Long> stats = new HashMap<>();
        String sql = "SELECT file_extension, SUM(file_size_bytes) as total_size FROM scanned_files WHERE scan_id = ? GROUP BY file_extension";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, scanId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String ext = rs.getString("file_extension");
                if (ext == null || ext.isEmpty()) ext = "no ext";
                stats.put(ext, rs.getLong("total_size"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching extension sizes: " + e.getMessage());
        }
        return stats;
    }
}