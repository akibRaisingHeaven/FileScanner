package com.example;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiService {

    private final HttpClient httpClient;
    private final Gson gson;

    // Model class matching incoming JSON structure
    public static class ExtensionDetails {
        @SerializedName("title")
        private String extensionName;

        @SerializedName("body")
        private String description;

        public String getExtensionName() { return extensionName; }
        public String getDescription() { return description; }
    }

    public ApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }

    /**
     * Fetches mock file format information over HTTP and parses the JSON response.
     */
    public ExtensionDetails fetchExtensionInfo(String extension) throws Exception {
        // Using JSONPlaceholder as a reliable public REST API for demonstration
        String url = "https://jsonplaceholder.typicode.com/posts/1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Parse JSON response string into ExtensionDetails object via Gson
            return gson.fromJson(response.body(), ExtensionDetails.class);
        } else {
            throw new RuntimeException("HTTP Request failed with status code: " + response.statusCode());
        }
    }
}