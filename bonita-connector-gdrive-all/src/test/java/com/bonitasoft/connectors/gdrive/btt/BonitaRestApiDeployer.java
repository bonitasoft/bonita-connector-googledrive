package com.bonitasoft.connectors.gdrive.btt;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys a Bonita .bar file to a running Bonita instance via REST API.
 *
 * <p>Handles the full lifecycle: login -> upload -> deploy -> enable.</p>
 *
 * <p>Uses Java's {@link HttpClient} with cookie management for session tracking
 * and CSRF token extraction.</p>
 */
public class BonitaRestApiDeployer {

    private static final Logger logger = LoggerFactory.getLogger(BonitaRestApiDeployer.class);

    private final String bonitaUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;

    private String apiToken;

    /**
     * Creates a new deployer targeting the given Bonita instance.
     *
     * @param bonitaUrl base URL (e.g., {@code http://localhost:8080/bonita})
     * @param username  technical user (e.g., {@code install})
     * @param password  technical password (e.g., {@code install})
     */
    public BonitaRestApiDeployer(String bonitaUrl, String username, String password) {
        this.bonitaUrl = bonitaUrl.endsWith("/") ? bonitaUrl.substring(0, bonitaUrl.length() - 1) : bonitaUrl;
        this.username = username;
        this.password = password;

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Performs a full deployment: login -> upload -> deploy -> enable.
     *
     * @param barFile the .bar file to deploy
     * @return the process definition ID
     * @throws IOException          if any HTTP request fails
     * @throws InterruptedException if the thread is interrupted
     */
    public String deploy(File barFile) throws IOException, InterruptedException {
        login();
        String tempName = uploadBar(barFile);
        String processId = deployProcess(tempName);
        enableProcess(processId);
        return processId;
    }

    /**
     * Logs in to Bonita and extracts session cookie + CSRF token.
     */
    void login() throws IOException, InterruptedException {
        logger.info("Logging in to Bonita at {}", bonitaUrl);

        String body = "username=" + urlEncode(username)
                + "&password=" + urlEncode(password)
                + "&redirect=false";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bonitaUrl + "/loginservice"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new IOException("Login failed with status " + response.statusCode()
                    + ": " + response.body());
        }

        extractApiToken(response);
        logger.info("Login successful (apiToken={})", apiToken != null ? "present" : "missing");
    }

    /**
     * Uploads a .bar file to Bonita's temporary storage.
     *
     * @return the temporary file name returned by the server
     */
    String uploadBar(File barFile) throws IOException, InterruptedException {
        logger.info("Uploading BAR file: {}", barFile.getName());

        String boundary = "----BonitaUpload" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(barFile.toPath());

        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + barFile.getName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";

        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);

        byte[] multipartBody = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, multipartBody, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, multipartBody, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, multipartBody, prefixBytes.length + fileBytes.length, suffixBytes.length);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(bonitaUrl + "/portal/processUpload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody));

        if (apiToken != null) {
            requestBuilder.header("X-Bonita-API-Token", apiToken);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Upload failed with status " + response.statusCode()
                    + ": " + response.body());
        }

        String tempFileName = response.body().trim();
        if (tempFileName.startsWith("\"") && tempFileName.endsWith("\"")) {
            tempFileName = tempFileName.substring(1, tempFileName.length() - 1);
        }

        logger.info("Upload successful, temp file: {}", tempFileName);
        return tempFileName;
    }

    /**
     * Deploys an uploaded .bar file as a process definition.
     *
     * @return the process definition ID
     */
    String deployProcess(String uploadedFileName) throws IOException, InterruptedException {
        logger.info("Deploying process from temp file: {}", uploadedFileName);

        String jsonBody = "{\"fileupload\":\"" + uploadedFileName + "\"}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(bonitaUrl + "/API/bpm/process"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (apiToken != null) {
            requestBuilder.header("X-Bonita-API-Token", apiToken);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Deploy failed with status " + response.statusCode()
                    + ": " + response.body());
        }

        String processId = extractJsonField(response.body(), "id");
        logger.info("Process deployed with ID: {}", processId);
        return processId;
    }

    /**
     * Enables a deployed process definition.
     */
    void enableProcess(String processId) throws IOException, InterruptedException {
        logger.info("Enabling process: {}", processId);

        String jsonBody = "{\"activationState\":\"ENABLED\"}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(bonitaUrl + "/API/bpm/process/" + processId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (apiToken != null) {
            requestBuilder.header("X-Bonita-API-Token", apiToken);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Enable failed with status " + response.statusCode()
                    + ": " + response.body());
        }

        logger.info("Process enabled successfully");
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void extractApiToken(HttpResponse<?> response) {
        response.headers().allValues("Set-Cookie").forEach(cookie -> {
            if (cookie.startsWith("X-Bonita-API-Token=")) {
                apiToken = cookie.split("=", 2)[1].split(";")[0];
            }
        });

        if (apiToken == null) {
            httpClient.cookieHandler().ifPresent(handler -> {
                if (handler instanceof CookieManager cm) {
                    cm.getCookieStore().getCookies().forEach(c -> {
                        if ("X-Bonita-API-Token".equals(c.getName())) {
                            apiToken = c.getValue();
                        }
                    });
                }
            });
        }
    }

    private static String extractJsonField(String json, String fieldName) {
        String searchKey = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex < 0) {
            return null;
        }
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) {
            return null;
        }
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length()
                    && json.charAt(valueEnd) != ','
                    && json.charAt(valueEnd) != '}'
                    && json.charAt(valueEnd) != ']') {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
