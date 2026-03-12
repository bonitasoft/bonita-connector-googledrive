package com.bonitasoft.connectors.gdrive;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadFileConnectorTest {

    @Mock
    private GDriveClient mockClient;

    private UploadFileConnector connector;

    private static final String VALID_KEY_JSON = "{\"type\":\"service_account\",\"project_id\":\"test\"}";
    private static final String VALID_FILE_NAME = "test-file.pdf";
    private static final String VALID_CONTENT_BASE64 = Base64.getEncoder().encodeToString("hello world".getBytes());
    private static final String VALID_MIME_TYPE = "application/pdf";

    @BeforeEach
    void setUp() {
        connector = new UploadFileConnector();
    }

    private Map<String, Object> buildValidInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, VALID_FILE_NAME);
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, VALID_CONTENT_BASE64);
        inputs.put(UploadFileConnector.INPUT_MIME_TYPE, VALID_MIME_TYPE);
        inputs.put(UploadFileConnector.INPUT_PARENT_FOLDER_ID, "folder123");
        inputs.put(UploadFileConnector.INPUT_UPLOAD_STRATEGY, "AUTO");
        inputs.put(UploadFileConnector.INPUT_DESCRIPTION, "A test file");
        return inputs;
    }

    private void injectMockClient(UploadFileConnector conn) throws Exception {
        Field clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(conn, mockClient);
    }

    @Test
    @DisplayName("Validation succeeds with all mandatory inputs")
    void validationSucceedsWithValidInputs() throws ConnectorValidationException {
        connector.setInputParameters(buildValidInputs());
        connector.validateInputParameters();
    }

    @Test
    @DisplayName("Validation fails when serviceAccountKeyJson is missing")
    void validationFailsWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = buildValidInputs();
        inputs.remove(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    @DisplayName("Validation fails when fileName is missing")
    void validationFailsWhenFileNameMissing() {
        Map<String, Object> inputs = buildValidInputs();
        inputs.remove(UploadFileConnector.INPUT_FILE_NAME);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName");
    }

    @Test
    @DisplayName("Validation fails when fileName is blank")
    void validationFailsWhenFileNameBlank() {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, "   ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileName");
    }

    @Test
    @DisplayName("Validation fails when fileContentBase64 is missing")
    void validationFailsWhenFileContentBase64Missing() {
        Map<String, Object> inputs = buildValidInputs();
        inputs.remove(UploadFileConnector.INPUT_FILE_CONTENT_BASE64);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileContentBase64");
    }

    @Test
    @DisplayName("Validation fails when fileContentBase64 is blank")
    void validationFailsWhenFileContentBase64Blank() {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, "");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileContentBase64");
    }

    @Test
    @DisplayName("doExecute uploads file and sets all outputs")
    void doExecuteUploadsFileAndSetsOutputs() throws Exception {
        connector.setInputParameters(buildValidInputs());
        connector.validateInputParameters();
        injectMockClient(connector);

        GDriveClient.UploadFileResult uploadResult = new GDriveClient.UploadFileResult(
                "file-id-123",
                "https://drive.google.com/file/d/file-id-123/view",
                "https://drive.google.com/uc?id=file-id-123"
        );

        when(mockClient.uploadFile(
                eq(VALID_FILE_NAME),
                any(byte[].class),
                eq(VALID_MIME_TYPE),
                eq("folder123"),
                eq("AUTO"),
                eq("A test file")
        )).thenReturn(uploadResult);

        connector.doExecute();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_ID)).isEqualTo("file-id-123");
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_VIEW_LINK))
                .isEqualTo("https://drive.google.com/file/d/file-id-123/view");
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_CONTENT_LINK))
                .isEqualTo("https://drive.google.com/uc?id=file-id-123");

        verify(mockClient).uploadFile(
                eq(VALID_FILE_NAME),
                argThat(bytes -> new String(bytes).equals("hello world")),
                eq(VALID_MIME_TYPE),
                eq("folder123"),
                eq("AUTO"),
                eq("A test file")
        );
    }

    @Test
    @DisplayName("doExecute throws GDriveException on invalid base64 content")
    void doExecuteThrowsOnInvalidBase64() throws Exception {
        Map<String, Object> inputs = buildValidInputs();
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, "!!!not-valid-base64!!!");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient(connector);

        assertThatThrownBy(() -> connector.doExecute())
                .isInstanceOf(GDriveException.class)
                .hasMessageContaining("Invalid base64 content");
    }

    @Test
    @DisplayName("doExecute propagates GDriveException from client")
    void doExecutePropagatesGDriveException() throws Exception {
        connector.setInputParameters(buildValidInputs());
        connector.validateInputParameters();
        injectMockClient(connector);

        when(mockClient.uploadFile(anyString(), any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new GDriveException("API quota exceeded", 429, true));

        assertThatThrownBy(() -> connector.doExecute())
                .isInstanceOf(GDriveException.class)
                .hasMessageContaining("API quota exceeded");
    }

    @Test
    @DisplayName("executeBusinessLogic sets success=false and errorMessage on GDriveException")
    void executeBusinessLogicSetsErrorOutputsOnFailure() throws Exception {
        connector.setInputParameters(buildValidInputs());
        connector.validateInputParameters();
        injectMockClient(connector);

        when(mockClient.uploadFile(anyString(), any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new GDriveException("Upload failed: network error"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Upload failed: network error");
    }

    @Test
    @DisplayName("executeBusinessLogic sets success=true on successful upload")
    void executeBusinessLogicSetsSuccessOnSuccess() throws Exception {
        connector.setInputParameters(buildValidInputs());
        connector.validateInputParameters();
        injectMockClient(connector);

        GDriveClient.UploadFileResult uploadResult = new GDriveClient.UploadFileResult(
                "file-id-456", "https://link.view", "https://link.content"
        );
        when(mockClient.uploadFile(anyString(), any(byte[].class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(uploadResult);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    @Test
    @DisplayName("Default values are applied when optional inputs are missing")
    void defaultValuesAppliedForOptionalInputs() throws ConnectorValidationException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, VALID_FILE_NAME);
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, VALID_CONTENT_BASE64);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Access the built configuration via reflection
        try {
            Field configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

            assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
            assertThat(config.getConnectTimeout()).isEqualTo(30000);
            assertThat(config.getReadTimeout()).isEqualTo(60000);
            assertThat(config.getMimeType()).isEqualTo("application/octet-stream");
            assertThat(config.getUploadStrategy()).isEqualTo("AUTO");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("doExecute passes null parentFolderId and description when not set")
    void doExecuteHandlesNullOptionalFields() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(UploadFileConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, VALID_KEY_JSON);
        inputs.put(UploadFileConnector.INPUT_FILE_NAME, VALID_FILE_NAME);
        inputs.put(UploadFileConnector.INPUT_FILE_CONTENT_BASE64, VALID_CONTENT_BASE64);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient(connector);

        GDriveClient.UploadFileResult uploadResult = new GDriveClient.UploadFileResult(
                "file-id-789", null, null
        );
        when(mockClient.uploadFile(anyString(), any(byte[].class), anyString(), isNull(), eq("AUTO"), isNull()))
                .thenReturn(uploadResult);

        connector.doExecute();

        verify(mockClient).uploadFile(
                eq(VALID_FILE_NAME),
                any(byte[].class),
                eq("application/octet-stream"),
                isNull(),
                eq("AUTO"),
                isNull()
        );

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_ID)).isEqualTo("file-id-789");
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_VIEW_LINK)).isNull();
        assertThat(outputs.get(UploadFileConnector.OUTPUT_FILE_WEB_CONTENT_LINK)).isNull();
    }
}
