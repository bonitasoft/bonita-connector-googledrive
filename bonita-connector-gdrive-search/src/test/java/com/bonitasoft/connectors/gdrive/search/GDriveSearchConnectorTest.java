package com.bonitasoft.connectors.gdrive.search;

import com.bonitasoft.connectors.gdrive.GDriveClient;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("GDriveSearchConnector")
class GDriveSearchConnectorTest {

    private GDriveSearchConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveSearchConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("nameContains", "test");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when no search criteria provided")
        void shouldFailOnNoSearchCriteria() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("At least one search criterion");
        }

        @Test
        @DisplayName("should pass with query parameter")
        void shouldPassWithQuery() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("query", "name contains 'report'");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with nameContains parameter")
        void shouldPassWithNameContains() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "report");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with parentFolderId parameter")
        void shouldPassWithParentFolder() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("parentFolderId", "folder-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with mimeType parameter")
        void shouldPassWithMimeType() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("mimeType", "application/pdf");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with fullTextSearch parameter")
        void shouldPassWithFullTextSearch() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fullTextSearch", "quarterly report");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with multiple search criteria")
        void shouldPassWithMultipleCriteria() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "report");
            params.put("mimeType", "application/pdf");
            params.put("parentFolderId", "folder-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional pagination parameters")
        void shouldPassWithPaginationParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "report");
            params.put("maxResults", 50);
            params.put("orderBy", "modifiedTime desc");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with date filters")
        void shouldPassWithDateFilters() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "report");
            params.put("modifiedAfter", "2024-01-01T00:00:00Z");
            params.put("modifiedBefore", "2024-12-31T23:59:59Z");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should fail when query exceeds max length")
        void shouldFailOnQueryTooLong() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("query", "a".repeat(2001)); // MAX_QUERY_LENGTH = 2000

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("query exceeds maximum length");
        }
    }

    @Nested
    @DisplayName("Connector metadata")
    class Metadata {

        @Test
        @DisplayName("should return correct connector name")
        void shouldReturnConnectorName() {
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Search");
        }
    }

    @Nested
    @DisplayName("Execution")
    @ExtendWith(MockitoExtension.class)
    class Execution {

        @Mock
        private GDriveClient mockClient;

        @Mock
        private Drive mockDriveService;

        private void injectClient(GDriveSearchConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveSearchConnector c) throws Exception {
            var method = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredMethod("executeBusinessLogic");
            method.setAccessible(true);
            try {
                method.invoke(c);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof ConnectorException ce) throw ce;
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }

        @Test
        @DisplayName("should_return_search_results_when_files_found")
        void should_return_search_results_when_files_found() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "report");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File file1 = new File();
            file1.setId("file-1");
            file1.setName("Q1 Report");
            file1.setMimeType("application/pdf");
            file1.setSize(2048L);

            File file2 = new File();
            file2.setId("file-2");
            file2.setName("Q2 Report");
            file2.setMimeType("application/pdf");
            file2.setSize(4096L);

            FileList fileList = new FileList();
            fileList.setFiles(List.of(file1, file2));
            fileList.setNextPageToken(null);

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(fileList);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("totalResults")).isEqualTo(2);
            assertThat(outputs.get("nextPageToken")).isNull();
            assertThat(outputs.get("success")).isEqualTo(true);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) outputs.get("files");
            assertThat(files).hasSize(2);
            assertThat(files.get(0).get("fileId")).isEqualTo("file-1");
            assertThat(files.get(0).get("fileName")).isEqualTo("Q1 Report");
            assertThat(files.get(1).get("fileId")).isEqualTo("file-2");
            assertThat(files.get(1).get("fileName")).isEqualTo("Q2 Report");
        }

        @Test
        @DisplayName("should_return_empty_list_when_no_files_found")
        void should_return_empty_list_when_no_files_found() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "nonexistent");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            FileList emptyList = new FileList();
            emptyList.setFiles(List.of());
            emptyList.setNextPageToken(null);

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(emptyList);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("totalResults")).isEqualTo(0);
            assertThat(outputs.get("success")).isEqualTo(true);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) outputs.get("files");
            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("should_use_raw_query_when_query_parameter_provided")
        void should_use_raw_query_when_query_parameter_provided() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("query", "name contains 'report' and trashed = false");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            FileList fileList = new FileList();
            fileList.setFiles(List.of());

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(fileList);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("totalResults")).isEqualTo(0);
        }

        @Test
        @DisplayName("should_return_next_page_token_when_more_results_available")
        void should_return_next_page_token_when_more_results_available() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "data");
            params.put("maxResults", 1);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File file1 = new File();
            file1.setId("file-1");
            file1.setName("Data File");
            file1.setMimeType("text/csv");

            FileList fileList = new FileList();
            fileList.setFiles(List.of(file1));
            fileList.setNextPageToken("next-page-token-abc");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(fileList);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("totalResults")).isEqualTo(1);
            assertThat(outputs.get("nextPageToken")).isEqualTo("next-page-token-abc");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_search_fails")
        void should_set_error_outputs_when_search_fails() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("nameContains", "test");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("Invalid query"));

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
        }
    }
}
