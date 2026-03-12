package com.bonitasoft.connectors.gdrive;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListFilesConnectorPropertyTest {

    private ListFilesConnector createConnectorWithMockClient(GDriveClient mockClient) throws Exception {
        ListFilesConnector connector = new ListFilesConnector();
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
        return connector;
    }

    private Map<String, Object> baseInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        return inputs;
    }

    // --- Property 1: Any non-blank serviceAccountKeyJson passes validation ---
    @Property
    void anyNonBlankServiceAccountKeyPassesValidation(@ForAll @StringLength(min = 1) String key) {
        Assume.that(!key.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        assertThatCode(() -> connector.validateInputParameters())
                .doesNotThrowAnyException();
    }

    // --- Property 2: Blank or null serviceAccountKeyJson always fails validation ---
    @Property
    void blankServiceAccountKeyAlwaysFailsValidation(@ForAll("blankStrings") String key) {
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t\n  ");
    }

    // --- Property 3: maxResults default is always 100 when not provided ---
    @Property
    void maxResultsDefaultsTo100WhenNotProvided(@ForAll @StringLength(min = 1) String key) {
        Assume.that(!key.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.getMaxResults()).isEqualTo(100);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 4: Provided maxResults is always preserved ---
    @Property
    void providedMaxResultsIsPreserved(@ForAll @IntRange(min = 1, max = 1000) int maxResults) {
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_MAX_RESULTS, maxResults);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.getMaxResults()).isEqualTo(maxResults);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 5: orderBy default is always "modifiedTime desc" ---
    @Property
    void orderByDefaultsToModifiedTimeDesc(@ForAll @StringLength(min = 1) String key) {
        Assume.that(!key.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.getOrderBy()).isEqualTo("modifiedTime desc");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 6: includeFiles defaults to true ---
    @Property
    void includeFilesDefaultsToTrue(@ForAll @StringLength(min = 1) String key) {
        Assume.that(!key.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.isIncludeFiles()).isTrue();
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 7: includeFolders defaults to true ---
    @Property
    void includeFoldersDefaultsToTrue(@ForAll @StringLength(min = 1) String key) {
        Assume.that(!key.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, key);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.isIncludeFolders()).isTrue();
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 8: Any parentFolderId string is passed through to configuration ---
    @Property
    void parentFolderIdIsPassedThrough(@ForAll @StringLength(min = 1, max = 200) String folderId) {
        Assume.that(!folderId.isBlank());
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_PARENT_FOLDER_ID, folderId);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.getParentFolderId()).isEqualTo(folderId);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    // --- Property 9: Success is always true when client returns any valid result ---
    @Property
    void successIsTrueForAnyValidClientResult(
            @ForAll @IntRange(min = 0, max = 500) int fileCount) throws Exception {

        GDriveClient mockClient = mock(GDriveClient.class);
        ListFilesConnector connector = createConnectorWithMockClient(mockClient);

        Map<String, Object> inputs = baseInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        List<Map<String, String>> files = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            files.add(Map.of("id", "f" + i, "name", "file" + i));
        }
        var listResult = new GDriveClient.ListFilesResult(files, fileCount, null);
        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(listResult);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_TOTAL_COUNT)).isEqualTo(fileCount);
    }

    // --- Property 10: GDriveException always results in success=false ---
    @Property
    void gdriveExceptionAlwaysResultsInFailure(
            @ForAll @StringLength(min = 1, max = 200) String errorMsg,
            @ForAll @IntRange(min = 400, max = 599) int statusCode) throws Exception {

        GDriveClient mockClient = mock(GDriveClient.class);
        ListFilesConnector connector = createConnectorWithMockClient(mockClient);

        Map<String, Object> inputs = baseInputs();
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new GDriveException(errorMsg, statusCode, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains(errorMsg);
    }

    // --- Property 11: Boolean inputs are correctly passed through ---
    @Property
    void booleanInputsAreCorrectlyPassedThrough(
            @ForAll boolean includeFiles,
            @ForAll boolean includeFolders) throws Exception {

        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FILES, includeFiles);
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FOLDERS, includeFolders);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.isIncludeFiles()).isEqualTo(includeFiles);
        assertThat(config.isIncludeFolders()).isEqualTo(includeFolders);
    }

    // --- Property 12: Application name defaults when blank ---
    @Property
    void applicationNameDefaultsWhenBlank(@ForAll("blankStrings") String blankName) {
        ListFilesConnector connector = new ListFilesConnector();
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_APPLICATION_NAME, blankName);
        connector.setInputParameters(inputs);

        try {
            connector.validateInputParameters();
            var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);
            assertThat(config.getApplicationName()).isEqualTo("Bonita-GoogleDrive-Connector");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
