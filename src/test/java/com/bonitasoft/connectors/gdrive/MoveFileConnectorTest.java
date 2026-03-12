package com.bonitasoft.connectors.gdrive;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoveFileConnectorTest {

    @Mock
    private GDriveClient mockClient;

    private MoveFileConnector connector;

    @BeforeEach
    void setUp() {
        connector = new MoveFileConnector();
    }

    private void injectMockClient() throws Exception {
        Field clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("serviceAccountKeyJson", "{\"type\":\"service_account\"}");
        inputs.put("fileId", "file-123");
        inputs.put("newParentFolderId", "folder-456");
        inputs.put("newName", "renamed.txt");
        inputs.put("removeFromCurrentParents", true);
        return inputs;
    }

    // --- Test 1: Happy path — move and rename ---
    @Test
    void shouldMoveAndRenameFileSuccessfully() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "renamed.txt", "folder-456", "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", "folder-456", "renamed.txt", true)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("fileId")).isEqualTo("file-123");
        assertThat(connector.getOutputs().get("fileName")).isEqualTo("renamed.txt");
        assertThat(connector.getOutputs().get("newParentFolderId")).isEqualTo("folder-456");
        assertThat(connector.getOutputs().get("webViewLink")).isEqualTo("https://drive.google.com/file/d/file-123/view");
        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
    }

    // --- Test 2: Missing serviceAccountKeyJson ---
    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("serviceAccountKeyJson");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson is mandatory");
    }

    // --- Test 3: Missing fileId ---
    @Test
    void shouldFailValidationWhenFileIdMissing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("fileId");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId is mandatory");
    }

    // --- Test 4: Blank fileId ---
    @Test
    void shouldFailValidationWhenFileIdBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put("fileId", "   ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId is mandatory");
    }

    // --- Test 5: Neither newParentFolderId nor newName provided ---
    @Test
    void shouldFailValidationWhenNeitherNewParentNorNewNameProvided() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("newParentFolderId");
        inputs.remove("newName");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("At least one of newParentFolderId or newName must be provided");
    }

    // --- Test 6: Only newName provided (rename only) ---
    @Test
    void shouldPassValidationWithOnlyNewName() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.remove("newParentFolderId");
        connector.setInputParameters(inputs);
        injectMockClient();

        connector.validateInputParameters();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "renamed.txt", null, "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", null, "renamed.txt", true)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
        assertThat(connector.getOutputs().get("fileName")).isEqualTo("renamed.txt");
    }

    // --- Test 7: Only newParentFolderId provided (move only) ---
    @Test
    void shouldPassValidationWithOnlyNewParentFolderId() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.remove("newName");
        connector.setInputParameters(inputs);
        injectMockClient();

        connector.validateInputParameters();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "original.txt", "folder-456", "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", "folder-456", null, true)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
        assertThat(connector.getOutputs().get("newParentFolderId")).isEqualTo("folder-456");
    }

    // --- Test 8: GDriveException sets success=false and errorMessage ---
    @Test
    void shouldSetErrorOutputsOnGDriveException() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.moveFile(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new GDriveException("API error occurred", 500, true));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(false);
        assertThat(connector.getOutputs().get("errorMessage")).asString().contains("API error occurred");
    }

    // --- Test 9: Auth failure (403) not retryable ---
    @Test
    void shouldSetErrorOnAuthFailure() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.moveFile(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new GDriveException("Forbidden", 403, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(false);
        assertThat(connector.getOutputs().get("errorMessage")).asString().contains("Forbidden");
    }

    // --- Test 10: Null optional inputs use defaults ---
    @Test
    void shouldUseDefaultsForOptionalInputs() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("serviceAccountKeyJson", "{\"type\":\"service_account\"}");
        inputs.put("fileId", "file-789");
        inputs.put("newName", "new-name.pdf");
        connector.setInputParameters(inputs);
        injectMockClient();

        connector.validateInputParameters();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-789", "new-name.pdf", null, "https://drive.google.com/file/d/file-789/view");
        when(mockClient.moveFile("file-789", null, "new-name.pdf", true)).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
        assertThat(connector.getOutputs().get("fileId")).isEqualTo("file-789");
    }

    // --- Test 11: All output fields populated ---
    @Test
    void shouldPopulateAllOutputFields() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "renamed.txt", "folder-456", "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", "folder-456", "renamed.txt", true)).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs).containsKey("fileId");
        assertThat(outputs).containsKey("fileName");
        assertThat(outputs).containsKey("newParentFolderId");
        assertThat(outputs).containsKey("webViewLink");
        assertThat(outputs).containsKey("success");
        assertThat(outputs.get("fileId")).isEqualTo("file-123");
        assertThat(outputs.get("fileName")).isEqualTo("renamed.txt");
        assertThat(outputs.get("newParentFolderId")).isEqualTo("folder-456");
        assertThat(outputs.get("webViewLink")).isEqualTo("https://drive.google.com/file/d/file-123/view");
        assertThat(outputs.get("success")).isEqualTo(true);
    }

    // --- Test 12: Unexpected exception sets success=false ---
    @Test
    void shouldSetErrorOnUnexpectedException() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.moveFile(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(false);
        assertThat(connector.getOutputs().get("errorMessage")).asString().contains("Unexpected failure");
    }

    // --- Test 13: removeFromCurrentParents defaults to true ---
    @Test
    void shouldDefaultRemoveFromCurrentParentsToTrue() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("serviceAccountKeyJson", "{\"type\":\"service_account\"}");
        inputs.put("fileId", "file-abc");
        inputs.put("newParentFolderId", "folder-def");
        // removeFromCurrentParents not set
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-abc", "doc.txt", "folder-def", "https://drive.google.com/file/d/file-abc/view");
        when(mockClient.moveFile("file-abc", "folder-def", null, true)).thenReturn(result);

        connector.executeBusinessLogic();

        verify(mockClient).moveFile("file-abc", "folder-def", null, true);
        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
    }

    // --- Test 14: removeFromCurrentParents set to false ---
    @Test
    void shouldPassRemoveFromCurrentParentsFalse() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put("removeFromCurrentParents", false);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "renamed.txt", "folder-456", "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", "folder-456", "renamed.txt", false)).thenReturn(result);

        connector.executeBusinessLogic();

        verify(mockClient).moveFile("file-123", "folder-456", "renamed.txt", false);
        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
    }

    // --- Test 15: Blank newParentFolderId and blank newName fails ---
    @Test
    void shouldFailValidationWhenBothNewParentAndNewNameBlank() {
        Map<String, Object> inputs = validInputs();
        inputs.put("newParentFolderId", "  ");
        inputs.put("newName", "  ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("At least one of newParentFolderId or newName must be provided");
    }

    // --- Test 16: Client called with correct arguments ---
    @Test
    void shouldCallClientWithCorrectArguments() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-123", "renamed.txt", "folder-456", "https://drive.google.com/file/d/file-123/view");
        when(mockClient.moveFile("file-123", "folder-456", "renamed.txt", true)).thenReturn(result);

        connector.executeBusinessLogic();

        verify(mockClient, times(1)).moveFile("file-123", "folder-456", "renamed.txt", true);
    }
}
