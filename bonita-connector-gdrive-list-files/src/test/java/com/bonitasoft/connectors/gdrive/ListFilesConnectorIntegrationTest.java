package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class ListFilesConnectorIntegrationTest {

    private GDriveClient client;
    private final List<String> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"))
                .impersonatedUserEmail(System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"))
                .build();
        client = new GDriveClient(config);
    }

    @AfterEach
    void tearDown() {
        for (String id : createdIds) {
            try { client.deleteFile(id, true); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldListFilesFromRoot() throws Exception {
        ListFilesConnector connector = new ListFilesConnector();
        connector.setInputParameters(Map.of(
                ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                ListFilesConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                ListFilesConnector.INPUT_MAX_RESULTS, 10
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_FILES)).isNotNull();
    }

    @Test
    void shouldListFilesInCreatedFolder() throws Exception {
        // Create a folder and upload 2 files into it
        GDriveClient.CreateFolderResult folder = client.createFolder(
                "integration-test-list-" + System.currentTimeMillis(), null, null);
        createdIds.add(folder.folderId());

        client.uploadFile("listtest-1.txt", "data1".getBytes(), "text/plain", folder.folderId(), null, null);
        client.uploadFile("listtest-2.txt", "data2".getBytes(), "text/plain", folder.folderId(), null, null);

        Thread.sleep(1000); // Drive indexing delay

        ListFilesConnector connector = new ListFilesConnector();
        connector.setInputParameters(Map.of(
                ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                ListFilesConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                ListFilesConnector.INPUT_PARENT_FOLDER_ID, folder.folderId(),
                ListFilesConnector.INPUT_MAX_RESULTS, 50
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> files = (List<Map<String, String>>) connector.getOutputs().get(ListFilesConnector.OUTPUT_FILES);
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldListOnlyFolders() throws Exception {
        ListFilesConnector connector = new ListFilesConnector();
        connector.setInputParameters(Map.of(
                ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                ListFilesConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                ListFilesConnector.INPUT_INCLUDE_FILES, false,
                ListFilesConnector.INPUT_INCLUDE_FOLDERS, true,
                ListFilesConnector.INPUT_MAX_RESULTS, 10
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }
}
