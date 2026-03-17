package com.bonitasoft.connectors.gdrive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import com.google.api.services.drive.Drive;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base connector for Google Drive operations.
 * <p>
 * Implements the Bonita 4-phase lifecycle:
 * <ol>
 *   <li>VALIDATE — check inputs, fail fast</li>
 *   <li>CONNECT — create client, authenticate</li>
 *   <li>EXECUTE — call API, set outputs</li>
 *   <li>DISCONNECT — close client, release resources</li>
 * </ol>
 *
 * @see <a href="https://documentation.bonitasoft.com/bonita/latest/process/connector-archetype-tutorial">Bonita Connector Tutorial</a>
 */
@Slf4j
public abstract class AbstractGDriveConnector extends AbstractConnector {

    // --- Common input parameter names ---
    public static final String INPUT_SERVICE_ACCOUNT_JSON = "serviceAccountJson";
    public static final String INPUT_IMPERSONATE_USER = "impersonateUser";
    public static final String INPUT_CLIENT_ID = "clientId";
    public static final String INPUT_CLIENT_SECRET = "clientSecret";
    public static final String INPUT_REFRESH_TOKEN = "refreshToken";
    public static final String INPUT_APPLICATION_NAME = "applicationName";
    public static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    public static final String INPUT_READ_TIMEOUT = "readTimeout";

    // --- Common output parameter names ---
    public static final String OUTPUT_SUCCESS = "success";
    public static final String OUTPUT_ERROR_MESSAGE = "errorMessage";

    // --- Input validation limits (security/performance) ---
    protected static final int MAX_STRING_INPUT_LENGTH = 10_000;
    protected static final int MAX_FILE_NAME_LENGTH = 255;
    protected static final int MAX_DESCRIPTION_LENGTH = 5_000;
    protected static final int MAX_FILE_CONTENT_LENGTH = 50_000_000; // ~50MB Base64
    protected static final int MAX_QUERY_LENGTH = 2_000;

    protected GDriveConfiguration configuration;
    protected GDriveClient client;

    // --- Phase 1: VALIDATE ---

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        log.debug("Validating input parameters for {}", getConnectorName());
        List<String> errors = new ArrayList<>();
        
        validateAuthenticationInputs(errors);
        validateOperationInputs(errors);
        
