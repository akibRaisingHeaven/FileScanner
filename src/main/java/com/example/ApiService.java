package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ApiService {

    private static final String WIKI_API = "https://en.wikipedia.org/w/api.php";

    private final HttpClient httpClient;
    private final Gson gson;

    public ApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }

    public static class ExtensionDetails {
        private final String extensionName;
        private final String description;

        public ExtensionDetails(String extensionName, String description) {
            this.extensionName = extensionName;
            this.description = description;
        }

        public String getExtensionName() { return extensionName; }
        public String getDescription() { return description; }
    }

    /**
     * Sends an HTTP GET request to the Wikipedia API and parses the JSON response.
     *
     * Response shape (formatversion=2):
     * { "query": { "pages": [ { "title": "...", "extract": "..." } ] } }
     */
    public ExtensionDetails fetchExtensionInfo(String extension) throws Exception {
        if (extension == null || extension.isBlank()) {
            return new ExtensionDetails("No extension", "This file has no extension, so there is nothing to look up.");
        }

        String ext = extension.toLowerCase().trim();
        String searchTerm = URLEncoder.encode(buildSearchTerm(ext), StandardCharsets.UTF_8);

        String url = WIKI_API
                + "?action=query&format=json&formatversion=2"
                + "&generator=search&gsrlimit=1&gsrsearch=" + searchTerm
                + "&prop=extracts&exintro=1&explaintext=1&exsentences=3";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "FileScanner/1.0 (student project)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed with status code: " + response.statusCode());
        }

        return parseResponse(response.body(), ext);
    }

    // JSON parsing: walk query -> pages -> first page -> title / extract
    private ExtensionDetails parseResponse(String json, String ext) {
        JsonObject root = gson.fromJson(json, JsonObject.class);

        if (root == null || !root.has("query")) {
            return new ExtensionDetails("." + ext, "No online information was found for this file type.");
        }

        JsonArray pages = root.getAsJsonObject("query").getAsJsonArray("pages");
        if (pages == null || pages.isEmpty()) {
            return new ExtensionDetails("." + ext, "No online information was found for this file type.");
        }

        JsonObject page = pages.get(0).getAsJsonObject();
        String title = getString(page, "title", "." + ext);
        String extract = getString(page, "extract", "No description available.");

        return new ExtensionDetails(title, extract.trim());
    }

    private String getString(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : fallback;
    }

    // Some extensions are ambiguous as search terms, so refine them
    private String buildSearchTerm(String ext) {
        return switch (ext) {
            case "java" -> "Java programming language source file";
            case "txt" -> "text file";
            case "exe" -> "Windows executable file format";
            case "dll" -> "dynamic-link library";
            case "jpg", "jpeg" -> "JPEG image format";
            default -> ext + " file format";
        };
    }
}