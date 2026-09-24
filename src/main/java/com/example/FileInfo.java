package com.example;

public class FileInfo {
    private final String name;
    private final String path;
    private final long sizeBytes;
    private final String extension;

    public FileInfo(String name, String path, long sizeBytes, String extension) {
        this.name = name;
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.extension = extension;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSizeBytes() { return sizeBytes; }
    public String getExtension() { return extension; }

    public String getFormattedSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        int exp = (int) (Math.log(sizeBytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", sizeBytes / Math.pow(1024, exp), pre);
    }
}