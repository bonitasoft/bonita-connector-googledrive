package com.bonitasoft.connectors.gdrive.move;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("GDriveMoveConnector")
class GDriveMoveConnectorTest {

    private GDriveMoveConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveMoveConnector();
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should fail when serviceAccountJson is missing")
        void shouldFailOnMissingAuth() {
            Map<String, Object> params = new HashMap<>();
            params.put("fileId", "test-file-id");
            params.put("destinationFolderId", "dest-folder-id");

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
            params.put("destinationFolderId", "dest-folder-id");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fileId");
        }

        @Test
        @DisplayName("should fail when destinationFolderId is missing")
        void shouldFailOnMissingDestination() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("destinationFolderId");
        }

        @Test
        @DisplayName("should fail when destinationFolderId is blank")
        void shouldFailOnBlankDestination() {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("destinationFolderId", "   ");

            connector.setInputParameters(params);

            assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("destinationFolderId");
        }

        @Test
        @DisplayName("should pass with all required parameters")
        void shouldPassWithRequiredParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("destinationFolderId", "dest-folder-id");

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with optional removeFromCurrentParent parameter")
        void shouldPassWithOptionalParams() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("destinationFolderId", "dest-folder-id");
            params.put("removeFromCurrentParent", false);

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
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Move");
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

        private void injectClient(GDriveMoveConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveMoveConnector c) throws Exception {
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
        @DisplayName("should_move_file_with_parent_removal_when_removeFromCurrentParent_is_true")
        void should_move_file_with_parent_removal_when_removeFromCurrentParent_is_true() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-to-move");
            params.put("destinationFolderId", "dest-folder-123");
            params.put("removeFromCurrentParent", true);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            // Current file with existing parent
            File currentFile = new File();
            currentFile.setParents(List.of("old-parent-id"));

            // Moved file result
            File movedFile = new File();
            movedFile.setId("file-to-move");
            movedFile.setParents(List.of("dest-folder-123"));

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            // First call: get current parents, second call: move file
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(currentFile)
                    .thenReturn(movedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileId")).isEqualTo("file-to-move");
            assertThat(outputs.get("newParents")).isEqualTo("dest-folder-123");
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("errorMessage")).isEqualTo("");
        }

        @Test
        @DisplayName("should_move_file_without_parent_removal_when_removeFromCurrentParent_is_false")
        void should_move_file_without_parent_removal_when_removeFromCurrentParent_is_false() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-to-move");
            params.put("destinationFolderId", "dest-folder-456");
            params.put("removeFromCurrentParent", false);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File currentFile = new File();
            currentFile.setParents(List.of("old-parent-id"));

            File movedFile = new File();
            movedFile.setId("file-to-move");
            movedFile.setParents(List.of("old-parent-id", "dest-folder-456"));

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(currentFile)
                    .thenReturn(movedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("fileId")).isEqualTo("file-to-move");
            assertThat(outputs.get("newParents")).isEqualTo("old-parent-id,dest-folder-456");
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_move_fails")
        void should_set_error_outputs_when_move_fails() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-no-access");
            params.put("destinationFolderId", "dest-folder");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("Access denied"));

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
        }
    }
}
