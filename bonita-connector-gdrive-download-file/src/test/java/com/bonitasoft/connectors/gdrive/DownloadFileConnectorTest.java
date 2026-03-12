package com.bonitasoft.connectors.gdrive;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class DownloadFileConnectorTest {

    private static final String VALID_SERVICE_ACCOUNT_JSON = "{\"type\":\"service_account\",\"project_id\":\"test\"}";
    private static final String VALID_FILE_ID = "1AbCdEfGhIjKlMnOpQrStUvWxYz";
    private static final String EXPORT_MIME_PDF = "application/pdf";

    @Mock
    private GDriveClient mockClient;

    private DownloadFileConnector connector;

    @BeforeEach
    void setUp() {
        connector = new DownloadFileConnector();
    }

    private void setInputs(Map<String, Object> inputs) {
        connector.setInputParameters(inputs);
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private void injectConfiguration() throws Exception {
        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        configField.set(connector, GDriveConfiguration.builder()
                .serviceAccountKeyJson(VALID_SERVICE_ACCOUNT_JSON)
                .fileId(VALID_FILE_ID)
                .build());
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_SERVICE_ACCOUNT_JSON);
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        return inputs;
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.DownloadFileResult expectedResult = new GDriveClient.DownloadFileResult(
                "dGVzdCBjb250ZW50", "report.pdf", "application/pdf", 12L
        );
        when(mockClient.downloadFile(VALID_FILE_ID, null)).thenReturn(expectedResult);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DownloadFileConnector.OUTPUT_FILE_CONTENT_BASE64))
                .isEqualTo("dGVzdCBjb250ZW50");
        assertThat(connector.getOutputs().get(DownloadFileConnector.OUTPUT_FILE_NAME))
                .isEqualTo("report.pdf");
        assertThat(connector.getOutputs().get(DownloadFileConnector.OUTPUT_MIME_TYPE))
                .isEqualTo("application/pdf");
        assertThat(connector.getOutputs().get(DownloadFileConnector.OUTPUT_FILE_SIZE_BYTES))
                .isEqualTo(12L);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(true);
    }

    @Test
    void shouldExecuteWithExportMimeType() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_EXPORT_MIME_TYPE, EXPORT_MIME_PDF);
        setInputs(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.DownloadFileResult expectedResult = new GDriveClient.DownloadFileResult(
                "cGRmIGNvbnRlbnQ=", "document.pdf", EXPORT_MIME_PDF, 2048L
        );
        when(mockClient.downloadFile(VALID_FILE_ID, EXPORT_MIME_PDF)).thenReturn(expectedResult);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DownloadFileConnector.OUTPUT_MIME_TYPE))
                .isEqualTo(EXPORT_MIME_PDF);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(true);
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON);
        setInputs(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "   ");
        setInputs(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenFileIdMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove(DownloadFileConnector.INPUT_FILE_ID);
        setInputs(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    void shouldFailValidationWhenFileIdBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_FILE_ID, "  ");
        setInputs(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    void shouldFailImmediatelyOnAuthError() throws Exception {
        injectConfiguration();
        injectMockClient();

        when(mockClient.downloadFile(anyString(), any()))
                .thenThrow(new GDriveException("Google Drive API error 401 (unauthorized): Invalid credentials", 401, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("401");
    }

    @Test
    void shouldHandleNetworkTimeout() throws Exception {
        injectConfiguration();
        injectMockClient();

        when(mockClient.downloadFile(anyString(), any()))
                .thenThrow(new GDriveException("Download failed: Read timed out"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("timed out");
    }

    @Test
    void shouldApplyDefaultsForNullOptionalInputs() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
        assertThat(config.getConnectTimeout()).isEqualTo(30000);
        assertThat(config.getReadTimeout()).isEqualTo(60000);
        assertThat(config.getExportMimeType()).isNull();
        assertThat(config.getImpersonatedUserEmail()).isNull();
    }

    @Test
    void shouldPopulateAllOutputFields() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.DownloadFileResult expectedResult = new GDriveClient.DownloadFileResult(
                "YWJj", "test.txt", "text/plain", 3L
        );
        when(mockClient.downloadFile(VALID_FILE_ID, null)).thenReturn(expectedResult);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs).containsKeys(
                DownloadFileConnector.OUTPUT_FILE_CONTENT_BASE64,
                DownloadFileConnector.OUTPUT_FILE_NAME,
                DownloadFileConnector.OUTPUT_MIME_TYPE,
                DownloadFileConnector.OUTPUT_FILE_SIZE_BYTES,
                AbstractGDriveConnector.OUTPUT_SUCCESS
        );
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_FILE_CONTENT_BASE64)).isNotNull();
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_FILE_NAME)).isNotNull();
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_MIME_TYPE)).isNotNull();
        assertThat(outputs.get(DownloadFileConnector.OUTPUT_FILE_SIZE_BYTES)).isNotNull();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    @Test
    void shouldSetErrorOutputsOnFailure() throws Exception {
        injectConfiguration();
        injectMockClient();

        when(mockClient.downloadFile(anyString(), any()))
                .thenThrow(new GDriveException("File not found", 404, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(false);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .isNotNull();
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("File not found");
    }

    @Test
    void shouldHandleForbiddenError() throws Exception {
        injectConfiguration();
        injectMockClient();

        when(mockClient.downloadFile(anyString(), any()))
                .thenThrow(new GDriveException("Google Drive API error 403 (forbidden): Access denied", 403, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("403");
    }

    @Test
    void shouldHandleExportSizeLimitExceeded() throws Exception {
        injectConfiguration();
        injectMockClient();

        when(mockClient.downloadFile(anyString(), any()))
                .thenThrow(new GDriveException("Export exceeds 10 MB limit", 413, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS))
                .isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("10 MB");
    }

    @Test
    void shouldApplyCustomApplicationName() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(DownloadFileConnector.INPUT_APPLICATION_NAME, "MyCustomApp");
        inputs.put(DownloadFileConnector.INPUT_CONNECT_TIMEOUT, 5000);
        inputs.put(DownloadFileConnector.INPUT_READ_TIMEOUT, 10000);
        setInputs(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("MyCustomApp");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getReadTimeout()).isEqualTo(10000);
    }
}
