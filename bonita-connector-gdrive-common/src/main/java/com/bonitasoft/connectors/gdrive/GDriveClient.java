package com.bonitasoft.connectors.gdrive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;

import lombok.extern.slf4j.Slf4j;

/**
 * Client wrapper for Google Drive API.
 * <p>
 * Implements {@link AutoCloseable} for proper resource cleanup in the
 * DISCONNECT phase. Encapsulates authentication, retry logic, and
 * connection management.
 *
 * <h3>Usage in connector:</h3>
 * <pre>
 * try (var client = new GDriveClient(config)) {
 *     var driveService = client.getDriveService();
 *     var result = driveService.files().get(fileId).execute();
 * }
 * </pre>
 */
@Slf4j
public class GDriveClient implements AutoCloseable {

    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 1_000;
    private static final long MAX_DELAY_MS = 64_000;
    private static final double JITTER_FACTOR = 0.2;

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final GDriveConfiguration configuration;
    private final Drive driveService;
    private final HttpTransport httpTransport;

    /**
     * Create a new client and establish connection.
     *
     * @param configuration connector configuration
     * @throws GDriveException.ConnectionException if connection fails
     */
    public GDriveClient(GDriveConfiguration configuration) {
        this.configuration = configuration;
        configuration.validate();

        try {
            log.debug("Initializing Google Drive client for application: {}", configuration.applicationName());
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredentials credentials = buildCredentials();
            HttpRequestInitializer requestInitializer = buildRequestInitializer(credentials);
            
            this.driveService = new Drive.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                    .setApplicationName(configuration.applicationName())
                    .build();
            
            log.info("Google Drive client initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize Google Drive client: {}", e.getMessage());
            throw new GDriveException.ConnectionException(
                    "Failed to connect to Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Get the underlying Drive service for API calls.
     */
    public Drive getDriveService() {
        return driveService;
    }

    /**
     * Execute an operation with exponential backoff retry.
     * <p>
     * Retries on rate-limit (429) and server errors (5xx).
     *
     * @param operation   the operation to execute
     * @param description description for logging
     * @param <T>         result type
     * @return the operation result
     * @throws GDriveException if all retries fail
     */
    public <T> T executeWithRetry(Callable<T> operation, String description) {
        int attempt = 0;
        GDriveException lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    log.debug("Retry attempt {}/{} for: {}", attempt, MAX_RETRIES, description);
                }
                return operation.call();
            } catch (Exception e) {
                GDriveException gdEx = wrapException(e);
                
                if (!gdEx.isRetryable()) {
                    log.warn("Non-retryable error for {}: {}", description, gdEx.getMessage());
                    throw gdEx;
                }

                lastException = gdEx;
                attempt++;

                if (attempt > MAX_RETRIES) {
                    log.error("Max retries ({}) exceeded for: {}", MAX_RETRIES, description);
                    break;
                }

                long delay = calculateBackoffDelay(attempt);
                log.warn("Retryable error (attempt {}/{}), waiting {}ms before retry: {}",
                        attempt, MAX_RETRIES, delay, gdEx.getMessage());
                sleep(delay);
            }
        }

        throw new GDriveException(
                String.format("Max retries (%d) exceeded for %s: %s", 
                        MAX_RETRIES, description, lastException.getMessage()),
                lastException);
    }

    @Override
    public void close() {
        log.debug("Closing Google Drive client");
        // HttpTransport cleanup if needed
        // The Google API client handles connection pooling internally
    }

    // --- Private methods ---

    private GoogleCredentials buildCredentials() throws IOException {
        if (configuration.isServiceAccountAuth()) {
            return buildServiceAccountCredentials();
        } else if (configuration.isOAuthAuth()) {
            return buildOAuthCredentials();
        }
        throw new GDriveException.ValidationException("No valid authentication configured");
    }

    private GoogleCredentials buildServiceAccountCredentials() throws IOException {
        log.debug("Building Service Account credentials");
        String json = resolveServiceAccountJson();
        
        try (var stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
            
            String impersonateUser = configuration.impersonateUser();
            if (impersonateUser != null && !impersonateUser.isBlank()) {
                log.debug("Impersonating user: {}", impersonateUser);
                // For domain-wide delegation with impersonation
                if (credentials instanceof ServiceAccountCredentials serviceAccountCreds) {
                    return serviceAccountCreds.createDelegated(impersonateUser)
                            .createScoped(configuration.scopes());
                }
            }

            // Apply scopes
            return credentials.createScoped(configuration.scopes());
        }
    }

    private String resolveServiceAccountJson() {
        // Priority: 1. Connector input, 2. Environment variable, 3. JVM property
        String json = configuration.serviceAccountJson();
        
        if (json == null || json.isBlank()) {
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (envPath != null) {
                log.debug("Using GOOGLE_APPLICATION_CREDENTIALS from environment");
                try {
                    json = java.nio.file.Files.readString(java.nio.file.Path.of(envPath));
                } catch (IOException e) {
                    throw new GDriveException.ConnectionException(
                            "Failed to read credentials from GOOGLE_APPLICATION_CREDENTIALS: " + envPath, e);
                }
            }
        }
        
        if (json == null || json.isBlank()) {
            String propPath = System.getProperty("google.application.credentials");
            if (propPath != null) {
                log.debug("Using google.application.credentials from JVM property");
                try {
                    json = java.nio.file.Files.readString(java.nio.file.Path.of(propPath));
                } catch (IOException e) {
                    throw new GDriveException.ConnectionException(
                            "Failed to read credentials from google.application.credentials: " + propPath, e);
                }
            }
        }
        
        if (json == null || json.isBlank()) {
            throw new GDriveException.ValidationException(
                    "Service Account JSON not provided and no fallback credentials found");
        }
        
        return json;
    }

    private GoogleCredentials buildOAuthCredentials() {
        log.debug("Building OAuth 2.0 credentials");
        return UserCredentials.newBuilder()
                .setClientId(configuration.clientId())
                .setClientSecret(configuration.clientSecret())
                .setRefreshToken(configuration.refreshToken())
                .setTokenServerUri(java.net.URI.create(TOKEN_ENDPOINT))
                .build();
    }

    private HttpRequestInitializer buildRequestInitializer(GoogleCredentials credentials) {
        HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
        
        return request -> {
            adapter.initialize(request);
            request.setConnectTimeout(configuration.connectTimeout());
            request.setReadTimeout(configuration.readTimeout());
        };
    }

    private GDriveException wrapException(Exception e) {
        if (e instanceof GDriveException) {
            return (GDriveException) e;
        }
        if (e instanceof com.google.api.client.googleapis.json.GoogleJsonResponseException googleEx) {
            return GDriveException.fromGoogleException(googleEx);
        }
        return new GDriveException("Unexpected error: " + e.getMessage(), e);
    }

    private long calculateBackoffDelay(int attempt) {
        long baseDelay = BASE_DELAY_MS * (1L << (attempt - 1)); // Exponential: 1s, 2s, 4s, 8s...
        long capped = Math.min(baseDelay, MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong((long) (capped * JITTER_FACTOR));
        return capped + jitter;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GDriveException("Operation interrupted", e);
        }
    }
}
