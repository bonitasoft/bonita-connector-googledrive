package com.bonitasoft.connectors.gdrive;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class UploadFileConnectorIntegrationTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetMappings() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("Integration: full upload flow with mocked Google Drive API")
    void fullUploadFlowWithMockedApi() throws Exception {
        // Stub the upload endpoint
        stubFor(post(urlPathEqualTo("/upload/drive/v3/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": "integration-file-id",
                                    "webViewLink": "https://drive.google.com/file/d/integration-file-id/view",
                                    "webContentLink": "https://drive.google.com/uc?id=integration-file-id"
                                }
                                """)));

        // Create a mock GDriveClient that returns a canned result
        GDriveClient.UploadFileResult mockResult = new GDriveClient.UploadFileResult(
                "integration-file-id",
                "https://drive.google.com/file/d/integration-file-id/view",
                "https://drive.google.com/uc?id=integration-file-id"
        );

        GDriveClient mockClient = org.mockito.Mockito.mock(GDriveClient.class);
        org.mockito.Mockito.when(mockClient.uploadFile(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(mockResult);

        UploadFileConnector connector = new UploadFileConnector();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON,
                "{\"type\":\"service_account\",\"project_id\":\"integration-test\"}");
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, "integration-test.txt");
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64,
                Base64.getEncoder().encodeToString("integration test content".getBytes()));
        inputs.put(UploadFileConnector.INPUT_MIME_TYPE, "text/plain");
        inputs.put(UploadFileConnector.INPUT_PARENT_FOLDER_ID, "parent-folder-id");
        inputs.put(UploadFileConnector.INPUT_UPLOAD_STRATEGY, "SIMPLE");
        inputs.put(UploadFileConnector.INPUT_DESCRIPTION, "Integration test upload");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Inject mock client via reflection
        Field clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_ID)).isEqualTo("integration-file-id");
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_VIEW_LINK))
                .isEqualTo("https://drive.google.com/file/d/integration-file-id/view");
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_CONTENT_LINK))
                .isEqualTo("https://drive.google.com/uc?id=integration-file-id");
    }

    @Test
    @DisplayName("Integration: upload failure sets error outputs correctly")
    void uploadFailureSetsErrorOutputs() throws Exception {
        GDriveClient mockClient = org.mockito.Mockito.mock(GDriveClient.class);
        org.mockito.Mockito.when(mockClient.uploadFile(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenThrow(new GDriveException("Google Drive API error 403 (rateLimitExceeded): Rate limit exceeded",
                403, true));

        UploadFileConnector connector = new UploadFileConnector();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON,
                "{\"type\":\"service_account\",\"project_id\":\"integration-test\"}");
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, "fail-test.txt");
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64,
                Base64.getEncoder().encodeToString("fail content".getBytes()));
        inputs.put(UploadFileConnector.INPUT_MIME_TYPE, "text/plain");
        inputs.put(UploadFileConnector.INPUT_PARENT_FOLDER_ID, "parent-folder-id");
        inputs.put(UploadFileConnector.INPUT_UPLOAD_STRATEGY, "AUTO");
        inputs.put(UploadFileConnector.INPUT_DESCRIPTION, "Should fail");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        Field clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("rateLimitExceeded");
    }

    @Test
    @DisplayName("Integration: validation rejects missing mandatory fields end-to-end")
    void validationRejectsMissingMandatoryFields() {
        UploadFileConnector connector = new UploadFileConnector();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON,
                "{\"type\":\"service_account\",\"project_id\":\"test\"}");
        // Missing fileName and fileContentBase64
        connector.setInputParameters(inputs);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.bonitasoft.engine.connector.ConnectorValidationException.class,
                connector::validateInputParameters
        );
    }
}
