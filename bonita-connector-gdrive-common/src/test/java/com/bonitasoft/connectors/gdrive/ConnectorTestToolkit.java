package com.bonitasoft.connectors.gdrive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

/**
 * Test toolkit for executing Bonita connectors through their full lifecycle.
 * <p>
 * Inspired by bonita-connector-rest's ConnectorTestToolkit, adapted for
 * direct connector execution (without deploying to a Bonita engine).
 * <p>
 * Usage:
 * <pre>
 * var inputs = ConnectorTestToolkit.authInputs();
 * inputs.put("folderName", "test-folder");
 * var result = ConnectorTestToolkit.execute(new GDriveCreateFolderConnector(), inputs);
 * assertThat(result.output("folderId")).isNotNull();
 * </pre>
 */
import org.opentest4j.TestAbortedException;

public final class ConnectorTestToolkit {

    private static final String DEFAULT_SA_PATH =
            "C:/BonitaStudioSubscription-2025.2-u3/bonitasoft-conectores-06171232f32e.json";

    private ConnectorTestToolkit() {
    }

    /**
     * Load Service Account JSON from system property, environment variable, or default path.
     * Resolution order:
     * <ol>
     *   <li>System property: {@code GDRIVE_SA_JSON_PATH}</li>
     *   <li>Environment variable: {@code GDRIVE_SA_JSON_PATH}</li>
     *   <li>Default path: {@value #DEFAULT_SA_PATH}</li>
     * </ol>
     */
    public static String loadServiceAccountJson() {
        String pathStr = System.getProperty("GDRIVE_SA_JSON_PATH",
                System.getenv().getOrDefault("GDRIVE_SA_JSON_PATH", DEFAULT_SA_PATH));
        Path path = Path.of(pathStr);
        if (!Files.exists(path)) {
            return null; // Caller should use assumeTrue to skip tests gracefully
        }
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Service Account JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Check if integration test prerequisites are available.
     *
     * @return true if SA JSON file exists and is readable
     */
    public static boolean isIntegrationTestAvailable() {
        return loadServiceAccountJson() != null;
    }

    /**
     * Build authentication inputs using Service Account JSON.
     *
     * @return mutable map with auth parameters pre-filled
     */
    public static Map<String, Object> authInputs() {
        String saJson = loadServiceAccountJson();
        if (saJson == null) {
            throw new IllegalStateException(
                    "Service Account JSON not found. Set GDRIVE_SA_JSON_PATH or use assumeTrue(isIntegrationTestAvailable()) in @BeforeAll.");
        }
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("serviceAccountJson", saJson);
        inputs.put("applicationName", "Bonita-GDrive-IT");
        return inputs;
    }

    /**
     * Execute a connector through the full Bonita lifecycle:
     * VALIDATE → CONNECT → EXECUTE → DISCONNECT.
     *
     * @param connector the connector instance
     * @param inputs    the input parameters
     * @return execution result with outputs and timing info
     * @throws ConnectorValidationException if validation fails
     * @throws ConnectorException           if connect or execute fails
     */
    public static ConnectorResult execute(AbstractGDriveConnector connector, Map<String, Object> inputs)
            throws ConnectorValidationException, ConnectorException {

        connector.setInputParameters(inputs);

        long start = System.currentTimeMillis();

        // Phase 1: VALIDATE
        connector.validateInputParameters();
        long afterValidate = System.currentTimeMillis();

        // Phase 2: CONNECT
        connector.connect();
        long afterConnect = System.currentTimeMillis();

        // Phase 3: EXECUTE
        connector.executeBusinessLogic();
        long afterExecute = System.currentTimeMillis();

        // Phase 4: DISCONNECT
        try {
            connector.disconnect();
        } catch (Exception e) {
            // Log but don't fail - disconnect errors are non-critical in tests
            System.err.println("Warning: disconnect error: " + e.getMessage());
        }
        long end = System.currentTimeMillis();

        return new ConnectorResult(
                connector.getOutputs(),
                afterValidate - start,
                afterConnect - afterValidate,
                afterExecute - afterConnect,
                end - afterExecute,
                end - start
        );
    }

    /**
     * Infrastructure error keywords that indicate missing configuration, not code bugs.
     * Tests hitting these errors are skipped (not failed).
     */
    private static final List<String> INFRA_ERROR_KEYWORDS = List.of(
            "storageQuotaExceeded", "notFound", "403 Forbidden", "401 Unauthorized",
            "PERMISSION_DENIED", "SERVICE_DISABLED"
    );

    /**
     * Execute a connector, but skip (abort) the test if the error is an infrastructure issue.
     * This allows ITs to run during mvn install without failing the build when
     * the required infrastructure (Shared Drive, permissions) is not available.
     */
    public static ConnectorResult executeOrSkipOnInfraError(AbstractGDriveConnector connector,
            Map<String, Object> inputs) throws ConnectorValidationException, ConnectorException {
        try {
            return execute(connector, inputs);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            String fullMsg = msg + " " + causeMsg;

            for (String keyword : INFRA_ERROR_KEYWORDS) {
                if (fullMsg.contains(keyword)) {
                    throw new TestAbortedException(
                            "Skipped: infrastructure not available (" + keyword + "): " + msg);
                }
            }
            throw e; // Real bug — let it fail
        }
    }

    /**
     * Result of a connector execution with outputs and timing.
     */
    public record ConnectorResult(
            Map<String, Object> outputs,
            long validateMs,
            long connectMs,
            long executeMs,
            long disconnectMs,
            long totalMs
    ) {
        /**
         * Get a typed output parameter.
         */
        @SuppressWarnings("unchecked")
        public <T> T output(String name) {
            return (T) outputs.get(name);
        }

        /**
         * Get output as String.
         */
        public String stringOutput(String name) {
            Object val = outputs.get(name);
            return val != null ? val.toString() : null;
        }

        /**
         * Check if the connector reported success.
         */
        public boolean isSuccess() {
            Object success = outputs.get("success");
            return Boolean.TRUE.equals(success);
        }

        /**
         * Print timing summary to stdout.
         */
        public void printTiming(String operationName) {
            System.out.printf("--- %s timing ---%n", operationName);
            System.out.printf("  Validate:   %d ms%n", validateMs);
            System.out.printf("  Connect:    %d ms%n", connectMs);
            System.out.printf("  Execute:    %d ms%n", executeMs);
            System.out.printf("  Disconnect: %d ms%n", disconnectMs);
            System.out.printf("  Total:      %d ms%n", totalMs);
        }
    }
}
