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
class CreateFolderConnectorTest {

    @Mock
    private GDriveClient mockClient;

    private CreateFolderConnector connector;

    @BeforeEach
    void setUp() {
        connector = new CreateFolderConnector();
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "My Folder");
        return inputs;
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_PARENT_FOLDER_ID, "parent123");
        inputs.put(CreateFolderConnector.INPUT_DESCRIPTION, "A test folder");
        connector.setInputParameters(inputs);

        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.CreateFolderResult expectedResult =
                new GDriveClient.CreateFolderResult("folder-abc", "https://drive.google.com/folders/folder-abc");
        when(mockClient.createFolder("My Folder", "parent123", "A test folder"))
                .thenReturn(expectedResult);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(CreateFolderConnector.OUTPUT_FOLDER_ID)).isEqualTo("folder-abc");
        assertThat(outputs.get(CreateFolderConnector.OUTPUT_FOLDER_WEB_VIEW_LINK))
                .isEqualTo("https://drive.google.com/folders/folder-abc");
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "   ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    @Test
    void shouldFailValidationWhenFolderNameMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove(CreateFolderConnector.INPUT_FOLDER_NAME);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("folderName");
    }

    @Test
    void shouldFailValidationWhenFolderNameBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_FOLDER_NAME, "   ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("folderName");
    }

    @Test
    void shouldHandleRateLimitExceptionFromClient() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveException rateLimitEx = new GDriveException("Rate limit exceeded", 429, true);
        when(mockClient.createFolder("My Folder", null, null))
                .thenThrow(rateLimitEx);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE)).contains("Rate limit");
    }

    @Test
    void shouldFailImmediatelyOnAuthError() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveException authEx = new GDriveException("Authentication failed", 401, false);
        when(mockClient.createFolder("My Folder", null, null))
                .thenThrow(authEx);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Authentication failed");
        verify(mockClient, times(1)).createFolder("My Folder", null, null);
    }

    @Test
    void shouldHandleNetworkTimeout() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveException timeoutEx = new GDriveException("Connection timed out", -1, false);
        when(mockClient.createFolder("My Folder", null, null))
                .thenThrow(timeoutEx);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Connection timed out");
    }

    @Test
    void shouldApplyDefaultsForNullOptionalInputs() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.CreateFolderResult expectedResult =
                new GDriveClient.CreateFolderResult("folder-defaults", "https://drive.google.com/folders/folder-defaults");
        when(mockClient.createFolder("My Folder", null, null))
                .thenReturn(expectedResult);

        connector.executeBusinessLogic();

        // Verify call was made with null optional params
        verify(mockClient).createFolder("My Folder", null, null);

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(outputs.get(CreateFolderConnector.OUTPUT_FOLDER_ID)).isEqualTo("folder-defaults");
    }

    @Test
    void shouldPopulateAllOutputFields() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_PARENT_FOLDER_ID, "parentXYZ");
        inputs.put(CreateFolderConnector.INPUT_DESCRIPTION, "Full output test");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.CreateFolderResult expectedResult =
                new GDriveClient.CreateFolderResult("folder-full", "https://drive.google.com/folders/folder-full");
        when(mockClient.createFolder("My Folder", "parentXYZ", "Full output test"))
                .thenReturn(expectedResult);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs).containsKeys(
                CreateFolderConnector.OUTPUT_FOLDER_ID,
                CreateFolderConnector.OUTPUT_FOLDER_WEB_VIEW_LINK,
                AbstractGDriveConnector.OUTPUT_SUCCESS
        );
        assertThat(outputs.get(CreateFolderConnector.OUTPUT_FOLDER_ID)).isNotNull();
        assertThat(outputs.get(CreateFolderConnector.OUTPUT_FOLDER_WEB_VIEW_LINK)).isNotNull();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    @Test
    void shouldSetErrorOutputsOnFailure() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveException serverError = new GDriveException("Internal server error", 500, false);
        when(mockClient.createFolder("My Folder", null, null))
                .thenThrow(serverError);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE)).isNotNull();
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Internal server error");
    }

    @Test
    void shouldHandleUnexpectedExceptionGracefully() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.createFolder("My Folder", null, null))
                .thenThrow(new RuntimeException("Something unexpected"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) outputs.get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Unexpected error");
    }

    @Test
    void shouldPassDescriptionToClient() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_DESCRIPTION, "Project documents folder");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.CreateFolderResult expectedResult =
                new GDriveClient.CreateFolderResult("folder-desc", "https://drive.google.com/folders/folder-desc");
        when(mockClient.createFolder("My Folder", null, "Project documents folder"))
                .thenReturn(expectedResult);

        connector.executeBusinessLogic();

        verify(mockClient).createFolder("My Folder", null, "Project documents folder");
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    @Test
    void shouldUseDefaultApplicationName() throws Exception {
        Map<String, Object> inputs = validInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Verify configuration built with default application name
        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
        assertThat(config.getConnectTimeout()).isEqualTo(30000);
        assertThat(config.getReadTimeout()).isEqualTo(60000);
    }

    @Test
    void shouldUseCustomApplicationName() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put(CreateFolderConnector.INPUT_APPLICATION_NAME, "CustomApp");
        inputs.put(CreateFolderConnector.INPUT_CONNECT_TIMEOUT, 15000);
        inputs.put(CreateFolderConnector.INPUT_READ_TIMEOUT, 45000);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("CustomApp");
        assertThat(config.getConnectTimeout()).isEqualTo(15000);
        assertThat(config.getReadTimeout()).isEqualTo(45000);
    }
}