        if (!errors.isEmpty()) {
            log.warn("Validation failed for {}: {}", getConnectorName(), errors);
            throw new ConnectorValidationException(this, errors);
        }
        log.debug("Input validation passed for {}", getConnectorName());
    }

    /**
     * Validate authentication parameters.
     * At least one auth method must be configured.
     */
    protected void validateAuthenticationInputs(List<String> errors) {
        String serviceAccountJson = getStringInput(INPUT_SERVICE_ACCOUNT_JSON);
        String clientId = getStringInput(INPUT_CLIENT_ID);
        String clientSecret = getStringInput(INPUT_CLIENT_SECRET);
        String refreshToken = getStringInput(INPUT_REFRESH_TOKEN);

        boolean hasServiceAccount = serviceAccountJson != null && !serviceAccountJson.isBlank();
        boolean hasOAuth = clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && refreshToken != null && !refreshToken.isBlank();

        if (!hasServiceAccount && !hasOAuth) {
            errors.add("Authentication required: provide either serviceAccountJson (Service Account) " +
                    "or clientId + clientSecret + refreshToken (OAuth 2.0)");
        }

        // Validate Service Account JSON is actual JSON, not a file path
        if (hasServiceAccount && !serviceAccountJson.trim().startsWith("{")) {
            errors.add("serviceAccountJson must contain the actual JSON content, not a file path. " +
                    "Copy the entire contents of your service account key file.");
        }

        // Validate timeouts
        Integer connectTimeout = getIntInput(INPUT_CONNECT_TIMEOUT);
        if (connectTimeout != null && connectTimeout < 0) {
            errors.add("connectTimeout must be a positive number");
        }
        Integer readTimeout = getIntInput(INPUT_READ_TIMEOUT);
        if (readTimeout != null && readTimeout < 0) {
            errors.add("readTimeout must be a positive number");
        }
    }

    /**
     * Validate operation-specific parameters.
     * Subclasses must implement this to check their own inputs.
     *
     * @param errors list to add validation errors to
     */
    protected abstract void validateOperationInputs(List<String> errors);

    /**
     * Get the connector name for logging.
     */
    protected abstract String getConnectorName();

    // --- Phase 2: CONNECT ---

    @Override
    public void connect() throws ConnectorException {
        log.info("Connecting to Google Drive for {}", getConnectorName());
        try {
            configuration = buildConfiguration();
            client = new GDriveClient(configuration);
            log.debug("Successfully connected to Google Drive");
        } catch (GDriveException e) {
            log.error("Failed to connect to Google Drive: {}", e.getMessage());
            throw new ConnectorException(GDriveException.truncateMessage("Failed to connect to Google Drive: " + e.getMessage()), e);
        }
    }

    /**
     * Build configuration from input parameters.
     * Subclasses may override to customize.
     */
    protected GDriveConfiguration buildConfiguration() {
        return new GDriveConfiguration(
            getStringInput(INPUT_SERVICE_ACCOUNT_JSON),
            getStringInput(INPUT_IMPERSONATE_USER),
            getStringInput(INPUT_CLIENT_ID),
            getStringInput(INPUT_CLIENT_SECRET),
            getStringInput(INPUT_REFRESH_TOKEN),
            getStringInput(INPUT_APPLICATION_NAME),
            null, // scopes - use defaults
            getIntInputOrDefault(INPUT_CONNECT_TIMEOUT, 30_000),
            getIntInputOrDefault(INPUT_READ_TIMEOUT, 60_000)
        );
    }

    /**
     * Inject a pre-built client for testing.
     * <p>
     * Intended for unit tests only — allows mock injection without reflection.
     *
     * @param client the client to inject
     */
    protected void setClient(GDriveClient client) {
        this.client = client;
    }

    // --- Phase 3: EXECUTE ---

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        log.info("Executing {} operation", getConnectorName());
        try {
            Drive driveService = client.getDriveService();
            executeOperation(driveService);
            setSuccessOutputs();
            log.info("{} operation completed successfully", getConnectorName());
        } catch (GDriveException e) {
            log.error("{} operation failed: {}", getConnectorName(), e.getMessage());
            setErrorOutputs(e.toOutputMessage());
            throw new ConnectorException(GDriveException.truncateMessage(e.getMessage()), e);
        } catch (Exception e) {
            log.error("{} operation failed unexpectedly: {}", getConnectorName(), e.getMessage(), e);
            setErrorOutputs("Unexpected error: " + e.getMessage());
            throw new ConnectorException(GDriveException.truncateMessage("Unexpected error: " + e.getMessage()), e);
        }
    }

    /**
     * Execute the operation-specific business logic.
     * Subclasses must implement this to perform their operation.
     *
     * @param driveService the Google Drive service
     * @throws GDriveException if the operation fails
     */
    protected abstract void executeOperation(Drive driveService) throws GDriveException;

    // --- Phase 4: DISCONNECT ---

    @Override
    public void disconnect() throws ConnectorException {
        log.debug("Disconnecting from Google Drive for {}", getConnectorName());
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing Google Drive client: {}", e.getMessage());
            }
        }
    }

    // --- Output helpers ---

    protected void setSuccessOutputs() {
        setOutputParameter(OUTPUT_SUCCESS, true);
        setOutputParameter(OUTPUT_ERROR_MESSAGE, "");
    }

    protected void setErrorOutputs(String message) {
        setOutputParameter(OUTPUT_SUCCESS, false);
        setOutputParameter(OUTPUT_ERROR_MESSAGE, GDriveException.truncateMessage(message));
    }

    /**
     * Expose output parameters for testing.
     */
    public Map<String, Object> getOutputs() {
        return getOutputParameters();
    }

    // --- Input helpers ---

    public String getStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    public String getStringInputOrDefault(String name, String defaultValue) {
        String value = getStringInput(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    public Integer getIntInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getIntInputOrDefault(String name, int defaultValue) {
        Integer value = getIntInput(name);
        return value != null ? value : defaultValue;
    }

    public Long getLongInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getBooleanInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    public boolean getBooleanInputOrDefault(String name, boolean defaultValue) {
        Boolean value = getBooleanInput(name);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> getListInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof List) return (List<String>) value;
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getMapInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Map) return (Map<String, String>) value;
        return null;
    }
}
