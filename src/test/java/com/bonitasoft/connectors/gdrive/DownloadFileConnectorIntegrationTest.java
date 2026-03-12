package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Base64;
import java.util.Map;

@EnabledIfEnvironmentVariable(named = "GDRIVE_SERVICE_ACCOUNT_KEY_JSON", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GDRIVE_IMPERSONATED_USER_EMAIL", matches = ".+")
class DownloadFileConnectorIntegrationTest {

    private GDriveClient client;
    private String testFileId;

    @BeforeEach
    void setUp() throws Exception {
        GDriveConfiguration config = GDriveConfiguration.builder()
                .serviceAccountKeyJson(System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"))
                .impersonatedUserEmail(System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"))
                .build();
        client = new GDriveClient(config);

        // Upload a test file
        GDriveClient.UploadFileResult result = client.uploadFile(
                "integration-test-download-" + System.currentTimeMillis() + ".txt",
                "Hello integration test download".getBytes(),
                "text/plain", null, "AUTO", null);
        testFileId = result.fileId();
    }

    @AfterEach
    void tearDown() {
        if (testFileId != null) {
            try { client.deleteFile(testFileId, true); } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldDownloadUploadedFile() throws Exception {
        DownloadFileConnector connector = new DownloadFileConnector();
        connector.setInputParameters(Map.of(
                DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, System.getenv("GDRIVE_SERVICE_ACCOUNT_KEY_JSON"),
                DownloadFileConnector.INPUT_IMPERSONATED_USER_EMAIL, System.getenv("GDRIVE_IMPERSONATED_USER_EMAIL"),
                DownloadFileConnector.INPUT_FILE_ID, testFileId
        ));
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat((String) outputs.get(DownloadFileConnector.OUTPUT_FILE_CONTENT_BASE64)).isNotBlank();
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_FILE_NAME)).isNotNull();
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_MIME_TYPE)).isNotNull();
        assertThat((Long) outputs.get(DownloadFileConnector.OUTPUT_FILE_SIZE_BYTES)).isGreaterThan(0L);

        byte[] downloaded = Base64.getDecoder().decode((String) outputs.get(DownloadFileConnector.OUTPUT_FILE_CONTENT_BASE64));
        assertThat(new String(downloaded)).isEqualTo("Hello integration test download");
    }
}
