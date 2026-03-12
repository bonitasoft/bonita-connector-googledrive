package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class DeleteFileConnectorIntegrationTest {

    private GDriveClient client;

    @BeforeEach
    void setUp() throws Exception {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"))
                .impersonatedUserEmail(System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"))
                .build();
        client = new GDriveClient(config);
    }

    @Test
    void shouldTrashFile() throws Exception {
        GDriveClient.UploadFileResult file = client.uploadFile(
                "delete-test-trash-" + System.currentTimeMillis() + ".txt",
                "trash test".getBytes(), "text/plain", null, null, null);

        DeleteFileConnector connector = new DeleteFileConnector();
        connector.setInputParameters(Map.of(
                DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                DeleteFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                DeleteFileConnector.INPUT_FILE_ID, file.fileId(),
                DeleteFileConnector.INPUT_PERMANENT, false
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_DELETED_FILE_ID)).isEqualTo(file.fileId());
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(false);

        // Cleanup permanently
        try { client.deleteFile(file.fileId(), true); } catch (Exception ignored) {}
    }

    @Test
    void shouldPermanentlyDeleteFile() throws Exception {
        GDriveClient.UploadFileResult file = client.uploadFile(
                "delete-test-permanent-" + System.currentTimeMillis() + ".txt",
                "permanent delete test".getBytes(), "text/plain", null, null, null);

        DeleteFileConnector connector = new DeleteFileConnector();
        connector.setInputParameters(Map.of(
                DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                DeleteFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                DeleteFileConnector.INPUT_FILE_ID, file.fileId(),
                DeleteFileConnector.INPUT_PERMANENT, true
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_DELETED_FILE_ID)).isEqualTo(file.fileId());
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(true);
    }
}
