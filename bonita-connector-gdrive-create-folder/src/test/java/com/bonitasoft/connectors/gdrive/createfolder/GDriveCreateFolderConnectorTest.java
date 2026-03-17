package com.bonitasoft.connectors.gdrive.createfolder;

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

@DisplayName("GDriveCreateFolderConnector")
class GDriveCreateFolderConnectorTest {

    private GDriveCreateFolderConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveCreateFolderConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("folderName", "Test Folder");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should fail when folderName is missing")
        void shouldFailOnMissingFolderName() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("folderName");
        }

        @Test
        @DisplayName("should fail when folderName is blank")
        void shouldFailOnBlankFolderName() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "   ");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("folderName");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "Test Folder");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional parameters")
        void shouldPassWithOptionalParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "Test Folder");
            params.put("parentFolderId", "parent-id");
            params.put("description", "Test description");

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
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-CreateFolder");
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

        private void injectClient(GDriveCreateFolderConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveCreateFolderConnector c) throws Exception {
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
        @DisplayName("should_create_folder_and_set_all_outputs_when_valid_inputs")
        void should_create_folder_and_set_all_outputs_when_valid_inputs() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "Test Folder");
            params.put("parentFolderId", "parent-123");
            params.put("description", "A test folder");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File createdFolder = new File();
            createdFolder.setId("folder-789");
            createdFolder.setName("Test Folder");
            createdFolder.setWebViewLink("https://drive.google.com/drive/folders/folder-789");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(createdFolder);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("folderId")).isEqualTo("folder-789");
            assertThat(outputs.get("folderName")).isEqualTo("Test Folder");
            assertThat(outputs.get("webViewLink")).isEqualTo("https://drive.google.com/drive/folders/folder-789");
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("errorMessage")).isEqualTo("");
        }

        @Test
        @DisplayName("should_create_folder_without_parent_when_no_parent_specified")
        void should_create_folder_without_parent_when_no_parent_specified() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "Root Folder");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File createdFolder = new File();
            createdFolder.setId("folder-root");
            createdFolder.setName("Root Folder");
            createdFolder.setWebViewLink("https://drive.google.com/drive/folders/folder-root");

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(createdFolder);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("folderId")).isEqualTo("folder-root");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_folder_creation_fails")
        void should_set_error_outputs_when_folder_creation_fails() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("folderName", "Test Folder");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("Permission denied"));

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
            assertThat((String) outputs.get("errorMessage")).contains("Permission denied");
        }
    }
}
