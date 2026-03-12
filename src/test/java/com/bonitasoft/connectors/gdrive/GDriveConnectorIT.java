package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bonitasoft.web.client.BonitaClient;
import org.bonitasoft.web.client.api.ArchivedProcessInstanceApi;
import org.bonitasoft.web.client.api.ProcessInstanceApi;
import org.bonitasoft.web.client.exception.NotFoundException;
import org.bonitasoft.web.client.model.ArchivedProcessInstance;
import org.bonitasoft.web.client.services.policies.OrganizationImportPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Process-based integration tests for GDrive connectors.
 *
 * These tests build a Bonita process containing the connector, deploy it
 * to a Docker Bonita instance, and verify the connector executes correctly
 * within the process engine.
 *
 * Requires:
 * - Docker running
 * - GDRIVE_SERVICE_ACCOUNT_KEY_JSON environment variable set
 * - GDRIVE_TEST_FILE_ID environment variable set (a file ID accessible by the service account)
 * - GDRIVE_TEST_FOLDER_ID environment variable set (a folder ID for upload/create tests)
 * - Project built with mvn package (JAR must exist in target/)
 */
@Testcontainers
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
class GDriveConnectorIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(GDriveConnectorIT.class);

    // Connector definition IDs and versions (must match pom.xml properties)
    private static final String GET_FILE_DEF_ID = "gdrive-get-file";
    private static final String GET_FILE_DEF_VERSION = "1.0.0";

    private static final String LIST_FILES_DEF_ID = "gdrive-list-files";
    private static final String LIST_FILES_DEF_VERSION = "1.0.0";

    private static final String DELETE_FILE_DEF_ID = "gdrive-delete-file";
    private static final String DELETE_FILE_DEF_VERSION = "1.0.0";

    private static final String CREATE_FOLDER_DEF_ID = "gdrive-create-folder";
    private static final String CREATE_FOLDER_DEF_VERSION = "1.0.0";

    private static final String UPLOAD_FILE_DEF_ID = "gdrive-upload-file";
    private static final String UPLOAD_FILE_DEF_VERSION = "1.0.0";

    private static final String DOWNLOAD_FILE_DEF_ID = "gdrive-download-file";
    private static final String DOWNLOAD_FILE_DEF_VERSION = "1.0.0";

    private static final String MOVE_FILE_DEF_ID = "gdrive-move-file";
    private static final String MOVE_FILE_DEF_VERSION = "1.0.0";

    @Container
    static GenericContainer<?> BONITA_CONTAINER = new GenericContainer<>(
            DockerImageName.parse("bonita:10.2.0"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/bonita"))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    private BonitaClient client;

    @BeforeAll
    static void installOrganization() {
        var client = BonitaClient
                .builder(String.format("http://%s:%s/bonita",
                        BONITA_CONTAINER.getHost(),
                        BONITA_CONTAINER.getFirstMappedPort()))
                .build();
        client.login("install", "install");
        client.users().importOrganization(
                new File(GDriveConnectorIT.class.getResource("/ACME.xml").getFile()),
                OrganizationImportPolicy.IGNORE_DUPLICATES);
        client.logout();
    }

    @BeforeEach
    void login() {
        client = BonitaClient
                .builder(String.format("http://%s:%s/bonita",
                        BONITA_CONTAINER.getHost(),
                        BONITA_CONTAINER.getFirstMappedPort()))
                .build();
        client.login("install", "install");
    }

    @AfterEach
    void logout() {
        client.logout();
    }

    @Test
    void testGetFileConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("fileId", System.getenv("GDRIVE_TEST_FILE_ID"));

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultFileName", ConnectorTestToolkit.Output.create("fileName", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                GET_FILE_DEF_ID, GET_FILE_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");

        var fileName = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultFileName");
        assertThat(fileName).isNotEmpty();
    }

    @Test
    void testListFilesConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("maxResults", "5");

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultTotalCount", ConnectorTestToolkit.Output.create("totalCount", Integer.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                LIST_FILES_DEF_ID, LIST_FILES_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    @Test
    void testCreateFolderConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("folderName", "IT-TestFolder-" + System.currentTimeMillis());
        var parentFolderId = System.getenv("GDRIVE_TEST_FOLDER_ID");
        if (parentFolderId != null) {
            inputs.put("parentFolderId", parentFolderId);
        }

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultFolderId", ConnectorTestToolkit.Output.create("folderId", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                CREATE_FOLDER_DEF_ID, CREATE_FOLDER_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");

        var folderId = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultFolderId");
        assertThat(folderId).isNotEmpty();
    }

    @Test
    void testUploadFileConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("fileName", "IT-TestUpload-" + System.currentTimeMillis() + ".txt");
        inputs.put("fileContentBase64", java.util.Base64.getEncoder().encodeToString("Integration test content".getBytes()));
        inputs.put("mimeType", "text/plain");
        var parentFolderId = System.getenv("GDRIVE_TEST_FOLDER_ID");
        if (parentFolderId != null) {
            inputs.put("parentFolderId", parentFolderId);
        }

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultFileId", ConnectorTestToolkit.Output.create("fileId", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                UPLOAD_FILE_DEF_ID, UPLOAD_FILE_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    @Test
    void testDownloadFileConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("fileId", System.getenv("GDRIVE_TEST_FILE_ID"));

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                DOWNLOAD_FILE_DEF_ID, DOWNLOAD_FILE_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    private Map<String, String> commonInputs() {
        var inputs = new HashMap<String, String>();
        inputs.put("serviceAccountKeyJson", System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"));
        inputs.put("applicationName", "Bonita GDrive Connector IT");
        return inputs;
    }

    private Callable<String> pollInstanceState(String id) {
        return () -> {
            try {
                var instance = client.get(ProcessInstanceApi.class)
                        .getProcessInstanceById(id, (String) null);
                return instance.getState().name().toLowerCase();
            } catch (NotFoundException e) {
                return getCompletedProcess(id).getState().name().toLowerCase();
            }
        };
    }

    private ArchivedProcessInstance getCompletedProcess(String id) {
        var archivedInstances = client.get(ArchivedProcessInstanceApi.class)
                .searchArchivedProcessInstances(
                        new ArchivedProcessInstanceApi.SearchArchivedProcessInstancesQueryParams()
                                .c(1)
                                .p(0)
                                .f(List.of("caller=any", "sourceObjectId=" + id)));
        if (!archivedInstances.isEmpty()) {
            return archivedInstances.get(0);
        }
        return null;
    }
}
