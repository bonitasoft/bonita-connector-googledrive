package com.bonitasoft.connectors.gdrive.btt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.bonitasoft.test.toolkit.model.ProcessDefinition;
import com.bonitasoft.test.toolkit.model.ProcessInstance;
import com.bonitasoft.test.toolkit.model.User;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bonita Test Toolkit (BTT) integration tests for Google Drive Connector.
 *
 * <p>Validates connector deployment and execution inside a real Bonita runtime
 * (Docker via Testcontainers). The process is built programmatically as a .bar
 * and deployed via REST API before tests run.</p>
 *
 * <p>The process exercises all 8 GDrive connector types in 10 tasks:
 * CreateFolder, Upload, Search, Download, Copy, CreateArchiveFolder,
 * Move, UploadConvert, Export, Delete.</p>
 *
 * <h3>Setup Steps:</h3>
 * <ol>
 *   <li>Build: {@code mvn clean install -DskipTests}</li>
 *   <li>Run: {@code mvn verify -Pbtt -pl bonita-connector-gdrive-all -am}</li>
 * </ol>
 *
 * <h3>Required for E2E tests (tests 2-4):</h3>
 * <ul>
 *   <li>Service Account JSON file: {@code -DGDRIVE_SA_JSON_PATH=/path/to/sa.json}</li>
 *   <li>Shared Drive folder ID: {@code -DGDRIVE_SHARED_FOLDER_ID=folderId}</li>
 * </ul>
 */
