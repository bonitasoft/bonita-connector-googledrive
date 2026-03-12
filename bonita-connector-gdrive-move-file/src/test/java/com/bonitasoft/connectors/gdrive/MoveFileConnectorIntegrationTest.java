package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class MoveFileConnectorIntegrationTest {

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
    void shouldRenameFile() throws Exception {
        GDriveClient.UploadFileResult file = client.uploadFile(
                "move-test-rename-" + System.currentTimeMillis() + ".txt",
                "rename test".getBytes(), "text/plain", null, null, null);
        createdIds.add(file.fileId());

        String newName = "renamed-" + System.currentTimeMillis() + ".txt";
        MoveFileConnector connector = new MoveFileConnector();
        connector.setInputParameters(Map.of(
                MoveFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                MoveFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                MoveFileConnector.INPUT_FILE_ID, file.fileId(),
                MoveFileConnector.INPUT_NEW_NAME, newName
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(MoveFileConnector.OUTPUT_FILE_NAME)).isEqualTo(newName);
    }

    @Test
    void shouldMoveFileToNewFolder() throws Exception {
        GDriveClient.UploadFileResult file = client.uploadFile(
                "move-test-file-" + System.currentTimeMillis() + ".txt",
                "move test".getBytes(), "text/plain", null, null, null);
        createdIds.add(file.fileId());

        GDriveClient.CreateFolderResult folder = client.createFolder(
                "move-test-target-" + System.currentTimeMillis(), null, null);
        createdIds.add(folder.folderId());

        MoveFileConnector connector = new MoveFileConnector();
        connector.setInputParameters(Map.of(
                MoveFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                MoveFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                MoveFileConnector.INPUT_FILE_ID, file.fileId(),
                MoveFileConnector.INPUT_NEW_PARENT_FOLDER_ID, folder.folderId(),
                MoveFileConnector.INPUT_REMOVE_FROM_CURRENT_PARENTS, true
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(MoveFileConnector.OUTPUT_NEW_PARENT_FOLDER_ID)).isEqualTo(folder.folderId());
    }

    @Test
    void shouldFailWithInvalidFileId() throws Exception {
        MoveFileConnector connector = new MoveFileConnector();
        connector.setInputParameters(Map.of(
                MoveFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                MoveFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                MoveFileConnector.INPUT_FILE_ID, "nonexistent-file-id-999",
                MoveFileConnector.INPUT_NEW_NAME, "should-not-work"
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE)).isNotNull();
    }
}
