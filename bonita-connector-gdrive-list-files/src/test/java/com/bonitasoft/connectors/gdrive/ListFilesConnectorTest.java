package com.bonitasoft.connectors.gdrive;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListFilesConnectorTest {

    private ListFilesConnector connector;

    @Mock
    private GDriveClient mockClient;

    @BeforeEach
    void setUp() {
        connector = new ListFilesConnector();
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractGDriveConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }

    private Map<String, Object> baseInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "{\"type\":\"service_account\"}");
        return inputs;
    }

    private void setInputsAndValidate(Map<String, Object> inputs) throws ConnectorValidationException {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    // --- Test 1: Happy path — all outputs populated ---
    @Test
    void shouldListFilesSuccessfully() throws Exception {
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_PARENT_FOLDER_ID, "folder123");
        inputs.put(ListFilesConnector.INPUT_MAX_RESULTS, 50);
        inputs.put(ListFilesConnector.INPUT_ORDER_BY, "name");
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FILES, true);
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FOLDERS, false);

        setInputsAndValidate(inputs);
        injectMockClient();

        List<Map<String, String>> filesList = List.of(
                Map.of("id", "f1", "name", "doc.pdf", "mimeType", "application/pdf"),
                Map.of("id", "f2", "name", "img.png", "mimeType", "image/png")
        );
        var listResult = new GDriveClient.ListFilesResult(filesList, 2, "nextToken123");
        when(mockClient.listFiles("folder123", null, null, 50, "name", true, false))
                .thenReturn(listResult);

        connector.executeBusinessLogic();

        assertThat((List<?>) connector.getOutputs().get(ListFilesConnector.OUTPUT_FILES)).hasSize(2);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_TOTAL_COUNT)).isEqualTo(2);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_NEXT_PAGE_TOKEN)).isEqualTo("nextToken123");
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // --- Test 2: Missing mandatory serviceAccountKeyJson ---
    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonMissing() {
        Map<String, Object> inputs = new HashMap<>();
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    // --- Test 3: Blank serviceAccountKeyJson ---
    @Test
    void shouldFailValidationWhenServiceAccountKeyJsonBlank() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ListFilesConnector.INPUT_SERVICE_ACCOUNT_KEY_JSON, "   ");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("serviceAccountKeyJson");
    }

    // --- Test 4: Error path — GDriveException sets success=false and errorMessage ---
    @Test
    void shouldSetErrorOutputsOnGDriveException() throws Exception {
        Map<String, Object> inputs = baseInputs();
        setInputsAndValidate(inputs);
        injectMockClient();

        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new GDriveException("Quota exceeded", 429, true));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Quota exceeded");
    }

    // --- Test 5: Auth failure — 401 error ---
    @Test
    void shouldSetErrorOutputsOnAuthFailure() throws Exception {
        Map<String, Object> inputs = baseInputs();
        setInputsAndValidate(inputs);
        injectMockClient();

        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new GDriveException("Invalid credentials", 401, false));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Invalid credentials");
    }

    // --- Test 6: Null optional inputs use defaults ---
    @Test
    void shouldUseDefaultsForNullOptionalInputs() throws Exception {
        Map<String, Object> inputs = baseInputs();
        setInputsAndValidate(inputs);
        injectMockClient();

        var emptyResult = new GDriveClient.ListFilesResult(List.of(), 0, null);
        when(mockClient.listFiles(isNull(), isNull(), isNull(), eq(100), eq("modifiedTime desc"), eq(true), eq(true)))
                .thenReturn(emptyResult);

        connector.executeBusinessLogic();

        verify(mockClient).listFiles(isNull(), isNull(), isNull(), eq(100), eq("modifiedTime desc"), eq(true), eq(true));
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // --- Test 7: All output fields populated including null nextPageToken ---
    @Test
    void shouldSetAllOutputFieldsWithNullNextPageToken() throws Exception {
        Map<String, Object> inputs = baseInputs();
        setInputsAndValidate(inputs);
        injectMockClient();

        List<Map<String, String>> files = List.of(
                Map.of("id", "abc", "name", "test.txt", "mimeType", "text/plain")
        );
        var listResult = new GDriveClient.ListFilesResult(files, 1, null);
        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(listResult);

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_FILES)).isEqualTo(files);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_TOTAL_COUNT)).isEqualTo(1);
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_NEXT_PAGE_TOKEN)).isNull();
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE)).isNull();
    }

    // --- Test 8: Unexpected exception sets success=false ---
    @Test
    void shouldSetErrorOutputsOnUnexpectedException() throws Exception {
        Map<String, Object> inputs = baseInputs();
        setInputsAndValidate(inputs);
        injectMockClient();

        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("Unexpected network failure"));

        connector.executeBusinessLogic();

        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(false);
        assertThat((String) connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_ERROR_MESSAGE))
                .contains("Unexpected error");
    }

    // --- Test 9: Search query and mimeTypeFilter passed correctly ---
    @Test
    void shouldPassSearchQueryAndMimeTypeFilter() throws Exception {
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_SEARCH_QUERY, "name contains 'report'");
        inputs.put(ListFilesConnector.INPUT_MIME_TYPE_FILTER, "application/pdf");
        setInputsAndValidate(inputs);
        injectMockClient();

        var emptyResult = new GDriveClient.ListFilesResult(List.of(), 0, null);
        when(mockClient.listFiles(any(), eq("name contains 'report'"), eq("application/pdf"),
                anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(emptyResult);

        connector.executeBusinessLogic();

        verify(mockClient).listFiles(isNull(), eq("name contains 'report'"), eq("application/pdf"),
                eq(100), eq("modifiedTime desc"), eq(true), eq(true));
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // --- Test 10: Empty result list ---
    @Test
    void shouldHandleEmptyResultList() throws Exception {
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_PARENT_FOLDER_ID, "emptyFolder");
        setInputsAndValidate(inputs);
        injectMockClient();

        var emptyResult = new GDriveClient.ListFilesResult(List.of(), 0, null);
        when(mockClient.listFiles(eq("emptyFolder"), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(emptyResult);

        connector.executeBusinessLogic();

        assertThat((List<?>) connector.getOutputs().get(ListFilesConnector.OUTPUT_FILES)).isEmpty();
        assertThat(connector.getOutputs().get(ListFilesConnector.OUTPUT_TOTAL_COUNT)).isEqualTo(0);
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }

    // --- Test 11: Custom application name and timeouts ---
    @Test
    void shouldBuildConfigurationWithCustomValues() throws Exception {
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_APPLICATION_NAME, "MyApp");
        inputs.put(ListFilesConnector.INPUT_CONNECT_TIMEOUT, 5000);
        inputs.put(ListFilesConnector.INPUT_READ_TIMEOUT, 10000);
        inputs.put(ListFilesConnector.INPUT_IMPERSONATED_USER_EMAIL, "admin@example.com");

        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        var configField = AbstractGDriveConnector.class.getDeclaredField("configuration");
        configField.setAccessible(true);
        GDriveConfiguration config = (GDriveConfiguration) configField.get(connector);

        assertThat(config.getApplicationName()).isEqualTo("MyApp");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getReadTimeout()).isEqualTo(10000);
        assertThat(config.getImpersonatedUserEmail()).isEqualTo("admin@example.com");
    }

    // --- Test 12: Include only folders ---
    @Test
    void shouldPassIncludeFoldersOnlyConfiguration() throws Exception {
        Map<String, Object> inputs = baseInputs();
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FILES, false);
        inputs.put(ListFilesConnector.INPUT_INCLUDE_FOLDERS, true);
        setInputsAndValidate(inputs);
        injectMockClient();

        var result = new GDriveClient.ListFilesResult(List.of(), 0, null);
        when(mockClient.listFiles(any(), any(), any(), anyInt(), any(), eq(false), eq(true)))
                .thenReturn(result);

        connector.executeBusinessLogic();

        verify(mockClient).listFiles(any(), any(), any(), anyInt(), any(), eq(false), eq(true));
        assertThat(connector.getOutputs().get(AbstractGDriveConnector.OUTPUT_SUCCESS)).isEqualTo(true);
    }
}