@Tag("btt")
@DisplayName("GDrive Bonita Runtime Integration Tests (BTT)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GDriveBonitaRuntimeIT extends AbstractProcessTest {

    private static final Logger logger = LoggerFactory.getLogger(GDriveBonitaRuntimeIT.class);

    private static final String PROCESS_NAME = "GDriveConnectorTestProcess";
    private static final String PROCESS_VERSION = "1.0";

    private static final String DEFAULT_SA_JSON_PATH =
            "C:/BonitaStudioSubscription-2025.2-u3/bonitasoft-conectores-06171232f32e.json";
    private static final String DEFAULT_SHARED_FOLDER_ID = "1E7UnzYxYzWtRNVJGIFw0YAcX4a-ARVvM";

    private ProcessDefinition process;

    @BeforeAll
    void setUp() throws Exception {
        // Read SA JSON content from file if available (for E2E tests)
        String saJsonContent = readServiceAccountJson();
        String sharedFolderId = resolveProperty("GDRIVE_SHARED_FOLDER_ID", DEFAULT_SHARED_FOLDER_ID);

        // Set as system properties so the BarBuilder can embed them as default values
        if (saJsonContent != null && !saJsonContent.isEmpty()) {
            System.setProperty("GDRIVE_SA_JSON_CONTENT", saJsonContent);
        }
        if (sharedFolderId != null && !sharedFolderId.isEmpty()) {
            System.setProperty("GDRIVE_SHARED_FOLDER_ID", sharedFolderId);
        }

        // 1. Build .bar programmatically (ZIP-based, no Bonita API dependency)
        File barFile = new File("target/GDriveConnectorTestProcess--1.0.bar");
        GDriveProcessBarBuilder.writeToFile(barFile);

        // 2. Deploy to running Bonita via REST API (Testcontainers URL)
        var deployer = new BonitaRestApiDeployer(getBonitaUrl(), "install", "install");
        deployer.deploy(barFile);

        // 3. Get process via BTT
        process = getProcess(PROCESS_NAME, PROCESS_VERSION);
    }

    // =========================================================================
    // Infrastructure Tests (no credentials needed)
    // =========================================================================

    @Test
    @Order(0)
    @DisplayName("should deploy process to Bonita runtime")
    void should_deploy_process_to_bonita_runtime() {
        assertThat(process).isNotNull();
        assertThat(process.getName()).isEqualTo(PROCESS_NAME);
    }

    @Test
    @Order(1)
    @DisplayName("should start process instance when deployed")
    void should_start_process_instance_when_deployed() {
        User initiator = toolkit.getUser("install");
        ProcessInstance instance = process.startProcessFor(initiator);
        assertThat(instance).isNotNull();
        assertThat(instance.getId()).isNotNull();
    }

    // =========================================================================
    // E2E Tests (require real Google Drive credentials)
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("should complete process when valid Google Drive credentials")
    void should_complete_process_when_valid_google_drive_credentials() {
        assumeTrue(hasValidCredentials(),
                "Skipping: no SA JSON file found. Set -DGDRIVE_SA_JSON_PATH or env GDRIVE_SA_JSON_PATH");

        User initiator = toolkit.getUser("install");
        ProcessInstance instance = process.startProcessFor(initiator);

        waitForCompletion(instance, Duration.ofMinutes(2));
        assertNoFailedFlowNodes(instance);
    }

    @Test
    @Order(3)
    @DisplayName("should complete within acceptable time")
    void should_complete_within_acceptable_time() {
        assumeTrue(hasValidCredentials(),
                "Skipping: no SA JSON file found. Set -DGDRIVE_SA_JSON_PATH or env GDRIVE_SA_JSON_PATH");

        User initiator = toolkit.getUser("install");

        long startTime = System.currentTimeMillis();
        ProcessInstance instance = process.startProcessFor(initiator);
        waitForCompletion(instance, Duration.ofMinutes(2));
        long elapsed = System.currentTimeMillis() - startTime;

        logger.info("Process completed in {} ms ({} seconds)", elapsed, elapsed / 1000);

        assertThat(elapsed)
                .as("Process should complete within 90 seconds")
                .isLessThan(90_000L);
        assertNoFailedFlowNodes(instance);
    }

    @Test
    @Order(4)
    @DisplayName("should handle concurrent instances")
    void should_handle_concurrent_instances() {
        assumeTrue(hasValidCredentials(),
                "Skipping: no SA JSON file found. Set -DGDRIVE_SA_JSON_PATH or env GDRIVE_SA_JSON_PATH");

        User initiator = toolkit.getUser("install");

        // Start 3 concurrent instances
        List<ProcessInstance> instances = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ProcessInstance instance = process.startProcessFor(initiator);
            instances.add(instance);
            logger.info("Started concurrent instance {}: {}", i + 1, instance.getId());
        }

        // Wait for all to complete
        for (ProcessInstance instance : instances) {
            waitForCompletion(instance, Duration.ofMinutes(3));
        }

        // Assert no failures on any instance
        for (ProcessInstance instance : instances) {
            assertNoFailedFlowNodes(instance);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Reads the Service Account JSON content from a file path.
     * Resolves from: system property -> env var -> default path.
     *
     * @return the JSON content as a String, or null if file not found
     */
    private static String readServiceAccountJson() {
        String saJsonPath = resolveProperty("GDRIVE_SA_JSON_PATH", DEFAULT_SA_JSON_PATH);
        Path path = Path.of(saJsonPath);

        if (!Files.exists(path)) {
            logger.warn("SA JSON file not found at: {}. E2E tests will be skipped.", saJsonPath);
            return null;
        }

        try {
            String content = Files.readString(path);
            logger.info("Loaded SA JSON from: {} ({} chars)", saJsonPath, content.length());
            return content;
        } catch (Exception e) {
            logger.warn("Failed to read SA JSON file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether valid Google Drive credentials are available.
     */
    private static boolean hasValidCredentials() {
        String saJsonPath = resolveProperty("GDRIVE_SA_JSON_PATH", DEFAULT_SA_JSON_PATH);
        return Files.exists(Path.of(saJsonPath));
    }

    /**
     * Resolves a property from system property, then environment variable, then default.
     */
    private static String resolveProperty(String name, String defaultValue) {
        String value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return defaultValue;
    }
}
