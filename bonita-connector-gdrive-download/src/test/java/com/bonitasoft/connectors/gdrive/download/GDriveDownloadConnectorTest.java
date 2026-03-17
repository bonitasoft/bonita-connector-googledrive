package com.bonitasoft.connectors.gdrive.download;

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

@DisplayName("GDriveDownloadConnector")
class GDriveDownloadConnectorTest {

    private GDriveDownloadConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveDownloadConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("fileId", "test-file-id");

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

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
        }

        @Test
        @DisplayName("should fail when fileId is blank")
        void shouldFailOnBlankFileId() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "   ");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional acknowledgeAbuse parameter")
        void shouldPassWithOptionalParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("acknowledgeAbuse", true);

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
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Download");
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

        private void injectClient(GDriveDownloadConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveDownloadConnector c) throws Exception {
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
        @DisplayName("should_download_file_and_return_base64_when_regular_file")
        void should_download_file_and_return_base64_when_regular_file() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-123");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File fileMetadata = new File();
            fileMetadata.setId("file-123");
            fileMetadata.setName("report.pdf");
            fileMetadata.setMimeType("application/pdf");
            fileMetadata.setSize(1024L);
            fileMetadata.setMd5Checksum("md5abc");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            // First call: get metadata, second call: download content (returns null)
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(fileMetadata)  // metadata call
                    .thenReturn(null);          // download call (writes to outputStream)

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileName")).isEqualTo("report.pdf");
            assertThat(outputs.get("mimeType")).isEqualTo("application/pdf");
            assertThat(outputs.get("size")).isEqualTo(1024L);
            assertThat(outputs.get("md5Checksum")).isEqualTo("md5abc");
            assertThat(outputs.get("fileContent")).isNotNull();
            // The content is Base64 of empty byte array (since mock doesn't write to stream)
            assertThat((String) outputs.get("fileContent")).isEqualTo(Base64.getEncoder().encodeToString(new byte[0]));
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_throw_exception_when_file_is_google_workspace_document")
        void should_throw_exception_when_file_is_google_workspace_document() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "doc-123");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File googleDoc = new File();
            googleDoc.setId("doc-123");
            googleDoc.setName("My Document");
            googleDoc.setMimeType("application/vnd.google-apps.document");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(googleDoc);

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class)
                    .hasCauseInstanceOf(GDriveException.ExecutionException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
            assertThat((String) outputs.get("errorMessage")).contains("Export connector");
        }
    }
}
