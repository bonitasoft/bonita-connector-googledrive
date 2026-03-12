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
class GetFileConnectorTest {

    @Mock
    private GDriveClient mockClient;

    private GetFileConnector connector;

    private static final String VALID_KEY_JSON = "{\"type\":\"service_account\",\"project_id\":\"test\"}";
    private static final String VALID_FILE_ID = "1abc2def3ghi";
    private static final String DEFAULT_FIELDS = "id,name,mimeType,webViewLink,webContentLink,size,createdTime,modifiedTime,owners,parents";

    @BeforeEach
    void setUp() {
        connector = new GetFileConnector();
    }

    private void setMandatoryInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        connector.setInputParameters(inputs);
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private GDriveClient.GetFileResult createSuccessResult() {
        return new GDriveClient.GetFileResult(
                VALID_FILE_ID,
                "test-document.pdf",
                "application/pdf",
                "https://drive.google.com/file/d/1abc2def3ghi/view",
                "https://drive.google.com/uc?id=1abc2def3ghi&export=download",
                2048L,
                "2025-01-15T10:30:00.000Z",
                "2025-06-20T14:45:00.000Z",
                "owner@example.com"
        );
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.GetFileResult result = createSuccessResult();
        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_FILE_ID)).isEqualTo(VALID_FILE_ID);
        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_FILE_NAME)).isEqualTo("test-document.pdf");
        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_MIME_TYPE)).isEqualTo("application/pdf");
        verify(mockClient).getFile(VALID_FILE_ID, DEFAULT_FIELDS);
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonBlank() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "   ");
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenFileIdMissing() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    void shouldFailValidationWhenFileIdBlank() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, "  ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    void shouldHandleGDriveExceptionAndSetErrorOutputs() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS))
                .thenThrow(new GDriveException("Google Drive API error 401 (unauthorized): Invalid credentials", 401, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(GetFileConnector.OUTPUT_ERROR_MESSAGE))
                .contains("401");
    }

    @Test
    void shouldHandleUnexpectedExceptionAndSetErrorOutputs() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS))
                .thenThrow(new RuntimeException("Connection reset"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(GetFileConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Unexpected error")
                .contains("Connection reset");
    }

    @Test
    void shouldHandleNetworkTimeout() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS))
                .thenThrow(new GDriveException("Get file failed: java.net.SocketTimeoutException: Read timed out"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(GetFileConnector.OUTPUT_ERROR_MESSAGE))
                .contains("timed out");
    }

    @Test
    void shouldApplyDefaultsForNullOptionalInputs() throws Exception {
        // Only set mandatory inputs; optional inputs are null
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.GetFileResult result = createSuccessResult();
        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        // The default fields value was used (verified by the mock matching DEFAULT_FIELDS)
        verify(mockClient).getFile(VALID_FILE_ID, DEFAULT_FIELDS);
    }

    @Test
    void shouldPopulateAllOutputFields() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.GetFileResult result = createSuccessResult();
        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS)).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(outputs.get(GetFileConnector.OUTPUT_FILE_ID)).isEqualTo(VALID_FILE_ID);
        assertThat(outputs.get(GetFileConnector.OUTPUT_FILE_NAME)).isEqualTo("test-document.pdf");
        assertThat(outputs.get(GetFileConnector.OUTPUT_MIME_TYPE)).isEqualTo("application/pdf");
        assertThat(outputs.get(GetFileConnector.OUTPUT_WEB_VIEW_LINK)).isEqualTo("https://drive.google.com/file/d/1abc2def3ghi/view");
        assertThat(outputs.get(GetFileConnector.OUTPUT_WEB_CONTENT_LINK)).isEqualTo("https://drive.google.com/uc?id=1abc2def3ghi&export=download");
        assertThat(outputs.get(GetFileConnector.OUTPUT_SIZE_BYTES)).isEqualTo(2048L);
        assertThat(outputs.get(GetFileConnector.OUTPUT_CREATED_TIME)).isEqualTo("2025-01-15T10:30:00.000Z");
        assertThat(outputs.get(GetFileConnector.OUTPUT_MODIFIED_TIME)).isEqualTo("2025-06-20T14:45:00.000Z");
        assertThat(outputs.get(GetFileConnector.OUTPUT_OWNER_EMAIL)).isEqualTo("owner@example.com");
        assertThat(outputs.get(GetFileConnector.OUTPUT_ERROR_MESSAGE)).isNull();
    }

    @Test
    void shouldUseCustomFieldsWhenProvided() throws Exception {
        String customFields = "id,name";
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        inputs.put(GetFileConnector.INPUT_FIELDS, customFields);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.GetFileResult result = new GDriveClient.GetFileResult(
                VALID_FILE_ID, "doc.pdf", null, null, null, 0L, null, null, null);
        when(mockClient.getFile(VALID_FILE_ID, customFields)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        verify(mockClient).getFile(VALID_FILE_ID, customFields);
    }

    @Test
    void shouldUseCustomApplicationName() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GetFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(GetFileConnector.INPUT_FILE_ID, VALID_FILE_ID);
        inputs.put(GetFileConnector.INPUT_APPLICATION_NAME, "My-Custom-App");
        inputs.put(GetFileConnector.INPUT_CONNECT_TIMEOUT, 5000);
        inputs.put(GetFileConnector.INPUT_READ_TIMEOUT, 10000);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Verify configuration was built with custom values by checking the configuration field
        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("My-Custom-App");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getReadTimeout()).isEqualTo(10000);
    }

    @Test
    void shouldHandleFileNotFound() throws Exception {
        setMandatoryInputs();
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.getFile(VALID_FILE_ID, DEFAULT_FIELDS))
                .thenThrow(new GDriveException("Google Drive API error 404 (notFound): File not found: " + VALID_FILE_ID, 404, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(GetFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(GetFileConnector.OUTPUT_ERROR_MESSAGE))
                .contains("404")
                .contains("notFound");
    }
}
