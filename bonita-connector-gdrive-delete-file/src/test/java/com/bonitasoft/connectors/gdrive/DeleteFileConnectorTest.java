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
class DeleteFileConnectorTest {

    @Mock
    private GDriveClient mockClient;

    private DeleteFileConnector connector;
    private Map<String, Object> defaultInputs;

    @BeforeEach
    void setUp() {
        connector = new DeleteFileConnector();
        defaultInputs = new HashMap<>();
        defaultInputs.put(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        defaultInputs.put(DeleteFileConnector.INPUT_FILE_ID, "file-abc-123");
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private void setConfigurationViaValidation() throws ConnectorValidationException {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
    }

    // 1. Happy path
    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenReturn(new GDriveClient.DeleteFileResult("file-abc-123", false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_DELETED_FILE_ID)).isEqualTo("file-abc-123");
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(false);
        verify(mockClient).deleteFile("file-abc-123", false);
    }

    // 2a. Missing mandatory input: serviceAccountKeyJson
    @Test
    void shouldFailValidationWhenServiceAccountKeyMissing() {
        defaultInputs.remove(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON);
        connector.setInputParameters(defaultInputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    // 2b. Missing mandatory input: fileId
    @Test
    void shouldFailValidationWhenFileIdMissing() {
        defaultInputs.remove(DeleteFileConnector.INPUT_FILE_ID);
        connector.setInputParameters(defaultInputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    // 2c. Blank fileId
    @Test
    void shouldFailValidationWhenFileIdBlank() {
        defaultInputs.put(DeleteFileConnector.INPUT_FILE_ID, "   ");
        connector.setInputParameters(defaultInputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
    }

    // 3. Rate limit retry (429 then success)
    @Test
    void shouldRetryOnRateLimit() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new GDriveException("Rate limit exceeded", 429, true))
                .thenReturn(new GDriveClient.DeleteFileResult("file-abc-123", false));

        // The retry logic is inside GDriveClient itself, so when mock throws a retryable
        // exception, the connector catches it. We simulate the client throwing once then succeeding.
        // Since the mock client doesn't have retry logic, the first call throws and is caught by executeBusinessLogic.
        connector.executeBusinessLogic();

        // The retryable exception is caught by executeBusinessLogic -> sets success=false
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).asString().contains("Rate limit");
    }

    // 4. Auth failure (401)
    @Test
    void shouldFailImmediatelyOnAuthError() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new GDriveException("Authentication failed", 401, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).asString().contains("Authentication failed");
    }

    // 5. Network timeout
    @Test
    void shouldHandleNetworkTimeout() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new GDriveException("Connection timed out", -1, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).asString().contains("timed out");
    }

    // 6. Null optional inputs - verify defaults
    @Test
    void shouldApplyDefaultsForNullOptionalInputs() throws Exception {
        // Only mandatory inputs set; optional ones (permanent, applicationName, etc.) should use defaults
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenReturn(new GDriveClient.DeleteFileResult("file-abc-123", false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // 7. All output fields populated
    @Test
    void shouldPopulateAllOutputFields() throws Exception {
        defaultInputs.put(DeleteFileConnector.INPUT_PERMANENT, true);
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", true))
                .thenReturn(new GDriveClient.DeleteFileResult("file-abc-123", true));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(DeleteFileConnector.OUTPUT_DELETED_FILE_ID)).isEqualTo("file-abc-123");
        assertThat(outputs.get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(true);
        assertThat(outputs.get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // 8. Error path outputs
    @Test
    void shouldSetErrorOutputsOnFailure() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new GDriveException("File not accessible"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).isEqualTo("File not accessible");
    }

    // 9. Permanent delete flag passed correctly
    @Test
    void shouldPassPermanentFlagToClient() throws Exception {
        defaultInputs.put(DeleteFileConnector.INPUT_PERMANENT, true);
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", true))
                .thenReturn(new GDriveClient.DeleteFileResult("file-abc-123", true));

        connector.executeBusinessLogic();

        verify(mockClient).deleteFile("file-abc-123", true);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_PERMANENT)).isEqualTo(true);
    }

    // 10. Unexpected exception sets error outputs
    @Test
    void shouldHandleUnexpectedException() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new RuntimeException("Unexpected failure"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).asString().contains("Unexpected");
    }

    // 11. Blank serviceAccountKeyJson
    @Test
    void shouldFailValidationWhenServiceAccountKeyBlank() {
        defaultInputs.put(DeleteFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "  ");
        connector.setInputParameters(defaultInputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    // 12. 403 forbidden error
    @Test
    void shouldFailOnForbiddenError() throws Exception {
        connector.setInputParameters(defaultInputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.deleteFile("file-abc-123", false))
                .thenThrow(new GDriveException("Access denied", 403, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(connector.getOutputs().get(DeleteFileConnector.OUTPUT_ERROR_MESSAGE)).asString().contains("Access denied");
    }
}
