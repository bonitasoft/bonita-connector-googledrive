package com.bonitasoft.connectors.gdrive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class GetFileConnectorIntegrationTest {

    private GDriveClient client;
    private String testFileId;

    @BeforeEach
    void setUp() throws Exception {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"))
                .impersonatedUserEmail(System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"))
                .build();
        client = new GDriveClient(config);

        GDriveClient.UploadFileResult result = client.uploadFile(
                "integration-test-getfile-" + System.currentTimeMillis() + ".txt",
                "test content".getBytes(), "text/plain", null, "AUTO", null);
        testFileId = result.fileId();
    }

    @AfterEach
    void tearDown() {
        if (testFileId != null) {
            try { client.deleteFile(testFileId, true); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldGetFileMetadata() throws Exception {
        GetFileConnector connector = new GetFileConnector();
        connector.setInputParameters(Map.of(
                GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                GetFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                GetFileConnector.INPUT_FILE_ID, testFileId
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(outputs.get(GetFileConnector.OUTPUT_FILE_ID)).isEqualTo(testFileId);
        assertThat(outputs.get(GetFileConnector.OUTPUT_FILE_NAME)).isNotNull();
        assertThat(outputs.get(GetFileConnector.OUTPUT_MIME_TYPE)).isEqualTo("text/plain");
    }

    @Test
    void shouldGetFileWithCustomFields() throws Exception {
        GetFileConnector connector = new GetFileConnector();
        connector.setInputParameters(Map.of(
                GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                GetFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                GetFileConnector.INPUT_FILE_ID, testFileId
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_FILE_ID)).isEqualTo(testFileId);
    }

    @Test
    void shouldFailForNonExistentFile() throws Exception {
        GetFileConnector connector = new GetFileConnector();
        connector.setInputParameters(Map.of(
                GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                GetFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                GetFileConnector.INPUT_FILE_ID, "nonexistent-file-id-12345"
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE)).isNotNull();
    }
}
