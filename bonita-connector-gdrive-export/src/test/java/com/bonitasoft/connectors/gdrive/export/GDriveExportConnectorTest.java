package com.bonitasoft.connectors.gdrive.export;

import com.bonitasoft.connectors.gdrive.GDriveClient;
import com.bonitasoft.connectors.gdrive.GDriveException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("GDriveExportConnector")
class GDriveExportConnectorTest {

    private GDriveExportConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveExportConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("fileId", "test-file-id");
            params.put("exportMimeType", "application/pdf");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when fileId is missing")
        void shouldFailOnMissingFileId() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("exportMimeType", "application/pdf");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
        }

        @Test
        @DisplayName("should fail when exportMimeType is missing")
        void shouldFailOnMissingExportMimeType() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("exportMimeType");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("exportMimeType", "application/pdf");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with docx export format")
        void shouldPassWithDocxFormat() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("exportMimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }
    }

    @Nested
    @DisplayName("Connector metadata")
    class Metadata {

        @Test
        @DisplayName("should return correct connector name")
        void shouldReturnConnectorName() {
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Export");
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

        private void injectClient(GDriveExportConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveExportConnector c) throws Exception {
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
        @DisplayName("should_export_google_doc_to_pdf_when_valid_workspace_document")
        void should_export_google_doc_to_pdf_when_valid_workspace_document() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "doc-123");
            params.put("exportMimeType", "application/pdf");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File googleDoc = new File();
            googleDoc.setId("doc-123");
            googleDoc.setName("My Document");
            googleDoc.setMimeType("application/vnd.google-apps.document");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            // First call: get metadata, second call: export (returns null, writes to stream)
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(googleDoc)  // metadata call
                    .thenReturn(null);       // export call

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileName")).isEqualTo("My Document.pdf");
            assertThat(outputs.get("exportedMimeType")).isEqualTo("application/pdf");
            assertThat(outputs.get("fileContent")).isNotNull();
            assertThat(outputs.get("size")).isEqualTo(0L);
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_throw_exception_when_file_is_not_google_workspace_document")
        void should_throw_exception_when_file_is_not_google_workspace_document() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-123");
            params.put("exportMimeType", "application/pdf");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File regularFile = new File();
            regularFile.setId("file-123");
            regularFile.setName("report.pdf");
            regularFile.setMimeType("application/pdf");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(regularFile);

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class)
                    .hasCauseInstanceOf(GDriveException.ExecutionException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
            assertThat((String) outputs.get("errorMessage")).contains("Google Workspace");
        }

        @Test
        @DisplayName("should_append_docx_extension_when_exporting_to_docx")
        void should_append_docx_extension_when_exporting_to_docx() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "doc-456");
            params.put("exportMimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File googleDoc = new File();
            googleDoc.setId("doc-456");
            googleDoc.setName("My Spreadsheet");
            googleDoc.setMimeType("application/vnd.google-apps.document");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(googleDoc)
                    .thenReturn(null);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileName")).isEqualTo("My Spreadsheet.docx");
        }
    }
}
