package com.bonitasoft.connectors.gdrive.delete;

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

@DisplayName("GDriveDeleteConnector")
class GDriveDeleteConnectorTest {

    private GDriveDeleteConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GDriveDeleteConnector();
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
            params.put("fileId", "");

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
        @DisplayName("should pass with permanent delete flag")
        void shouldPassWithPermanentFlag() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("permanent", true);

            connector.setInputParameters(params);

            connector.validateInputParameters(); // Should not throw
        }

        @Test
        @DisplayName("should pass with trash mode (permanent=false)")
        void shouldPassWithTrashMode() throws ConnectorValidationException {
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "test-file-id");
            params.put("permanent", false);

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
            assertThat(connector.getConnectorName()).isEqualTo("GDrive-Delete");
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

        private void injectClient(GDriveDeleteConnector c, GDriveClient client) throws Exception {
            var field = com.bonitasoft.connectors.gdrive.AbstractGDriveConnector.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(c, client);
        }

        private void executeLogic(GDriveDeleteConnector c) throws Exception {
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
        @DisplayName("should_permanently_delete_file_when_permanent_is_true")
        void should_permanently_delete_file_when_permanent_is_true() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-to-delete");
            params.put("permanent", true);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(null); // delete returns void/null

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("errorMessage")).isEqualTo("");
        }

        @Test
        @DisplayName("should_trash_file_when_permanent_is_false")
        void should_trash_file_when_permanent_is_false() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-to-trash");
            params.put("permanent", false);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File trashedFile = new File();
            trashedFile.setId("file-to-trash");
            trashedFile.setTrashed(true);

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(trashedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(true);
            assertThat(outputs.get("errorMessage")).isEqualTo("");
        }

        @Test
        @DisplayName("should_default_to_trash_when_permanent_not_specified")
        void should_default_to_trash_when_permanent_not_specified() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-default-trash");

            connector.setInputParameters(params);
            connector.validateInputParameters();

            File trashedFile = new File();
            trashedFile.setId("file-default-trash");
            trashedFile.setTrashed(true);

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenReturn(trashedFile);

            injectClient(connector, mockClient);

            // When
            executeLogic(connector);

            // Then
            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("should_set_error_outputs_when_delete_fails")
        void should_set_error_outputs_when_delete_fails() throws Exception {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("serviceAccountJson", "{\"type\": \"service_account\"}");
            params.put("fileId", "file-no-access");
            params.put("permanent", true);

            connector.setInputParameters(params);
            connector.validateInputParameters();

            when(mockClient.getDriveService()).thenReturn(mockDriveService);
            when(mockClient.executeWithRetry(any(Callable.class), anyString()))
                    .thenThrow(new RuntimeException("File not found"));

            injectClient(connector, mockClient);

            // When / Then
            assertThatThrownBy(() -> executeLogic(connector))
                    .isInstanceOf(ConnectorException.class);

            Map<String, Object> outputs = connector.getOutputs();
            assertThat(outputs.get("success")).isEqualTo(false);
        }
    }
}
