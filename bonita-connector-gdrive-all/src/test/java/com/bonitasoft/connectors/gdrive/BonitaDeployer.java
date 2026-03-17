package com.bonitasoft.connectors.gdrive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys a .bar file to a Bonita Runtime via REST API.
 *
 * <p>Handles the full deploy lifecycle: login → upload → deploy → actor mapping → enable.</p>
 *
 * <p>Works with both Bonita Studio embedded Runtime and standalone Bonita Runtime.</p>
 */
public final class BonitaDeployer {

    private static final Logger log = LoggerFactory.getLogger(BonitaDeployer.class);

    private final String bonitaUrl;
    private final String user;
    private final String password;

    private String sessionCookie;
    private String apiToken;

    public BonitaDeployer(String bonitaUrl, String user, String password) {
        this.bonitaUrl = bonitaUrl.endsWith("/") ? bonitaUrl.substring(0, bonitaUrl.length() - 1) : bonitaUrl;
        this.user = user;
        this.password = password;
    }

    /**
     * Deploys a .bar file and returns the process ID.
     * If the process already exists, returns null (caller should handle).
     */
    public String deploy(Path barFile) throws IOException {
        log.info("Deploying {} to {}", barFile.getFileName(), bonitaUrl);
        log.info("Bar file exists: {}, size: {} bytes", Files.exists(barFile), Files.size(barFile));

        login();
        log.info("Login OK, token: {}", apiToken != null ? apiToken.substring(0, Math.min(8, apiToken.length())) + "..." : "NULL");

        // 1. Upload
        String uploadRef = upload(barFile);
        log.info("Uploaded .bar: [{}]", uploadRef);

        // 2. Deploy
        String deployResponse = post("/API/bpm/process",
                "{\"fileupload\":\"" + uploadRef + "\"}");

        String processId = extractJsonField(deployResponse, "id");
        String configState = extractJsonField(deployResponse, "configurationState");
        log.info("Deployed process: id={}, configurationState={}", processId, configState);

        if (processId == null) {
            String errorMsg = extractJsonField(deployResponse, "message");
            throw new IOException("Deploy failed: " + (errorMsg != null ? errorMsg : deployResponse));
        }

        // 3. Map actor to /acme group
        String actorsJson = get("/API/bpm/actor?p=0&c=10&f=process_id=" + processId);
        String actorId = extractJsonField(actorsJson, "id");
        if (actorId != null) {
            post("/API/bpm/actorMember",
                    "{\"actor_id\":\"" + actorId + "\",\"member_type\":\"GROUP\",\"group_id\":\"1\"}");
            log.info("Mapped actor {} to group /acme", actorId);
        }

        // 4. Enable
        put("/API/bpm/process/" + processId, "{\"activationState\":\"ENABLED\"}");
        log.info("Process enabled");

        logout();
        return processId;
    }

    /**
     * Checks if a process with the given name and version is already deployed.
     */
    public boolean isDeployed(String processName, String processVersion) throws IOException {
        login();
        String response = get("/API/bpm/process?p=0&c=10&f=name=" + processName + "&f=version=" + processVersion);
        logout();
        return response != null && response.contains("\"id\"");
    }

    // --- HTTP helpers ---

    private void login() throws IOException {
        URL url = new URL(bonitaUrl + "/loginservice");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "username=" + user + "&password=" + password + "&redirect=false";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200 && status != 204) {
            throw new IOException("Login failed: HTTP " + status);
        }

        // Collect ALL Set-Cookie headers (Java returns only first with getHeaderField)
        StringBuilder allCookies = new StringBuilder();
        for (int i = 0; ; i++) {
            String key = conn.getHeaderFieldKey(i);
            String val = conn.getHeaderField(i);
            if (key == null && val == null) break;
            if ("Set-Cookie".equalsIgnoreCase(key)) {
                if (allCookies.length() > 0) allCookies.append("; ");
                allCookies.append(val);
            }
        }
        sessionCookie = allCookies.toString();

        // Extract X-Bonita-API-Token from cookies
        Matcher m = Pattern.compile("X-Bonita-API-Token=([^;]+)").matcher(sessionCookie);
        if (m.find()) {
            apiToken = m.group(1);
        }

        // Fallback: get token from session API
        if (apiToken == null) {
            try {
                String session = get("/API/system/session/unusedId");
                if (session != null) {
                    apiToken = extractJsonField(session, "token");
                }
            } catch (IOException e) {
                log.debug("Session API not available (Studio mode): {}", e.getMessage());
            }
        }

        conn.disconnect();
        log.debug("Logged in (token: {})", apiToken != null ? apiToken.substring(0, 8) + "..." : "null");
    }

    private void logout() {
        try {
            URL url = new URL(bonitaUrl + "/logoutservice");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (sessionCookie != null) conn.setRequestProperty("Cookie", sessionCookie);
            conn.getResponseCode();
            conn.disconnect();
        } catch (IOException ignored) {
            // Best effort
        }
    }

    private String upload(Path barFile) throws IOException {
        String boundary = "----BarUpload" + System.currentTimeMillis();
        URL url = new URL(bonitaUrl + "/portal/processUpload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        setAuth(conn);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        byte[] fileBytes = Files.readAllBytes(barFile);

        try (OutputStream os = conn.getOutputStream()) {
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"process.bar\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            os.write(header.getBytes(StandardCharsets.UTF_8));
            os.write(fileBytes);
            os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        String response = readResponse(conn);
        conn.disconnect();
        return response.trim();
    }

    private String get(String path) throws IOException {
        URL url = new URL(bonitaUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        setAuth(conn);
        String response = readResponse(conn);
        conn.disconnect();
        return response;
    }

    private String post(String path, String jsonBody) throws IOException {
        URL url = new URL(bonitaUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        setAuth(conn);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        String response = readResponse(conn);
        conn.disconnect();
        return response;
    }

    private void put(String path, String jsonBody) throws IOException {
        URL url = new URL(bonitaUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        setAuth(conn);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        conn.getResponseCode(); // consume response
        conn.disconnect();
    }

    private void setAuth(HttpURLConnection conn) {
        if (sessionCookie != null) conn.setRequestProperty("Cookie", sessionCookie);
        if (apiToken != null) conn.setRequestProperty("X-Bonita-API-Token", apiToken);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
