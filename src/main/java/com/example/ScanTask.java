package com.example;

import javafx.concurrent.Task;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanTask extends Task<ScanTask.ScanResult> {

    private final Path rootPath;

    public static class ScanResult {
        public final TreeItem<String> rootTreeItem;
        public final List<FileInfo> fileList;
        public final long totalSizeBytes;

        public ScanResult(TreeItem<String> rootTreeItem, List<FileInfo> fileList, long totalSizeBytes) {
            this.rootTreeItem = rootTreeItem;
            this.fileList = fileList;
            this.totalSizeBytes = totalSizeBytes;
        }
    }

    public ScanTask(File directory) {
        this.rootPath = directory.toPath();
    }

    @Override
    protected ScanResult call() throws Exception {
        updateMessage("Scanning directory...");
        // -1 puts the progress bar in smooth active scanning mode without artificial pre-passes
        updateProgress(-1, 1);

        List<FileInfo> files = new ArrayList<>();
        Map<Path, TreeItem<String>> dirTreeNodes = new HashMap<>();

        TreeItem<String> rootNode = new TreeItem<>(rootPath.getFileName() != null ? rootPath.getFileName().toString() : rootPath.toString());
        rootNode.setExpanded(true);
        dirTreeNodes.put(rootPath, rootNode);

        final long[] totalBytes = {0};
        final long[] fileCount = {0};

        // Single-pass directory walk for maximum speed
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isCancelled()) return FileVisitResult.TERMINATE;

                if (!dir.equals(rootPath)) {
                    TreeItem<String> dirItem = new TreeItem<>(dir.getFileName().toString());
                    Path parentPath = dir.getParent();
                    if (parentPath != null && dirTreeNodes.containsKey(parentPath)) {
                        dirTreeNodes.get(parentPath).getChildren().add(dirItem);
                    }
                    dirTreeNodes.put(dir, dirItem);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCancelled()) return FileVisitResult.TERMINATE;

                if (attrs.isRegularFile()) {
                    long size = attrs.size();
                    totalBytes[0] += size;
                    fileCount[0]++;

                    String name = file.getFileName().toString();
                    String ext = "";
                    int i = name.lastIndexOf('.');
                    if (i > 0) ext = name.substring(i + 1);

                    FileInfo fileInfo = new FileInfo(name, file.toAbsolutePath().toString(), size, ext);
                    files.add(fileInfo);

                    Path parent = file.getParent();
                    if (parent != null && dirTreeNodes.containsKey(parent)) {
                        dirTreeNodes.get(parent).getChildren().add(new TreeItem<>(name));
                    }

                    // Update live feedback in real-time so the user sees immediate progress
                    if (fileCount[0] % 50 == 0) {
                        updateMessage(String.format("Scanning... (%d files found)", fileCount[0]));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        if (!isCancelled()) {
            updateMessage("Saving metadata to database...");
            Database.saveScanResult(rootPath.toString(), files, totalBytes[0]);
        }

        return new ScanResult(rootNode, files, totalBytes[0]);
    }
}