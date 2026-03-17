package com.bonitasoft.connectors.gdrive.copy;

import com.bonitasoft.connectors.gdrive.GDriveClient;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("GDriveCopyConnector")
class GDriveCopyConnectorTest {

    private GDriveCopyConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveCopyConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("sourceFileId", "source-file-id");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when sourceFileId is missing")
        void shouldFailOnMissingSourceFileId() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("sourceFileId");
        }

        @Test
        @DisplayName("should fail when sourceFileId is blank")
        void shouldFailOnBlankSourceFileId() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("sourceFileId");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-file-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional newName parameter")
        void shouldPassWithNewName() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-file-id");
            params.put("newName", "Copy of File");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional destinationFolderId parameter")
        void shouldPassWithDestinationFolder() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-file-id");
            params.put("destinationFolderId", "dest-folder-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with all optional parameters")
        void shouldPassWithAllOptionalParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-file-id");
            params.put("newName", "Copy of File");
            params.put("destinationFolderId", "dest-folder-id");

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
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Copy");
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

        private void injectClient(GDriveCopyConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveCopyConnector c) throws Exception {
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
        @DisplayName("should_copy_file_with_new_name_when_newName_provided")
        void should_copy_file_with_new_name_when_newName_provided() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-123");
            params.put("newName", "Copy of Report");
            params.put("destinationFolderId", "dest-folder-789");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File copiedFile = new File();
            copiedFile.setId("copy-456");
            copiedFile.setName("Copy of Report");
            copiedFile.setWebViewLink("https://drive.google.com/file/d/copy-456/view");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(copiedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("newFileId")).isEqualTo("copy-456");
            assertThat(outputs.get("fileName")).isEqualTo("Copy of Report");
            assertThat(outputs.get("webViewLink")).isEqualTo("https://drive.google.com/file/d/copy-456/view");
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("errorMessage")).isEqualTo("");
        }

        @Test
        @DisplayName("should_copy_file_without_new_name_when_newName_not_provided")
        void should_copy_file_without_new_name_when_newName_not_provided() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-123");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File copiedFile = new File();
            copiedFile.setId("copy-789");
            copiedFile.setName("Copy of Original File");
            copiedFile.setWebViewLink("https://drive.google.com/file/d/copy-789/view");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(copiedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("newFileId")).isEqualTo("copy-789");
            assertThat(outputs.get("fileName")).isEqualTo("Copy of Original File");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_copy_fails")
        void should_set_error_outputs_when_copy_fails() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("sourceFileId", "source-no-access");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("Source file not found"));

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
            assertThat((String) outputs.get("errorMessage")).contains("Source file not found");
        }
    }
}
