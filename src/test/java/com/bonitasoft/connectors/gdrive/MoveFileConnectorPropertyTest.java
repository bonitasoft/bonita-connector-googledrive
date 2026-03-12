package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveFileConnectorPropertyTest {

    private MoveFileConnector createConnector(Map<String, Object> inputs) {
        MoveFileConnector connector = new MoveFileConnector();
        connector.setInputParameters(inputs);
        return connector;
    }

    private void injectMockClient(MoveFileConnector connector, GDriveClient mockClient) throws Exception {
        Field clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private Map<String, Object> baseInputs(String fileId, String newParentFolderId, String newName) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("serviceAccountKeyJson", "{\"type\":\"service_account\"}");
        inputs.put("fileId", fileId);
        if (newParentFolderId != null) {
            inputs.put("newParentFolderId", newParentFolderId);
        }
        if (newName != null) {
            inputs.put("newName", newName);
        }
        return inputs;
    }

    // --- Property 1: Any non-blank fileId with non-blank newName passes validation ---
    @Property(tries = 50)
    void validationPassesWithNonBlankFileIdAndNewName(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String newName) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, null, newName);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();
        // no exception => pass
    }

    // --- Property 2: Any non-blank fileId with non-blank newParentFolderId passes validation ---
    @Property(tries = 50)
    void validationPassesWithNonBlankFileIdAndNewParentFolderId(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String parentId) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, parentId, null);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();
    }

    // --- Property 3: Missing serviceAccountKeyJson always fails validation ---
    @Property(tries = 50)
    void validationFailsWithoutServiceAccountKey(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String newName) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("fileId", fileId);
        inputs.put("newName", newName);
        MoveFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson is mandatory");
    }

    // --- Property 4: Blank-only fileId always fails validation ---
    @Property(tries = 20)
    void validationFailsWithBlankOnlyFileId(
            @ForAll @IntRange(min = 1, max = 20) int spaceCount) {
        String blankFileId = " ".repeat(spaceCount);
        Map<String, Object> inputs = baseInputs(blankFileId, "folder-1", null);
        MoveFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId is mandatory");
    }

    // --- Property 5: Output fileId always matches the result ---
    @Property(tries = 50)
    void outputFileIdMatchesResult(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String newName) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, null, newName);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                fileId, newName, null, "https://drive.google.com/file/d/" + fileId + "/view");
        when(mockClient.moveFile(eq(fileId), isNull(), eq(newName), anyBoolean())).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("fileId")).isEqualTo(fileId);
    }

    // --- Property 6: Output fileName always matches the result ---
    @Property(tries = 50)
    void outputFileNameMatchesResult(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String newName) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, null, newName);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                fileId, newName, null, "https://link");
        when(mockClient.moveFile(eq(fileId), isNull(), eq(newName), anyBoolean())).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("fileName")).isEqualTo(newName);
    }

    // --- Property 7: success is always true on successful execution ---
    @Property(tries = 50)
    void successIsTrueOnSuccessfulExecution(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String parentId) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, parentId, null);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                fileId, "name.txt", parentId, "https://link");
        when(mockClient.moveFile(eq(fileId), eq(parentId), isNull(), anyBoolean())).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(true);
    }

    // --- Property 8: success is always false on GDriveException ---
    @Property(tries = 30)
    void successIsFalseOnGDriveException(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 200) @AlphaChars String errorMsg) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, "folder-1", null);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        when(mockClient.moveFile(eq(fileId), eq("folder-1"), isNull(), anyBoolean()))
                .thenThrow(new GDriveException(errorMsg));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("success")).isEqualTo(false);
        assertThat(connector.getOutputs().get("errorMessage")).asString().contains(errorMsg);
    }

    // --- Property 9: Both newParentFolderId and newName non-blank always passes ---
    @Property(tries = 50)
    void validationPassesWithBothNewParentAndNewName(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String parentId,
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String newName) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, parentId, newName);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();
    }

    // --- Property 10: removeFromCurrentParents boolean is passed through ---
    @Property(tries = 20)
    void removeFromCurrentParentsIsPassedThrough(
            @ForAll boolean removeFlag) throws Exception {
        Map<String, Object> inputs = baseInputs("file-1", "folder-1", null);
        inputs.put("removeFromCurrentParents", removeFlag);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                "file-1", "name.txt", "folder-1", "https://link");
        when(mockClient.moveFile(eq("file-1"), eq("folder-1"), isNull(), eq(removeFlag))).thenReturn(result);

        connector.executeBusinessLogic();

        verify(mockClient).moveFile("file-1", "folder-1", null, removeFlag);
    }

    // --- Property 11: webViewLink output matches result ---
    @Property(tries = 50)
    void webViewLinkOutputMatchesResult(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId,
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String webLink) throws Exception {
        Map<String, Object> inputs = baseInputs(fileId, "folder-x", null);
        MoveFileConnector connector = createConnector(inputs);
        connector.validateInputParameters();

        GDriveClient mockClient = mock(GDriveClient.class);
        injectMockClient(connector, mockClient);

        GDriveClient.MoveFileResult result = new GDriveClient.MoveFileResult(
                fileId, "somefile.txt", "folder-x", webLink);
        when(mockClient.moveFile(eq(fileId), eq("folder-x"), isNull(), anyBoolean())).thenReturn(result);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get("webViewLink")).isEqualTo(webLink);
    }

    // --- Property 12: Neither null fileId nor blank passes without newParentFolderId and newName ---
    @Property(tries = 30)
    void validationFailsWhenBothDestinationsAbsent(
            @ForAll @StringLength(min = 1, max = 50) @AlphaChars String fileId) {
        Map<String, Object> inputs = baseInputs(fileId, null, null);
        MoveFileConnector connector = createConnector(inputs);

        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("At least one of newParentFolderId or newName must be provided");
    }
}
