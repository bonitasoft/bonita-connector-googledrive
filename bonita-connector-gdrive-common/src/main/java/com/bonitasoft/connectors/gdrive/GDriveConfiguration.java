package com.bonitasoft.connectors.gdrive;

import java.util.List;
import java.util.Map;

import com.google.api.services.drive.DriveScopes;

/**
 * Immutable configuration for Google Drive connectors.
 * <p>
 * Uses Java 17 record with compact constructor for validation.
 * Supports both Service Account and OAuth 2.0 authentication.
 *
 * @param serviceAccountJson Service Account JSON key content (not path)
 * @param impersonateUser    Email of user to impersonate (domain-wide delegation)
 * @param clientId           OAuth 2.0 client ID
 * @param clientSecret       OAuth 2.0 client secret
 * @param refreshToken       OAuth 2.0 refresh token
 * @param applicationName    Application name for API requests
 * @param scopes             OAuth scopes to request
 * @param connectTimeout     Connection timeout in milliseconds
 * @param readTimeout        Read timeout in milliseconds
 */
public record GDriveConfiguration(
        String serviceAccountJson,
        String impersonateUser,
        String clientId,
        String clientSecret,
        String refreshToken,
        String applicationName,
        List<String> scopes,
        int connectTimeout,
        int readTimeout
) {
    // Default values
    private static final String DEFAULT_APPLICATION_NAME = "Bonita-GDrive-Connector";
    private static final List<String> DEFAULT_SCOPES = List.of(DriveScopes.DRIVE);
    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;
    private static final int DEFAULT_READ_TIMEOUT = 60_000;

    /**
     * Compact constructor with validation.
     * <p>
     * Applies defaults for optional fields and validates invariants.
     * 
     * @throws IllegalArgumentException if timeouts are explicitly negative
     */
    public GDriveConfiguration {
        // Apply defaults for optional fields
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = DEFAULT_APPLICATION_NAME;
        }
        if (scopes == null || scopes.isEmpty()) {
            scopes = DEFAULT_SCOPES;
        }
        
        // Validate timeouts - allow 0 (will use default) but reject negative
        if (connectTimeout < 0) {
            throw new IllegalArgumentException("connectTimeout must be non-negative, got: " + connectTimeout);
        }
        if (readTimeout < 0) {
            throw new IllegalArgumentException("readTimeout must be non-negative, got: " + readTimeout);
        }
        
        // Apply defaults for zero timeouts
        if (connectTimeout == 0) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == 0) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }

    /**
     * Factory method to create configuration from Bonita input parameters.
     *
     * @param inputs Map of input parameters from the connector
     * @return Configured GDriveConfiguration instance
     */
    @SuppressWarnings("unchecked")
    public static GDriveConfiguration from(Map<String, Object> inputs) {
        return new GDriveConfiguration(
                getStringOrNull(inputs, "serviceAccountJson"),
                getStringOrNull(inputs, "impersonateUser"),
                getStringOrNull(inputs, "clientId"),
                getStringOrNull(inputs, "clientSecret"),
                getStringOrNull(inputs, "refreshToken"),
                getStringOrNull(inputs, "applicationName"),
                (List<String>) inputs.get("scopes"),
                getIntOrDefault(inputs, "connectTimeout", DEFAULT_CONNECT_TIMEOUT),
                getIntOrDefault(inputs, "readTimeout", DEFAULT_READ_TIMEOUT)
        );
    }

    /**
     * Check if Service Account authentication is configured.
     */
    public boolean isServiceAccountAuth() {
        return serviceAccountJson != null && !serviceAccountJson.isBlank();
    }

    /**
     * Check if OAuth 2.0 authentication is configured.
     */
    public boolean isOAuthAuth() {
        return isNotBlank(clientId) && isNotBlank(clientSecret) && isNotBlank(refreshToken);
    }

    /**
     * Check if any valid authentication method is configured.
     */
    public boolean hasValidAuth() {
        return isServiceAccountAuth() || isOAuthAuth();
    }

    /**
     * Validate that authentication is properly configured.
     *
     * @throws GDriveException.ValidationException if no valid auth is configured
     */
    public void validate() {
        if (!hasValidAuth()) {
            throw new GDriveException.ValidationException(
                    "Authentication required: provide either serviceAccountJson or OAuth credentials (clientId, clientSecret, refreshToken)");
        }
        
        // Validate Service Account JSON is actual JSON, not a file path
        if (isServiceAccountAuth() && !serviceAccountJson.trim().startsWith("{")) {
            throw new GDriveException.ValidationException(
                    "serviceAccountJson must contain the JSON content, not a file path. " +
                    "Copy the entire contents of your service account key file.");
        }
    }

    // --- Helper methods ---

    private static String getStringOrNull(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        return value != null ? value.toString() : null;
    }

    private static int getIntOrDefault(Map<String, Object> inputs, String key, int defaultValue) {
        Object value = inputs.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
